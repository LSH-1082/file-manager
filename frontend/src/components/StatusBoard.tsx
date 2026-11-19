import { useMemo, useState } from 'react'
import { useData } from '../store/useData'
import type { Doc } from '../types'
import { docTitle, isUncategorized } from '../utils'
import {
  formatDay,
  isRejected,
  latestVersion,
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
import { IconSearch, IconUpload } from './icons'
import { StageBadge } from './StageBadge'
import './StatusBoard.css'

interface Props {
  /** 범위(전체·미분류) 안의 문서 */
  docs: Doc[]
  scopeLabel: string
  onSelect: (id: string, versionId: string | null) => void
  onCreate: (files?: File[]) => void
  /** 미분류 문서를 카테고리에 넣은 뒤 */
  onAssigned: (doc: Doc) => void
}

type ColumnKey = 'name' | 'version' | 'stage' | 'worker' | 'reviewer' | 'sent'

const COLUMNS: { key: ColumnKey; label: string }[] = [
  { key: 'name', label: '문서 이름' },
  { key: 'version', label: '버전' },
  { key: 'stage', label: '상태' },
  { key: 'worker', label: '작업자' },
  { key: 'reviewer', label: '검토자' },
  { key: 'sent', label: '발송일/발송처' },
]

const EMPTY = '-'

/** 검토자 셀: 2명까지 이름을 보이고 나머지는 '외 n명'(전체 이름은 title) */
function reviewerSummary(names: string[]): string {
  if (names.length === 0) return EMPTY
  if (names.length <= 2) return names.join(', ')
  return `${names.slice(0, 2).join(', ')} 외 ${names.length - 2}명`
}

/** 문서 한 행의 표시값과 정렬값 */
function rowOf(title: string, doc: Doc, uncategorized: boolean) {
  const latest = latestVersion(doc)
  const reviewers = reviewersOf(latest)
  // 표의 다른 열처럼 최신 버전 기준: 최신 버전이 발송 단계일 때만 발송일/발송처를 보인다
  const sent = versionStage(latest) === 'sent' ? latest : undefined
  const text: Record<ColumnKey, string> = {
    name: title,
    version: versionLabel(latest),
    stage: STAGE_LABEL[versionStage(latest)],
    worker: workerOf(latest) ?? EMPTY,
    reviewer: reviewerSummary(reviewers),
    sent: sent ? (sent.sentTo ? `${formatDay(sent.sentAt!)} / ${sent.sentTo}` : formatDay(sent.sentAt!)) : EMPTY,
  }
  const sortValue: Record<ColumnKey, string | number> = {
    ...text,
    version: latest.number,
    stage: stageStep(versionStage(latest)),
    reviewer: reviewers[0] ?? '',
    sent: sent?.sentAt ?? '',
  }
  const titles: Record<ColumnKey, string> = { ...text, reviewer: reviewers.length ? reviewers.join(', ') : EMPTY }
  return { doc, latest, uncategorized, text, titles, sortValue }
}

/** 전체 문서·미분류 화면의 문서 현황: 문서(카테고리)당 한 행인 표 */
export default function StatusBoard({ docs, scopeLabel, onSelect, onCreate, onAssigned }: Props) {
  const { categories } = useData()
  const [tab, setTab] = useState<Stage | 'all'>('all')
  const [query, setQuery] = useState('')
  // 처음 순서: 상태 단계순(작업중 → … → 발송, 미분류도 자기 단계 자리), 같은 상태 안에서는 최근 변경순
  const [sort, setSort] = useState<{ key: ColumnKey; dir: 'asc' | 'desc' }>({ key: 'stage', dir: 'asc' })
  const [assigning, setAssigning] = useState<Doc | null>(null)

  const rows = useMemo(() => docs.map((d) => rowOf(docTitle(categories, d), d, isUncategorized(categories, d))), [docs, categories])
  const countOf = (stage: Stage) => rows.filter((r) => versionStage(r.latest) === stage).length

  const q = query.trim().toLowerCase()
  const shown = rows
    .filter((r) => (tab === 'all' || versionStage(r.latest) === tab) && (!q || r.text.name.toLowerCase().includes(q)))
    .sort((a, b) => {
      const x = a.sortValue[sort.key]
      const y = b.sortValue[sort.key]
      const order = typeof x === 'number' && typeof y === 'number' ? x - y : String(x).localeCompare(String(y), 'ko')
      if (order !== 0) return sort.dir === 'asc' ? order : -order
      // 같은 값끼리는 최근 변경순
      return b.doc.updatedAt.localeCompare(a.doc.updatedAt)
    })

  const toggleSort = (key: ColumnKey) =>
    setSort((s) => (s.key === key ? { key, dir: s.dir === 'asc' ? 'desc' : 'asc' } : { key, dir: 'asc' }))

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
          {shown.length === 0 ? (
            <tr className="board-empty">
              <td colSpan={COLUMNS.length}>조건에 맞는 문서가 없습니다.</td>
            </tr>
          ) : (
            shown.map(({ doc, latest, uncategorized, text, titles }) => {
              const stage = versionStage(latest)
              return (
                <tr key={doc.id} onClick={() => onSelect(doc.id, null)}>
                  <td className="col-name" title={titles.name}>
                    <div className="name-cell">
                    <button
                      type="button"
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
                    <StageBadge stage={stage} rejected={isRejected(latest)} />
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

      {assigning && <AssignCategoryDialog doc={assigning} onClose={() => setAssigning(null)} onAssigned={onAssigned} />}
    </section>
  )
}
