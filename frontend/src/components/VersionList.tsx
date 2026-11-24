import { useState } from 'react'
import { useData } from '../store/useData'
import type { Category, Doc, DocVersion } from '../types'
import { categoryPath } from '../utils'
import {
  chainFiles,
  fileRevLabel,
  formatDay,
  isRejected,
  latestVersion,
  STAGE_LABEL,
  topFileLabel,
  versionLabel,
  versionStage,
  versionWorker,
  reviewersOf,
} from '../versioning'
import { FileThumb } from './files'
import { IconChevron } from './icons'
import { StageBadge } from './StageBadge'
import { StageStepper } from './StageStepper'

interface Props {
  category: Category
  /** 이 카테고리의 문서(카테고리 하나 = 문서 하나). 없으면 undefined */
  doc: Doc | undefined
  /** 보고 있는 버전. null이면 최신 */
  selectedVersionId: string | null
  onSelect: (versionId: string) => void
  onOpenResources: () => void
  resourceCount: number
}

/**
 * 카테고리 = 문서. 카테고리를 고르면 그 문서의 버전 줄만 크게 보여준다.
 * 최신 버전부터 카드 블록으로 나눠, 버전마다 올라온 수정본을 보여준다.
 * 업로드는 이 목록이 아니라 상세 영역에서 한다(문서가 없는 카테고리는 상세에 첫 파일 올리기 화면이 바로 열린다).
 */
export default function VersionList({ category, doc, selectedVersionId, onSelect, onOpenResources, resourceCount }: Props) {
  const { categories } = useData()
  const latest = doc && latestVersion(doc)
  const path = categoryPath(categories, category.id)

  return (
    <section className="doc-list" aria-label="버전 목록">
      <header className="doc-list-header">
        <div className="doc-list-title">
          <div className="title-text">
            <h1 title={path}>{path}</h1>
            <p className="sub num">
              {doc && latest ? (
                <>
                  버전 {doc.versions.length}개 ·{' '}
                  <span className="sub-name" title={versionLabel(latest)}>
                    {versionLabel(latest)}
                  </span>{' '}
                  {STAGE_LABEL[versionStage(latest)]}
                </>
              ) : (
                '아직 문서가 없습니다'
              )}
            </p>
          </div>
        </div>
        <button type="button" className="resource-link" onClick={onOpenResources}>
          <span>이 분류의 양식·참고자료</span>
          <span className="resource-count num">{resourceCount}건</span>
          <IconChevron size={14} />
        </button>
      </header>

      {!doc ? (
        <div className="empty">
          <p>이 분류의 문서가 아직 없습니다.</p>
          <p>오른쪽에서 파일을 하나 올리면 이 분류 문서의 첫 버전 원본이 됩니다.</p>
        </div>
      ) : (
        <div className="vlist">
          <ol className="vcards">
            {[...doc.versions].reverse().map((v) => (
              <li key={v.id}>
                <VersionCard version={v} active={v.id === (selectedVersionId ?? latestVersion(doc).id)} onSelect={() => onSelect(v.id)} />
              </li>
            ))}
          </ol>
        </div>
      )}
    </section>
  )
}

/**
 * 버전 카드. 파일 순서는 상세 사슬과 같다: 맨 위 최신 수정본(수정본이 없으면 원본), 가운데 '수정본 n건' 토글(기본 접힘), 맨 아래 원본.
 * 카드 전체를 한 button으로 만들면 토글 button이 그 안에 중첩되므로, 선택 button과 토글 button을 형제로 둔다.
 */
function VersionCard({ version, active, onSelect }: { version: DocVersion; active: boolean; onSelect: () => void }) {
  const [open, setOpen] = useState(false)
  const { top, middle, base } = chainFiles(version)
  const sent = version.status === 'sent'
  const stage = versionStage(version)
  const rejected = isRejected(version)
  const worker = versionWorker(version)
  // 발송대기(발송예정)와 발송(제출본)은 이름표를 굵게 해 발송할 파일이 어느 것인지 분명히 한다
  const topLabel = topFileLabel(version)
  const topStrong = sent || stage === 'ready'

  return (
    // 카드의 빈 곳을 눌러도 선택된다. 키보드는 안의 선택 button이 맡는다
    <div className={`vcard ${version.status} ${active ? 'active' : ''}`} aria-current={active ? 'true' : undefined} onClick={onSelect}>
      <button type="button" className="vcard-main">
        {/* 머리는 한 줄: 버전 이름, 단계 표식(거절되어 돌아왔으면 '거절됨'), 발송된 버전은 발송일. 대조표 정보는 상세에만 있다 */}
        <span className="vcard-head">
          <span className="vcard-no num" title={versionLabel(version)}>
            {versionLabel(version)}
          </span>
          <span className="vcard-sent">
            {!sent && <StageStepper stage={stage} rejected={rejected} compact />}
            <StageBadge stage={stage} rejected={rejected} worker={worker} reviewers={reviewersOf(version)} />
            {sent && <span className="num">{formatDay(version.sentAt!)}</span>}
          </span>
        </span>
        {/* 파일 없이 만든 v1은 원본을 올리기 전까지 파일 줄 대신 안내만 */}
        {top ? (
          <span className="vcard-file">
            <FileThumb file={top} size="md" />
            <span className="vcard-file-text">
              <span className="vcard-file-name ellipsis" title={top.fileName}>
                {top.fileName}
              </span>
              <span className="vcard-file-meta num">
                {topStrong ? <strong className="vcard-file-sent">{topLabel}</strong> : <span>{topLabel}</span>}
                <span>{formatDay(top.uploadedAt)}</span>
              </span>
            </span>
          </span>
        ) : (
          <span className="vcard-file muted">원본이 없습니다</span>
        )}
      </button>
      {base && (
        <div className="vcard-more">
          {middle.length > 0 && (
            <button
              type="button"
              className="vcard-toggle"
              aria-expanded={open}
              onClick={(e) => {
                e.stopPropagation()
                setOpen((v) => !v)
              }}
            >
              <IconChevron size={14} />
              수정본 {middle.length}건
            </button>
          )}
          {open && (
            <div className="vcard-revs">
              {middle.map((f) => (
                <span key={f.id} className="vcard-rev-line">
                  <span className="vcard-rev-label">{fileRevLabel(version, version.files.indexOf(f))}</span>
                  <span className="ellipsis" title={f.fileName}>
                    {f.fileName}
                  </span>
                </span>
              ))}
            </div>
          )}
          <span className="vcard-rev-line vcard-base">
            <span className="vcard-rev-label">원본</span>
            <span className="ellipsis" title={base.fileName}>
              {base.fileName}
            </span>
          </span>
        </div>
      )}
    </div>
  )
}
