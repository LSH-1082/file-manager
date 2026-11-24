import './GanttView.css'
import { useId, useState } from 'react'
import type { FormEvent, ReactNode } from 'react'
import { SENT_TO_MAX } from '../api/storage'
import { downloadFile } from '../fileActions'
import { dueTitleColor, dueWarnText, nextDue } from '../deadline'
import { useData } from '../store/useData'
import type { Doc, DocVersion, FileRev } from '../types'
import { formatPeriod } from './ganttScale'
import { groupColorClass, groupOf, sortGroups } from './scheduleModel'
import { categoryPath, docTitle, fileExt, formatDate, isUncategorized } from '../utils'
import {
  availableCancel,
  baseFile,
  CANCEL_LABEL,
  CANCEL_PHRASE,
  CANCEL_TO,
  STAGE_TO,
  chainFiles,
  currentFile,
  defaultVersionName,
  fileRevLabel,
  formatDay,
  hasChanges,
  hasRevision,
  latestVersion,
  activeRejection,
  isRejected,
  needsRevisionSinceRejection,
  REJECTED_NEEDS_REVISION,
  uploadBlocker,
  versionStage,
  workBlocker,
  revisedFiles,
  revisionFile,
  activeReview,
  decideBlocker,
  startBlocker,
  sendBlocker,
  storedVersionName,
  versionLabel,
  versionNameError,
  versionWorker,
  versionOwner,
  WORKER_NAME_MAX,
  workerNameError,
  STAGE_LABEL,
  VERSION_NAME_MAX,
  reviewerNames,
  lastReviewRound,
  reviewCopiesOf,
  REVIEW_RESULT_LABEL,
  reviewersOf,
} from '../versioning'
import AssignCategoryDialog from './AssignCategoryDialog'
import { FileThumb } from './files'
import { IconAlert, IconCheck, IconChevron, IconClose, IconDownload, IconEdit, IconPlus, IconUpload } from './icons'
import Modal from './Modal'
import PairPicker, { FileSlot } from './PairPicker'
import { StageBadge } from './StageBadge'
import { StageStepper } from './StageStepper'

interface Props {
  doc: Doc
  /** 보고 있는 버전. null이면 최신. 버전은 가운데 목록의 버전 줄에서 고른다 */
  versionId: string | null
  onVersionChange: (versionId: string) => void
  onDeleted: () => void
  /** 미분류 문서를 카테고리에 넣은 뒤 */
  onAssigned: (doc: Doc) => void
}

export default function DocView({ doc, versionId, onVersionChange, onDeleted, onAssigned }: Props) {
  const { categories, schedules } = useData()
  const [assigning, setAssigning] = useState(false)
  const category = categories.find((c) => c.id === doc.categoryId)
  const version = doc.versions.find((v) => v.id === versionId) ?? latestVersion(doc)
  // 문서 제목은 카테고리 이름이다
  const title = docTitle(categories, doc)
  // 머리의 점은 사이드바 카테고리 점과 같은 뜻이다: 이 문서 최신 버전의 단계 색(거절됨은 작업중)
  const stage = versionStage(latestVersion(doc))
  const stageLabel = STAGE_LABEL[stage]

  return (
    <article className="doc-panel">
      <header className="doc-panel-header">
        <div className="doc-heading">
          <div className="breadcrumb">
            {category ? (
              <>
                <span className={`dot stage-dot ${stage}`} role="img" aria-label={stageLabel} title={stageLabel} />
                {categoryPath(categories, category.id)}
              </>
            ) : (
              '미분류'
            )}
          </div>
          <h2 className="doc-title" style={{ color: dueTitleColor(nextDue(doc, schedules)) }}>
            {title}
          </h2>
          <div className="doc-dates num">
            <div>등록 {formatDate(doc.createdAt)}</div>
            <div>수정 {formatDate(doc.updatedAt)}</div>
          </div>
        </div>
        {/* 분류가 없는 문서만 카테고리에 넣을 수 있다. 분류가 있는 문서의 이동은 없다 */}
        {isUncategorized(categories, doc) && (
          <button type="button" className="btn" onClick={() => setAssigning(true)}>
            분류 지정
          </button>
        )}
      </header>

      <DocSchedules doc={doc} />
      <VersionSection key={version.id} doc={doc} version={version} onVersionChange={onVersionChange} onDeleted={onDeleted} />
      {assigning && <AssignCategoryDialog doc={doc} onClose={() => setAssigning(false)} onAssigned={onAssigned} />}
    </article>
  )
}

/** 이 문서가 들어 있는 일정(발송 문서)들. 발송 문서가 없으면 그리지 않는다 */
function DocSchedules({ doc }: { doc: Doc }) {
  const { schedules, scheduleGroups } = useData()
  const groups = sortGroups(scheduleGroups)
  const mine = schedules
    .filter((s) => s.documentId === doc.id)
    .sort((a, b) => (a.plannedSendDate < b.plannedSendDate ? -1 : a.plannedSendDate > b.plannedSendDate ? 1 : 0))
  if (mine.length === 0) return null
  return (
    <section className="doc-schedules" aria-labelledby={`doc-schedules-${doc.id}`}>
      <h3 id={`doc-schedules-${doc.id}`}>일정</h3>
      <ul>
        {mine.map((s) => {
          const group = groupOf(s, groups)
          const warn = dueWarnText(s, doc)
          return (
            <li key={s.id}>
              <span className={`doc-schedule-swatch ${group ? groupColorClass(group.colorNo) : 'none'}`} aria-hidden="true" />
              <span className="doc-schedule-name">{group ? group.name : '일정과 관계없음'}</span>
              {group && <span className="doc-schedule-period num">일정 {formatPeriod(group.startDate, group.endDate)}</span>}
              <span className="doc-schedule-period num">발송 {formatPeriod(s.startDate, s.plannedSendDate)}</span>
              {s.memo && <span className="doc-schedule-memo">{s.memo}</span>}
              {warn && <span className="gantt-out-tag">{warn}</span>}
            </li>
          )
        })}
      </ul>
    </section>
  )
}

type Dialog = 'work' | 'review' | 'send' | 'cancel' | 'newVersion' | 'upload' | 'delete' | 'deleteRevision' | 'reject' | null

/** 확인 창. 실행 버튼은 실행할 일의 이름이고, 닫는 버튼은 '닫기'라서 '○○ 취소' 같은 말과 겹치지 않는다 */
function ActionConfirm({
  title,
  message,
  confirmLabel,
  onConfirm,
  onClose,
}: {
  title: string
  message: ReactNode
  confirmLabel: string
  onConfirm: () => void
  onClose: () => void
}) {
  return (
    <Modal
      title={title}
      onClose={onClose}
      footer={
        <>
          <button type="button" className="btn" onClick={onClose}>
            닫기
          </button>
          <button
            type="button"
            className="btn danger"
            onClick={() => {
              onConfirm()
              onClose()
            }}
          >
            {confirmLabel}
          </button>
        </>
      }
    >
      <p className="confirm-message">{message}</p>
    </Modal>
  )
}

function VersionSection({
  doc,
  version,
  onVersionChange,
  onDeleted,
}: {
  doc: Doc
  version: DocVersion
  onVersionChange: (versionId: string) => void
  onDeleted: () => void
}) {
  const { cancelStep, decideReview, deleteLatestRevision, removeVersion } = useData()
  const [dialog, setDialog] = useState<Dialog>(null)
  const [stepError, setStepError] = useState('')
  // 가운데 수정본('수정본 n건')은 기본 접힘
  const [middleOpen, setMiddleOpen] = useState(false)

  const working = version.status === 'working'
  const isLatest = version.id === latestVersion(doc).id
  const base = baseFile(version)
  const current = currentFile(version)
  // 파일 없이 만든 v1: 원본을 올리기 전이라 파일이 0개다
  const empty = !base || !current
  const copies = current ? reviewCopiesOf(version, current.id) : []
  const revised = revisedFiles(version)
  const { top, middle } = chainFiles(version)
  const currentRevision = revisionFile(version)
  const blocker = working ? sendBlocker(doc, version) : null
  const workStop = working ? workBlocker(doc, version) : null
  // v2 이상에서 수정본이 아직 없다: 작업완료가 막혀 있고 지금 할 일은 수정본 올리기다
  const needsRevised = working && versionStage(version) === 'working' && version.number > 1 && revised.length === 0
  const startStop = working ? startBlocker(version) : null
  const decideStop = working ? decideBlocker(version) : null
  const stage = versionStage(version)
  const rejection = activeRejection(version)
  const rejected = isRejected(version)
  // 거절 뒤 새 수정본(+대조표)을 올려야 작업완료할 수 있다. 판정은 서버와 같은 versioning 한 곳
  const rejectLock = working && stage === 'working' && needsRevisionSinceRejection(version)
  const workDoneRecord = version.workDone
  const startRecord = version.reviewStart
  const approval = activeReview(version)
  // 작업완료 뒤에는 수정본·대조표를 바꿀 수 없다. 작업중일 때만 올리기와 최신 수정본 × 삭제가 있다
  const editable = working && stage === 'working'
  const lockNote = working && !editable ? uploadBlocker(version) : null
  // 지금 단계의 취소 하나 (최신 버전만). 확인 창을 거친다
  const cancel = availableCancel(doc, version)
  // 발송 전 확인의 작업·검토 항목: 작업은 항목마다 한 줄씩(작업자, 작업완료 시각), 검토는 현재 상태 줄과 '검토 n건' 토글(회차마다 결과·검토자·시각)
  const workLines: CheckRow[] =
    stage !== 'working'
      ? [
          { k: '작업자', v: versionOwner(version) ?? '-' },
          { k: '작업완료', v: workDoneRecord ? formatDate(workDoneRecord.completedAt) : '-' },
        ]
      : [{ v: rejected ? `${STAGE_LABEL.working} · 거절됨, ${rejectLock ? '수정본을 올린 뒤 작업완료해야 합니다' : '작업완료를 다시 해야 합니다'}` : STAGE_LABEL.working }]
  const reviewerLines = (names: string[]): CheckRow[] => names.map((name) => ({ k: '검토자', v: name }))
  const startLine: CheckRow = { k: '검토 시작', v: startRecord ? formatDate(startRecord.startedAt) : '-' }
  // 현재 상태 줄(토글 밖). 지나간 결정은 아래 회차 줄(토글 안)에서 본다
  const reviewLines: CheckRow[] =
    stage === 'reviewing' && startRecord
      ? [...reviewerLines(startRecord.reviewers), startLine, { k: '결과', v: '검토중' }]
      : rejected && rejection
        ? [{ k: '결과', v: '거절됨' }]
        : (stage === 'ready' || stage === 'sent') && approval
          ? [{ k: '결과', v: '승인' }]
          : [{ v: '검토 전' }]
  const rounds = version.reviews
  const reviewToggle = rounds.length
    ? {
        label: `검토 ${rounds.length}건`,
        lines: rounds.map((r) => ({
          k: `${r.round}회차`,
          v: `${REVIEW_RESULT_LABEL[r.result]} · ${r.reviewers.join(', ')} · ${formatDate(r.reviewedAt)}`,
        })),
      }
    : undefined
  const prevVersion = doc.versions.find((v) => v.number === version.number - 1)
  const worker = versionWorker(version)
  const baseLabel = version.number === 1 || !prevVersion ? `${versionLabel(version)} 최초 업로드` : `${versionLabel(prevVersion)} 제출본`
  const nextLabel = fileRevLabel(version, version.files.length)
  // 지금 작업하는 파일(발송 전 버전의 마지막 파일) 또는 발송된 파일(발송된 버전의 마지막 파일)에 다는 단계 표식
  const currentMark = (
    <span className="file-sent">
      <StageBadge stage={stage} worker={worker} reviewers={reviewersOf(version)} />
      {!working && <span className="num">{formatDay(version.sentAt!)}</span>}
    </span>
  )
  const baseIsCurrent = !!base && base.id === current?.id

  // 거절 때 올린 검토본 줄. 검토한 파일 행의 아래에 수정대조표 줄과 같은 형식으로, 회차 순으로 붙는다
  const reviewCopyLines = (f: FileRev) =>
    reviewCopiesOf(version, f.id).map((r) => (
      <div key={`review-${r.round}`} className="chain-line rev review-copy">
        <span className="chain-label">
          <span className="chain-rev-mark">└ 검토본</span>
        </span>
        <FileLineBody file={r.reviewFile} />
      </div>
    ))

  const baseRow = base && (
    <li className={`chain-row base ${baseIsCurrent ? 'marked' : ''}`}>
      <div className="chain-line">
        <span className="chain-label">
          <span className="chain-badge base">원본</span>
          {/* 라벨 열이 좁아 두 줄로 나눈다. 길면 말줄임이고 전체는 title로 */}
          <span className="chain-sub">{working || revised.length > 0 ? '대조 기준' : '제출본'}</span>
          <span className="chain-sub" title={baseLabel}>
            {baseLabel}
          </span>
        </span>
        <FileLineBody file={base} mark={baseIsCurrent ? currentMark : null} />
      </div>
      {reviewCopyLines(base)}
    </li>
  )

  // 수정본 한 줄과 그 수정대조표 줄. 맨 위 줄(최신 수정본)만 현재 표식이 붙고, 작업중일 때 × 삭제 버튼이 붙는다
  const topSub = stage === 'ready' ? '발송할 파일' : working ? '현재 수정본' : '발송 때 제출'
  const revisedRow = (f: FileRev, isTop: boolean) => {
    const revision = f.revision
    return (
      <li key={f.id} className={`chain-row ${isTop ? 'current marked' : 'past'}`}>
        <div className="chain-line">
          <span className="chain-label">
            <span className="chain-badge">{fileRevLabel(version, version.files.indexOf(f))}</span>
            <span className="chain-sub">{isTop ? topSub : '이전 수정본'}</span>
          </span>
          <FileLineBody file={f} mark={isTop ? currentMark : null} onDelete={isTop && editable ? () => setDialog('deleteRevision') : undefined} />
        </div>
        <div className={`chain-line rev ${revision ? '' : 'none'}`}>
          <span className="chain-label">
            <span className="chain-rev-mark">└ 수정대조표</span>
          </span>
          {revision ? <FileLineBody file={revision} /> : <span className="file-same">대조표 없음</span>}
        </div>
        {reviewCopyLines(f)}
      </li>
    )
  }

  const approve = async () => {
    setStepError('')
    try {
      await decideReview(doc.id, 'approve')
    } catch (err) {
      setStepError(err instanceof Error ? err.message : String(err))
    }
  }

  return (
    <section className="version-section">
      <div className="version-heading">
        <VersionTitle doc={doc} version={version} />
        {/* 지금 단계는 바로 아래 단계표가 말한다. 머리에는 날짜만 두고, 발송처는 발송 기록에서만 본다 */}
        <span className="muted num version-date">{working ? `${formatDate(version.createdAt)} 시작` : formatDay(version.sentAt!)}</span>
        {/* 발송하지 않은 버전만 지울 수 있다. 발송된 버전에는 삭제가 없다 */}
        {working && isLatest && (
          <button type="button" className="btn text danger version-delete" onClick={() => setDialog('delete')}>
            이 버전 삭제
          </button>
        )}
      </div>

      {/* 진행 단계 5칸: 작업중 → 검토대기 → 검토중 → 발송대기 → 발송 */}
      <StageStepper stage={stage} rejected={rejected} />
      {/* 거절은 단계가 아니라 작업중으로 돌아간 이유다. 누가 언제 거절했는지 눈에 띄게, 이 한 곳에서만 말한다 */}
      {rejected && rejection && (
        <p className="reject-note" role="status">
          <IconAlert size={14} />
          <span>
            <strong>거절됨</strong> · {namesOf(rejection.reviewers)} · <span className="num">{formatDate(rejection.reviewedAt)}</span>
          </span>
        </p>
      )}

      {/* 모든 단계에서 같은 순서: 맨 위 최신 수정본, 가운데 '수정본 n건'(기본 접힘), 맨 아래 원본. 수정본마다 그 수정대조표를 바로 아래에 붙인다 */}
      <section className="file-pair" aria-labelledby={`files-${version.id}`}>
        <div className="chain-header">
          <h3 id={`files-${version.id}`}>파일</h3>
          <p className="muted small">수정대조표는 모두 맨 아래 원본과 비교해 작성합니다.</p>
        </div>
        <ol className={`file-chain ${version.status}`}>
          {revised.length === 0 ? (
            <>
              {/* 수정본 없이 그대로 발송한 버전은 원본이 곧 제출본이라 빈 안내 줄을 두지 않는다 */}
              {working && (
                <li className="chain-row empty-row">
                  <span className="muted">
                    {empty
                      ? '원본이 없습니다. 원본 파일 하나를 올려야 작업완료할 수 있습니다.'
                      : `아직 수정본이 없습니다.${editable ? ' 수정본을 올릴 때 그 수정본의 수정대조표를 함께 올립니다.' : ''}`}
                  </span>
                </li>
              )}
              {baseRow}
            </>
          ) : (
            <>
              {revisedRow(top!, true)}
              {middle.length > 0 && (
                <li className="chain-toggle">
                  <button type="button" aria-expanded={middleOpen} onClick={() => setMiddleOpen((open) => !open)}>
                    <IconChevron size={14} />
                    수정본 {middle.length}건
                  </button>
                </li>
              )}
              {middleOpen && middle.map((f) => revisedRow(f, false))}
              {baseRow}
            </>
          )}
        </ol>
        {editable && (
          <div className="chain-actions">
            <button type="button" className="btn" onClick={() => setDialog('upload')}>
              <IconUpload size={14} />
              {nextLabel} 올리기
            </button>
            <span className="muted small">{empty ? '파일 하나를 고르면 이 버전의 원본이 됩니다.' : '수정본과 수정대조표를 함께 올려야 등록됩니다.'}</span>
          </div>
        )}
        {lockNote && <p className="chain-lock muted small">{lockNote}</p>}
      </section>

      {/* 발송은 위 파일을 모두 확인한 뒤 맨 아래에서 한다 */}
      <section className={`send-block ${version.status}`} aria-labelledby={`send-${version.id}`}>
        <h3 id={`send-${version.id}`}>{working ? '발송 전 확인' : `${versionLabel(version)} 발송 완료`}</h3>
        {working ? (
          <ul className="send-checks">
            <CheckLine ok={!empty} quiet={empty} label="원본" value={base?.fileName ?? '없음'} />
            {/* v2부터는 원본 그대로 작업완료·발송을 할 수 없으므로 수정본이 없으면 미충족으로 보인다 */}
            <CheckLine
              ok={!needsRevised && !empty}
              quiet={needsRevised || empty}
              label="발송할 파일"
              value={
                revised.length
                  ? `${fileRevLabel(version, version.files.length - 1)} · ${current?.fileName}`
                  : empty
                    ? '원본 없음'
                    : needsRevised
                    ? '수정본 없음'
                    : `원본 그대로 · ${current?.fileName}`
              }
            />
            {revised.length > 0 && (
              <CheckLine
                ok={!hasChanges(doc, version) || hasRevision(version)}
                label="수정대조표"
                value={currentRevision ? currentRevision.fileName : '없음'}
              />
            )}
            {/* 단계 판정(versionStage)과 같은 기록을 읽는다 */}
            <CheckLine ok={stage !== 'working'} quiet label="작업" lines={workLines} />
            <CheckLine ok={stage === 'ready'} quiet label="검토" lines={reviewLines} toggle={reviewToggle} />
          </ul>
        ) : (
          <>
            {/* 발송 전 확인과 같은 형식: 항목 이름과 값을 한 줄씩. 검토는 현재 상태와 검토 n건 토글(회차마다 결과·검토자·시각) */}
            <ul className="send-checks">
              <CheckLine
                ok
                label="제출본"
                lines={[
                  { k: '파일', v: current?.fileName ?? '-' },
                  { k: '대조표', v: currentRevision ? currentRevision.fileName : '-' },
                ]}
              />
              <CheckLine ok quiet label="작업" lines={workLines} />
              <CheckLine ok quiet label="검토" lines={reviewLines} toggle={reviewToggle} />
              <CheckLine
                ok
                quiet
                label="발송"
                lines={[
                  { k: '발송일', v: formatDay(version.sentAt!) },
                  { k: '발송처', v: version.sentTo || '-' },
                  { k: '발송 메모', v: version.sentMemo || '-' },
                ]}
              />
            </ul>
            <p className="muted small">발송된 버전은 수정할 수 없습니다.</p>
          </>
        )}
        {needsRevised || empty ? (
          <p className="notice" role="status">
            <IconAlert size={14} />
            지금 할 일은 {nextLabel} 올리기입니다. {workStop}
          </p>
        ) : rejectLock ? (
          <p className="notice" role="status">
            <IconAlert size={14} />
            지금 할 일은 {nextLabel} 올리기입니다. {REJECTED_NEEDS_REVISION}
          </p>
        ) : (
          blocker &&
          !rejection && (
            <p className="notice" role="status">
              <IconAlert size={14} />
              {blocker}
            </p>
          )
        )}
        {stepError && <p className="form-error">{stepError}</p>}
        <div className="actions">
          {working && (
            <>
              {/* 지금 단계에서 할 수 있는 동작만 보인다: 작업중 → 작업완료, 검토대기 → 검토 시작, 검토중 → 거절·승인, 발송대기 → 발송. 취소는 그 앞에 하나 */}
              {cancel && (
                <button type="button" className="btn" onClick={() => setDialog('cancel')}>
                  {CANCEL_LABEL[cancel]}
                </button>
              )}
              {stage === 'working' && (
                <button type="button" className="btn primary" disabled={!!workStop} title={workStop ?? ''} onClick={() => setDialog('work')}>
                  작업완료
                </button>
              )}
              {stage === 'queued' && (
                <button type="button" className="btn primary" disabled={!!startStop} title={startStop ?? ''} onClick={() => setDialog('review')}>
                  검토 시작
                </button>
              )}
              {stage === 'reviewing' && (
                <>
                  <button type="button" className="btn" disabled={!!decideStop} title={decideStop ?? ''} onClick={() => setDialog('reject')}>
                    거절
                  </button>
                  <button type="button" className="btn primary" disabled={!!decideStop} title={decideStop ?? ''} onClick={approve}>
                    승인
                  </button>
                </>
              )}
              {stage === 'ready' && (
                <button type="button" className="btn primary" disabled={!!blocker} title={blocker ?? ''} onClick={() => setDialog('send')}>
                  발송
                </button>
              )}
            </>
          )}
          {!working && isLatest && (
            <>
              {cancel && (
                <button type="button" className="btn" onClick={() => setDialog('cancel')}>
                  {CANCEL_LABEL[cancel]}
                </button>
              )}
              <button type="button" className="btn primary" onClick={() => setDialog('newVersion')}>
                새 버전 시작
              </button>
            </>
          )}
        </div>
      </section>

      {dialog === 'upload' && (
        <RevisedUploadDialog doc={doc} version={version} label={nextLabel} baseName={base?.fileName} onClose={() => setDialog(null)} />
      )}
      {dialog === 'work' && <WorkDoneDialog doc={doc} version={version} onClose={() => setDialog(null)} />}
      {dialog === 'review' && <ReviewDialog doc={doc} version={version} onClose={() => setDialog(null)} />}
      {dialog === 'reject' && <RejectDialog doc={doc} version={version} onClose={() => setDialog(null)} />}
      {dialog === 'send' && <SendDialog doc={doc} version={version} onClose={() => setDialog(null)} />}
      {dialog === 'newVersion' && (
        <NewVersionDialog doc={doc} onClose={() => setDialog(null)} onStarted={onVersionChange} />
      )}
      {dialog === 'delete' && (
        <ActionConfirm
          title="이 버전 삭제"
          confirmLabel="삭제"
          message={
            <>
              {doc.versions.length === 1
                ? `${versionLabel(version)}의 원본을 포함한 모든 파일이 삭제되고 이 분류는 문서가 없는 상태가 됩니다. 첫 파일 올리기부터 다시 해야 합니다.`
                : `${versionLabel(version)}의 수정본·수정대조표·검토 기록이 삭제되고 ${versionLabel(doc.versions[doc.versions.length - 2])} 제출본이 최신이 됩니다.`}{' '}
              되돌릴 수 없습니다.
            </>
          }
          onConfirm={async () => {
            setStepError('')
            try {
              const updated = await removeVersion(doc.id, version.id)
              if (updated) onVersionChange(latestVersion(updated).id)
              else onDeleted()
            } catch (err) {
              setStepError(err instanceof Error ? err.message : String(err))
            }
          }}
          onClose={() => setDialog(null)}
        />
      )}
      {dialog === 'deleteRevision' && editable && revised.length > 0 && (
        <ActionConfirm
          title="수정본 삭제"
          confirmLabel="삭제"
          message={
            <>
              {fileRevLabel(version, version.files.length - 1)}({current?.fileName})
              {currentRevision ? `과 수정대조표(${currentRevision.fileName})` : ''}를 삭제합니다.{' '}
              {revised.length > 1 ? fileRevLabel(version, version.files.length - 2) : '원본'}이 현재 수정본이 됩니다.
              {copies.length > 0 ? ` 이 수정본에 붙은 검토본 ${copies.length}개도 함께 삭제됩니다.` : ''} 되돌릴 수 없습니다.
            </>
          }
          onConfirm={async () => {
            setStepError('')
            try {
              await deleteLatestRevision(doc.id, current!.id)
            } catch (err) {
              setStepError(err instanceof Error ? err.message : String(err))
            }
          }}
          onClose={() => setDialog(null)}
        />
      )}
      {dialog === 'cancel' && cancel && (
        <ActionConfirm
          title={CANCEL_LABEL[cancel]}
          confirmLabel={CANCEL_LABEL[cancel]}
          message={
            <>
              {versionLabel(version)} 버전의 <strong>{CANCEL_PHRASE[cancel]}</strong>하고 {STAGE_TO[CANCEL_TO[cancel]]} 되돌릴까요?
              {cancel === 'reject' &&
                (lastReviewRound(version)?.reviewFile
                  ? ` 거절 때 올린 검토본(${lastReviewRound(version)?.reviewFile?.fileName})과 이 회차의 검토 기록도 함께 삭제됩니다.`
                  : ' 이 회차의 검토 기록도 함께 삭제됩니다.')}
              {cancel === 'approve' && ' 이 회차의 검토 기록도 함께 삭제됩니다.'}
            </>
          }
          onConfirm={async () => {
            setStepError('')
            try {
              await cancelStep(doc.id, cancel)
            } catch (err) {
              setStepError(err instanceof Error ? err.message : String(err))
            }
          }}
          onClose={() => setDialog(null)}
        />
      )}
    </section>
  )
}

/** 버전 이름. 발송 전 버전은 작은 편집 버튼으로 이름을 고친다. 발송된 버전은 고칠 수 없다 */
function VersionTitle({ doc, version }: { doc: Doc; version: DocVersion }) {
  const { renameVersion } = useData()
  const [editing, setEditing] = useState(false)
  const [value, setValue] = useState('')
  const [error, setError] = useState('')
  const hintId = useId()

  const startEdit = () => {
    setValue(versionLabel(version))
    setError('')
    setEditing(true)
  }

  const save = async (e: FormEvent) => {
    e.preventDefault()
    e.stopPropagation()
    const invalid = versionNameError(doc.versions, version, value)
    if (invalid) return setError(invalid)
    try {
      await renameVersion(doc.id, version.id, value)
      setEditing(false)
    } catch (err) {
      setError(err instanceof Error ? err.message : String(err))
    }
  }

  if (editing) {
    return (
      <div className="version-title editing">
        <form className="version-name-form" onSubmit={save}>
          <input
            autoFocus
            aria-label="버전 이름"
            maxLength={VERSION_NAME_MAX}
            title="버전 이름 최대 5자"
            aria-describedby={hintId}
            value={value}
            placeholder={defaultVersionName(version.number)}
            onChange={(e) => {
              setValue(e.target.value)
              setError('')
            }}
            onKeyDown={(e) => {
              if (e.key === 'Escape') setEditing(false)
            }}
          />
          <button type="submit" className="btn small">
            저장
          </button>
          <button type="button" className="btn small text" onClick={() => setEditing(false)}>
            취소
          </button>
          <span className="field-hint" id={hintId}>
            최대 {VERSION_NAME_MAX}자
          </span>
        </form>
        {error && <p className="form-error">{error}</p>}
      </div>
    )
  }

  return (
    <div className="version-title">
      <h3 className="num" title={versionLabel(version)}>
        {versionLabel(version)}
      </h3>
      {version.status === 'working' && (
        <button type="button" className="icon-btn small" title="버전 이름 수정" aria-label="버전 이름 수정" onClick={startEdit}>
          <IconEdit size={14} />
        </button>
      )}
    </div>
  )
}

/** 발송 전 확인 항목 안의 한 줄: 이름(k)과 값(v). k가 없으면 값만 */
interface CheckRow {
  k?: string
  v: string
}

/**
 * 발송 전 확인 한 항목. value면 한 줄, lines면 줄마다 이름과 값을 나란히 놓는다(긴 이름은 말줄임 + title).
 * quiet: 아직 안 된 일을 빨강 없이 알린다(검토 전은 오류가 아니라 할 일)
 */
function CheckLine({
  ok,
  label,
  value,
  lines,
  toggle,
  quiet = false,
}: {
  ok: boolean
  label: string
  value?: string
  lines?: CheckRow[]
  /** 줄 뒤에 접어 두는 하위 줄(기본 접힘) */
  toggle?: { label: string; lines: CheckRow[] }
  quiet?: boolean
}) {
  const [open, setOpen] = useState(false)
  return (
    <li className={`send-check ${ok ? 'ok' : 'no'} ${quiet ? 'quiet' : ''}`}>
      <span className="send-check-mark" aria-hidden="true">
        {ok ? <IconCheck size={14} /> : <IconAlert size={14} />}
      </span>
      <span className="send-check-label">{label}</span>
      {lines ? (
        <div className="send-lines">
          {lines.map((line, i) => (
            <div key={i} className={`send-line ${line.k ? '' : 'plain'}`}>
              {line.k && <span className="send-line-k">{line.k}</span>}
              <span className="ellipsis" title={line.v}>
                {line.v}
              </span>
            </div>
          ))}
          {toggle && (
            <>
              <button type="button" className="check-toggle" aria-expanded={open} onClick={() => setOpen((v) => !v)}>
                <IconChevron size={14} />
                {toggle.label}
              </button>
              {open && (
                <div className="check-rounds">
                  {toggle.lines.map((line, i) => (
                    <div key={i} className="send-line review-round">
                      <span className="send-line-k">{line.k}</span>
                      <span className="ellipsis" title={line.v}>
                        {line.v}
                      </span>
                    </div>
                  ))}
                </div>
              )}
            </>
          )}
        </div>
      ) : (
        <span className="ellipsis" title={value}>
          {value}
        </span>
      )}
    </li>
  )
}

/** 파일 행의 본문: 32px 축소본, 파일명(+ 단계 표식)과 메타(형식·올린 때), 다운로드. onDelete가 있으면 다운로드 왼쪽에 × 삭제 */
function FileLineBody({ file, mark, onDelete }: { file: FileRev; mark?: ReactNode; onDelete?: () => void }) {
  return (
    <>
      <FileThumb file={file} />
      <div className="file-info">
        <div className="file-name-row">
          <div className="file-name" title={file.fileName}>
            {file.fileName}
          </div>
          {mark}
        </div>
        <div className="meta num" title={`${fileExt(file.fileName)} · ${formatDate(file.uploadedAt)} · ${file.uploadedBy ?? '-'} 올림`}>
          {fileExt(file.fileName)} · {formatDate(file.uploadedAt)} · {file.uploadedBy ?? '-'} 올림
        </div>
      </div>
      {onDelete && (
        <button type="button" className="icon-btn file-del" onClick={onDelete} title="수정본 삭제" aria-label={`${file.fileName} 삭제`}>
          <IconClose size={14} />
        </button>
      )}
      <button
        type="button"
        className="icon-btn file-dl"
        onClick={() => downloadFile(file)}
        title="다운로드"
        aria-label={`${file.fileName} 다운로드`}
      >
        <IconDownload />
      </button>
    </>
  )
}

/** 발송 전 버전에 수정본 하나를 그 수정대조표와 함께 올린다. baseName이 없으면(파일 0개인 버전) 파일 하나만 원본으로 올린다 */
function RevisedUploadDialog({
  doc,
  version,
  label,
  baseName,
  onClose,
}: {
  doc: Doc
  version: DocVersion
  label: string
  baseName: string | undefined
  onClose: () => void
}) {
  const { uploadWorkingFile } = useData()
  const [file, setFile] = useState<File | null>(null)
  const [revision, setRevision] = useState<File | null>(null)
  // 작업자 이름: 이 버전의 작업자가 기본값이고 바꿀 수 있다
  const [worker, setWorker] = useState(versionWorker(version) ?? '')
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')
  const hintId = useId()

  const submit = async () => {
    if (!file) return setError(baseName ? '수정본과 수정대조표를 모두 고르세요.' : '원본 파일을 고르세요.')
    if (baseName && !revision) return setError('수정본과 수정대조표를 모두 고르세요.')
    const invalid = workerNameError(worker)
    if (invalid) return setError(invalid)
    setBusy(true)
    try {
      await uploadWorkingFile(doc.id, file, revision ?? undefined, worker)
      onClose()
    } catch (err) {
      setBusy(false)
      setError(err instanceof Error ? err.message : String(err))
    }
  }

  return (
    <Modal
      title={`${versionLabel(version)} ${label} 올리기`}
      onClose={onClose}
      footer={
        <>
          <button type="button" className="btn" onClick={onClose}>
            취소
          </button>
          <button type="button" className="btn primary" onClick={submit} disabled={!file || (!!baseName && !revision) || !worker.trim() || busy}>
            {busy ? '등록 중…' : '등록'}
          </button>
        </>
      }
    >
      <div className="form">
        <label className="field">
          <span>작업자 이름</span>
          <input
            aria-label="작업자 이름"
            aria-describedby={hintId}
            required
            aria-required="true"
            value={worker}
            maxLength={WORKER_NAME_MAX}
            onChange={(e) => {
              setWorker(e.target.value)
              setError('')
            }}
          />
          <span className="field-hint" id={hintId}>
            필수 · 최대 {WORKER_NAME_MAX}자 · 이 파일을 올린 사람으로 남습니다
          </span>
        </label>
        {baseName ? (
          <PairPicker label={label} baseName={baseName} file={file} revision={revision} onFile={setFile} onRevision={setRevision} />
        ) : (
          <FileSlot
            title="원본 파일"
            file={file}
            onFile={(f) => {
              setFile(f)
              setError('')
            }}
            onReject={setError}
          />
        )}
        {error && <p className="form-error">{error}</p>}
      </div>
    </Modal>
  )
}

function today() {
  const d = new Date()
  const pad = (n: number) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`
}

function SendDialog({ doc, version, onClose }: { doc: Doc; version: DocVersion; onClose: () => void }) {
  const { sendVersion } = useData()
  // 이전 발송처를 기본값으로
  const lastSentTo = [...doc.versions].reverse().find((v) => v.sentTo)?.sentTo ?? ''
  const [sentAt, setSentAt] = useState(today())
  const [sentTo, setSentTo] = useState(lastSentTo)
  const [sentMemo, setSentMemo] = useState('')
  const [error, setError] = useState('')
  const hintId = useId()
  const revision = revisionFile(version)

  const submit = async (e: FormEvent) => {
    e.preventDefault()
    e.stopPropagation()
    if (!sentTo.trim()) return setError('발송처를 입력하세요.')
    try {
      // 날짜만 입력받으므로 현지 정오로 저장해 시간대 때문에 날짜가 바뀌지 않게 한다
      await sendVersion(doc.id, { sentAt: new Date(`${sentAt}T12:00:00`).toISOString(), sentTo: sentTo.trim(), sentMemo: sentMemo.trim() })
      onClose()
    } catch (err) {
      setError(err instanceof Error ? err.message : String(err))
    }
  }

  return (
    <Modal
      title={`${versionLabel(version)} 발송`}
      onClose={onClose}
      footer={
        <>
          <button type="button" className="btn" onClick={onClose}>
            취소
          </button>
          <button type="submit" form="send-form" className="btn primary">
            발송
          </button>
        </>
      }
    >
      <form id="send-form" className="form" onSubmit={submit}>
        <p className="muted small">
          발송하면 버전({versionLabel(version)})이 종료되어 제출본({currentFile(version)?.fileName})
          {revision ? `과 수정대조표(${revision.fileName})` : ''}가 고정됩니다. 다음 버전은 이 제출본을 원본으로 시작합니다.
        </p>
        <label className="field">
          <span>발송일</span>
          <input type="date" value={sentAt} max={today()} onChange={(e) => setSentAt(e.target.value)} required />
        </label>
        <label className="field">
          <span>발송처</span>
          <input
            autoFocus
            value={sentTo}
            maxLength={SENT_TO_MAX}
            aria-describedby={hintId}
            onChange={(e) => setSentTo(e.target.value)}
            placeholder="예: OO기관 계약팀"
          />
          <span className="field-hint" id={hintId}>
            최대 {SENT_TO_MAX}자
          </span>
        </label>
        <label className="field">
          <span>메모</span>
          <textarea rows={2} value={sentMemo} maxLength={500} onChange={(e) => setSentMemo(e.target.value)} placeholder="문서번호, 발송 방법 등 (선택)" />
        </label>
        {error && <p className="form-error">{error}</p>}
      </form>
    </Modal>
  )
}

/** 검토자 이름들을 한 줄로 */
function namesOf(reviewers: string[]) {
  return reviewers.join(', ')
}

/** 작업완료: 입력 없이 확인만 한다. 작업완료 기록에는 시각만 남는다 */
function WorkDoneDialog({ doc, version, onClose }: { doc: Doc; version: DocVersion; onClose: () => void }) {
  const { markWorkDone } = useData()
  const [error, setError] = useState('')
  const [busy, setBusy] = useState(false)

  const submit = async (e: FormEvent) => {
    e.preventDefault()
    e.stopPropagation()
    setBusy(true)
    try {
      await markWorkDone(doc.id)
      onClose()
    } catch (err) {
      setBusy(false)
      setError(err instanceof Error ? err.message : String(err))
    }
  }

  return (
    <Modal
      title={`${versionLabel(version)} 작업완료`}
      onClose={onClose}
      footer={
        <>
          <button type="button" className="btn" onClick={onClose}>
            취소
          </button>
          <button type="submit" form="work-form" className="btn primary" disabled={busy}>
            {busy ? '저장 중…' : '작업완료'}
          </button>
        </>
      }
    >
      <form id="work-form" className="form" onSubmit={submit}>
        <p className="muted small">
          현재 {revisedFiles(version).length > 0 ? '수정본' : '원본'}({currentFile(version)?.fileName})의 작업을 마쳤다고 표시합니다. 검토대기가 되고, 이어서 검토를 시작합니다.
          작업완료 뒤에는 파일을 바꿀 수 없습니다. 바꾸려면 작업완료를 취소하세요.
        </p>
        {error && <p className="form-error">{error}</p>}
      </form>
    </Modal>
  )
}

/**
 * 거절(검토중 → 작업중, 거절됨): 검토본 파일 한 개(.hwpx/.pdf)와 검토자(검토 시작 때 적은 사람 중 한 명, 한 명이면 자동)를 받는다.
 * 검토본은 지금 검토한 파일 아래에 붙는다
 */
function RejectDialog({ doc, version, onClose }: { doc: Doc; version: DocVersion; onClose: () => void }) {
  const { decideReview } = useData()
  const reviewers = version.reviewStart?.reviewers ?? []
  const [reviewer, setReviewer] = useState(reviewers.length === 1 ? reviewers[0] : '')
  const [file, setFile] = useState<File | null>(null)
  const [rejected, setRejected] = useState<string | null>(null)
  const [error, setError] = useState('')
  const [busy, setBusy] = useState(false)
  const target = currentFile(version)
  const targetKind = revisedFiles(version).length > 0 ? '수정본' : '원본'

  const submit = async () => {
    if (!file || !reviewer) return setError('검토자와 검토본 파일을 고르세요.')
    setBusy(true)
    try {
      await decideReview(doc.id, 'reject', { reviewer, file })
      onClose()
    } catch (err) {
      setBusy(false)
      setError(err instanceof Error ? err.message : String(err))
    }
  }

  return (
    <Modal
      title={`${versionLabel(version)} 거절`}
      onClose={onClose}
      footer={
        <>
          <button type="button" className="btn" onClick={onClose}>
            닫기
          </button>
          <button type="button" className="btn primary" onClick={submit} disabled={!file || !reviewer || busy}>
            {busy ? '저장 중…' : '거절'}
          </button>
        </>
      }
    >
      <div className="form">
        <p className="muted small">
          검토한 {targetKind}({target?.fileName}) 아래에 검토본으로 붙고, 작업중(거절됨)으로 돌아갑니다. 거절 사유는 검토본에 적습니다.
        </p>
        {reviewers.length > 1 ? (
          <label className="field">
            <span>검토자</span>
            <select
              aria-label="검토자"
              value={reviewer}
              onChange={(e) => {
                setReviewer(e.target.value)
                setError('')
              }}
            >
              <option value="" disabled>
                검토자를 고르세요
              </option>
              {reviewers.map((n) => (
                <option key={n} value={n}>
                  {n}
                </option>
              ))}
            </select>
            <span className="field-hint">검토본을 올린 사람으로 남습니다</span>
          </label>
        ) : (
          <p className="pair-picker-base">
            검토자 <strong className="ellipsis">{reviewers[0]}</strong>
          </p>
        )}
        <div className="pair-picker">
          <div className="pair-picker-slots">
            <FileSlot
              title="검토본 파일"
              file={file}
              onFile={(f) => {
                setRejected(null)
                setError('')
                setFile(f)
              }}
              onReject={(m) => {
                setFile(null)
                setRejected(m)
              }}
            />
          </div>
          {rejected && <p className="form-error">{rejected}</p>}
          <p className="hint">검토본은 한 개만, .hwpx·.pdf 파일만 올릴 수 있습니다.</p>
        </div>
        {error && <p className="form-error">{error}</p>}
      </div>
    </Modal>
  )
}

/** 검토 시작(검토대기 → 검토중): 검토자 이름(한 명 이상, 칸을 더하거나 뺄 수 있다)을 받는다 */
function ReviewDialog({ doc, version, onClose }: { doc: Doc; version: DocVersion; onClose: () => void }) {
  const { startReview } = useData()
  const [names, setNames] = useState<string[]>([''])
  const [error, setError] = useState('')
  const [busy, setBusy] = useState(false)
  const revision = revisionFile(version)

  const setName = (i: number, value: string) => {
    setNames((prev) => prev.map((n, k) => (k === i ? value : n)))
    setError('')
  }

  const submit = async (e: FormEvent) => {
    e.preventDefault()
    e.stopPropagation()
    const { error: invalid } = reviewerNames(names)
    if (invalid) return setError(invalid)
    setBusy(true)
    try {
      await startReview(doc.id, names)
      onClose()
    } catch (err) {
      setBusy(false)
      setError(err instanceof Error ? err.message : String(err))
    }
  }

  return (
    <Modal
      title={`${versionLabel(version)} 검토 시작`}
      onClose={onClose}
      footer={
        <>
          <button type="button" className="btn" onClick={onClose}>
            취소
          </button>
          <button type="submit" form="review-form" className="btn primary" disabled={busy}>
            {busy ? '저장 중…' : '검토 시작'}
          </button>
        </>
      }
    >
      <form id="review-form" className="form" onSubmit={submit}>
        <p className="muted small">
          검토 대상은 현재 {revisedFiles(version).length > 0 ? '수정본' : '원본'}({currentFile(version)?.fileName})
          {revision ? `과 수정대조표(${revision.fileName})` : ''}입니다. 검토를 시작하면 검토중이 되고, 이어서 승인하거나 거절합니다.
        </p>
        <div className="field">
          <span>검토자 이름</span>
          <div className="reviewer-list">
            {names.map((n, i) => (
              <div key={i} className="reviewer-row">
                <input
                  aria-label={`검토자 이름 ${i + 1}`}
                  value={n}
                  aria-describedby="reviewer-hint"
                  maxLength={WORKER_NAME_MAX}
                  autoFocus={i === 0}
                  onChange={(e) => setName(i, e.target.value)}
                />
                {i > 0 ? (
                  <button
                    type="button"
                    className="icon-btn"
                    aria-label="검토자 삭제"
                    title="검토자 삭제"
                    onClick={() => setNames((prev) => prev.filter((_, k) => k !== i))}
                  >
                    <IconClose size={14} />
                  </button>
                ) : (
                  // 삭제 버튼이 없는 첫 칸도 같은 폭의 빈 자리를 둬 입력칸 오른쪽 끝을 맞춘다
                  <span className="reviewer-slot" aria-hidden="true" />
                )}
              </div>
            ))}
            <button type="button" className="btn small reviewer-add" onClick={() => setNames((prev) => [...prev, ''])}>
              <IconPlus size={14} />
              검토자 추가
            </button>
            <span className="field-hint" id="reviewer-hint">
              필수 · 한 명당 최대 {WORKER_NAME_MAX}자
            </span>
          </div>
        </div>
        {error && <p className="form-error">{error}</p>}
      </form>
    </Modal>
  )
}

/** 새 버전 시작: 버전 이름과 작업자 이름(필수)만 받는다. 파일은 받지 않고, 수정본과 수정대조표는 그 버전의 수정본 올리기에서 올린다 */
function NewVersionDialog({
  doc,
  onClose,
  onStarted,
}: {
  doc: Doc
  onClose: () => void
  onStarted: (versionId: string) => void
}) {
  const { startVersion } = useData()
  const last = latestVersion(doc)
  const [worker, setWorker] = useState('')
  const [error, setError] = useState('')
  const [busy, setBusy] = useState(false)
  const nextNumber = last.number + 1
  const [versionName, setVersionName] = useState(defaultVersionName(nextNumber))
  const nameHintId = useId()
  const workerHintId = useId()
  // 입력 중인 이름이 잘못(5자 초과·중복)이면 제목·안내에는 기본 이름을 쓰고, 오류는 시작을 누를 때 알린다
  const nameInvalid = versionNameError(doc.versions, { id: null, number: nextNumber }, versionName) !== null
  const nextLabel = (!nameInvalid && storedVersionName(nextNumber, versionName)) || defaultVersionName(nextNumber)

  const start = async () => {
    const invalid = versionNameError(doc.versions, { id: null, number: nextNumber }, versionName) ?? workerNameError(worker)
    if (invalid) return setError(invalid)
    setBusy(true)
    try {
      const updated = await startVersion(doc.id, versionName, worker)
      onStarted(latestVersion(updated).id)
      onClose()
    } catch (err) {
      setBusy(false)
      setError(err instanceof Error ? err.message : String(err))
    }
  }

  return (
    <Modal
      title={`${nextLabel} 시작`}
      onClose={onClose}
      footer={
        <>
          <button type="button" className="btn" onClick={onClose}>
            취소
          </button>
          <button type="button" className="btn primary" onClick={start} disabled={!worker.trim() || busy}>
            {busy ? '시작 중…' : '새 버전 시작'}
          </button>
        </>
      }
    >
      <div className="form">
        <p className="muted small">
          {versionLabel(last)} 제출본이 새 버전({nextLabel})의 원본이 됩니다. 수정본과 수정대조표는 새 버전의 수정본 1 올리기에서 올립니다.
        </p>
        <label className="field">
          <span>버전 이름</span>
          <input
            value={versionName}
            maxLength={VERSION_NAME_MAX}
            placeholder={defaultVersionName(nextNumber)}
            aria-describedby={nameHintId}
            onChange={(e) => {
              setVersionName(e.target.value)
              setError('')
            }}
          />
          <span className="field-hint" id={nameHintId}>
            최대 {VERSION_NAME_MAX}자
          </span>
        </label>
        <label className="field">
          <span>작업자 이름</span>
          <input
            autoFocus
            aria-label="작업자 이름"
            aria-describedby={workerHintId}
            required
            aria-required="true"
            value={worker}
            maxLength={WORKER_NAME_MAX}
            onChange={(e) => {
              setWorker(e.target.value)
              setError('')
            }}
          />
          <span className="field-hint" id={workerHintId}>
            필수 · 최대 {WORKER_NAME_MAX}자 · 수정본 올리기 작업자 칸의 기본값이 됩니다
          </span>
        </label>
        {error && <p className="form-error">{error}</p>}
      </div>
    </Modal>
  )
}
