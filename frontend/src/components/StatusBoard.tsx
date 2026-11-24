import { useMemo, useState } from 'react'
import { daysLeft, dueTitleColor, nextDue } from '../deadline'
import { useData } from '../store/useData'
import type { Category, Doc, Schedule } from '../types'
import { docTitle, isUncategorized } from '../utils'
import {
  formatDay,
  isRejected,
  latestVersion,
  reviewerSummary,
  reviewersOf,
  stageStep,
  STAGE_LABEL,
  STEPS,
  versionLabel,
  versionStage,
  workerOf,
} from '../versioning'
import type { Stage } from '../versioning'
import AssignCategoryDialog from './AssignCategoryDialog'
import { formatMD } from './ganttScale'
import { IconChevron, IconSearch, IconUpload } from './icons'
import { StageBadge } from './StageBadge'
import './StatusBoard.css'

interface Props {
  /** 범위(전체·미분류) 안의 문서 */
  docs: Doc[]
  /** 숨긴 카테고리의 문서. 있으면 표 아래 접힌 '숨긴 문서' 섹션에 보인다 */
  hiddenDocs: Doc[]
  scopeLabel: string
  onSelect: (id: string, versionId: string | null) => void
  onCreate: (files?: File[]) => void
  /** 미분류 문서를 카테고리에 넣은 뒤 */
  onAssigned: (doc: Doc) => void
}

type ColumnKey = 'name' | 'version' | 'stage' | 'due' | 'worker' | 'reviewer' | 'sent'

const COLUMNS: { key: ColumnKey; label: string }[] = [
  { key: 'name', label: '문서 이름' },
  { key: 'version', label: '버전' },
  { key: 'stage', label: '상태' },
  { key: 'due', label: '발송 기한' },
  { key: 'worker', label: '작업자' },
  { key: 'reviewer', label: '검토자' },
  { key: 'sent', label: '발송일/발송처' },
]

const EMPTY = '-'

/** 문서 한 행의 표시값과 정렬값 */
function rowOf(title: string, doc: Doc, uncategorized: boolean, schedules: Schedule[]) {
  const latest = latestVersion(doc)
  const due = nextDue(doc, schedules)
  const left = due === undefined ? 0 : daysLeft(due)
  const reviewers = reviewersOf(latest)
  // 표의 다른 열처럼 최신 버전 기준: 최신 버전이 발송 단계일 때만 발송일/발송처를 보인다
  const sent = versionStage(latest) === 'sent' ? latest : undefined
  const text: Record<ColumnKey, string> = {
    name: title,
    version: versionLabel(latest),
    stage: STAGE_LABEL[versionStage(latest)],
    due: due === undefined ? EMPTY : `${formatMD(due)} · ${left < 0 ? '기한 넘김' : left === 0 ? '오늘' : `D-${left}`}`,
    worker: workerOf(latest) ?? EMPTY,
    reviewer: reviewerSummary(reviewers) || EMPTY,
    sent: sent ? (sent.sentTo ? `${formatDay(sent.sentAt!)} / ${sent.sentTo}` : formatDay(sent.sentAt!)) : EMPTY,
  }
  const sortValue: Record<ColumnKey, string | number> = {
    ...text,
    version: latest.number,
    stage: stageStep(versionStage(latest)),
    reviewer: reviewers[0] ?? '',
    due: due ?? '',
    sent: sent?.sentAt ?? '',
  }
  const titles: Record<ColumnKey, string> = { ...text, reviewer: reviewers.length ? reviewers.join(', ') : EMPTY }
  return { doc, latest, uncategorized, text, titles, sortValue }
}

function rowsOf(categories: Category[], docs: Doc[], schedules: Schedule[]) {
  return docs.map((d) => rowOf(docTitle(categories, d), d, isUncategorized(categories, d), schedules))
}
type Row = ReturnType<typeof rowOf>

/** 전체 문서·미분류 화면의 문서 현황: 문서(카테고리)당 한 행인 표 */
export default function StatusBoard({ docs, hiddenDocs, scopeLabel, onSelect, onCreate, onAssigned }: Props) {
  const { categories, schedules } = useData()
  const [tab, setTab] = useState<Stage | 'all'>('all')
  const [query, setQuery] = useState('')
  // 처음 순서: 상태 단계순(작업중 → … → 발송, 미분류도 자기 단계 자리), 같은 상태 안에서는 최근 변경순
  const [sort, setSort] = useState<{ key: ColumnKey; dir: 'asc' | 'desc' }>({ key: 'stage', dir: 'asc' })
  const [assigning, setAssigning] = useState<Doc | null>(null)
  /** 접힘 상태는 저장하지 않는다 */
  const [hiddenOpen, setHiddenOpen] = useState(false)

  const rows = useMemo(() => rowsOf(categories, docs, schedules), [docs, categories, schedules])
  const hiddenRows = useMemo(() => rowsOf(categories, hiddenDocs, schedules), [hiddenDocs, categories, schedules])
  const countOf = (stage: Stage) => rows.filter((r) => versionStage(r.latest) === stage).length

  const q = query.trim().toLowerCase()
  // 숨긴 문서 섹션도 같은 상태 탭·검색·정렬을 따른다
  const arrange = (list: Row[]) => list
    .filter((r) => (tab === 'all' || versionStage(r.latest) === tab) && (!q || r.text.name.toLowerCase().includes(q)))
    .sort((a, b) => {
      const x = a.sortValue[sort.key]
      const y = b.sortValue[sort.key]
      // 기한 없는 문서는 정렬 방향과 상관없이 뒤로(그 안에서는 아래 최근 변경순)
      if (sort.key === 'due' && (x === '') !== (y === '')) return x === '' ? 1 : -1
      const order = typeof x === 'number' && typeof y === 'number' ? x - y : String(x).localeCompare(String(y), 'ko')
      if (order !== 0) return sort.dir === 'asc' ? order : -order
      // 같은 값끼리는 최근 변경순
      return b.doc.updatedAt.localeCompare(a.doc.updatedAt)
    })
  const shown = arrange(rows)

  const toggleSort = (key: ColumnKey) =>
    setSort((s) => (s.key === key ? { key, dir: s.dir === 'asc' ? 'desc' : 'asc' } : { key, dir: 'asc' }))

  const renderTable = (list: Row[]) => (
    <table className="board-table">
      <thead>
        <tr>
          {COLUMNS.map((c) => (
            <th key={c.key} title={c.label} className={`col-${c.key}`} aria-sort={sort.key === c.key ? (sort.dir === 'asc' ? 'ascending' : 'descending') : 'none'}>
              <button type="button" onClick={() => toggleSort(c.key)}>
                <span className="ellipsis">{c.label}</span>
                <span className="sort-mark" aria-hidden="true">
                  {sort.key === c.key ? (sort.dir === 'asc' ? '↑' : '↓') : ''}
                </span>
              </button>
            </th>
          ))}
        </tr>
      </thead>
      <tbody>
        {list.length === 0 ? (
          <tr className="board-empty">
            <td colSpan={COLUMNS.length}>조건에 맞는 문서가 없습니다.</td>
          </tr>
        ) : (
          list.map(({ doc, latest, uncategorized, text, titles }) => {
            const stage = versionStage(latest)
            return (
              <tr key={doc.id} onClick={() => onSelect(doc.id, null)}>
                <td className="col-name" title={titles.name}>
                  <div className="name-cell">
                  <button
                    type="button"
                    style={{ color: dueTitleColor(nextDue(doc, schedules)) }}
                    onClick={(e) => {
                      e.stopPropagation()
                      onSelect(doc.id, null)
                    }}
                  >
                    {text.name}
                  </button>
                  {uncategorized && (
                    <button
                      type="button"
                      className="btn small assign"
                      onClick={(e) => {
                        e.stopPropagation()
                        setAssigning(doc)
                      }}
                    >
                      분류 지정
                    </button>
                  )}
                  </div>
                </td>
                <td className="col-version" title={titles.version}>
                  {text.version}
                </td>
                <td className="col-stage" title={text.stage + (isRejected(latest) ? ' · 거절됨' : '')}>
                  <StageBadge stage={stage} rejected={isRejected(latest)} reviewers={reviewersOf(latest)} />
                </td>
                <td className="col-due num" title={titles.due}>
                  {text.due}
                </td>
                <td className="col-worker" title={titles.worker}>
                  {text.worker}
                </td>
                <td className="col-reviewer" title={titles.reviewer}>
                  {text.reviewer}
                </td>
                <td className="col-sent num" title={titles.sent}>
                  {text.sent}
                </td>
              </tr>
            )
          })
        )}
      </tbody>
    </table>
  )

  return (
    <section className="status-board" aria-labelledby="board-title">
      <header className="board-head doc-list-title">
        <div>
          <h1 id="board-title">{scopeLabel} 현황</h1>
          <p className="sub num">문서 {docs.length}건</p>
        </div>
        <button type="button" className="btn" onClick={() => onCreate()}>
          <IconUpload />
          파일 업로드
        </button>
      </header>

      <div className="board-tools">
        <div className="board-tabs" role="tablist" aria-label="상태별 보기">
          <button type="button" role="tab" aria-selected={tab === 'all'} onClick={() => setTab('all')}>
            전체 <span className="num">{rows.length}</span>
          </button>
          {STEPS.map((s) => (
            <button key={s.key} type="button" role="tab" aria-selected={tab === s.key} onClick={() => setTab(s.key)}>
              {s.label} <span className="num">{countOf(s.key)}</span>
            </button>
          ))}
        </div>
        <label className="search">
          <IconSearch className="search-icon" />
          <input type="search" aria-label="문서 이름 검색" placeholder="문서 이름 검색" value={query} onChange={(e) => setQuery(e.target.value)} />
        </label>
      </div>

      {renderTable(shown)}

      {hiddenRows.length > 0 && (
        <div className="board-hidden">
          <div className="section-header">
            <button type="button" className="section-toggle" aria-expanded={hiddenOpen} onClick={() => setHiddenOpen((v) => !v)}>
              <IconChevron size={12} />
              숨긴 문서 ({hiddenRows.length})
            </button>
          </div>
          {hiddenOpen && renderTable(arrange(hiddenRows))}
        </div>
      )}

      {assigning && <AssignCategoryDialog doc={assigning} onClose={() => setAssigning(null)} onAssigned={onAssigned} />}
    </section>
  )
}
