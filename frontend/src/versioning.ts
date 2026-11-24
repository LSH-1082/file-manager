import { hasExtension } from './utils'
import type { CancelStep, Doc, DocVersion, FileRev, ReviewRound, VersionReview } from './types'

export function latestVersion(doc: Doc): DocVersion {
  return doc.versions[doc.versions.length - 1]
}

/** 현재(마지막) 파일. 원본을 아직 안 올린 v1(파일 0개)이면 undefined */
export function currentFile(version: DocVersion): FileRev | undefined {
  return version.files[version.files.length - 1]
}

/** 수정대조표의 기준(원본) 파일. 모든 버전에서 files[0]이다(v1은 처음 올린 파일, v2부터는 직전 버전 제출본) */
export function baseFile(version: DocVersion): FileRev | undefined {
  return version.files[0]
}

/** 원본에서 파일이 바뀌었는지(수정본이 있는지) */
export function hasChanges(_doc: Doc, version: DocVersion) {
  return version.files.length > 1
}

/** 버전 안에서 원본(대조 기준)이 아닌 파일, 즉 수정본들 */
export function revisedFiles(version: DocVersion): FileRev[] {
  return version.files.slice(1)
}

/** 이 버전에서 올린 파일. v1은 원본도 이 버전에서 올린 것이라 전부, v2부터는 원본(직전 제출본 복사)을 뺀다 */
export function ownFiles(version: DocVersion): FileRev[] {
  return version.number === 1 ? version.files : version.files.slice(1)
}

/** 이 버전에서 마지막으로 파일을 올린 사람, 올린 파일이 없으면 버전을 시작한 사람. 없으면 undefined */
export function versionWorker(version: DocVersion): string | undefined {
  const own = ownFiles(version)
  return own[own.length - 1]?.uploadedBy ?? version.worker
}

/** 버전 작업자: 새 버전 시작에 입력한 작업자, v1은 원본을 올린 사람. 없으면 undefined */
export function versionOwner(version: DocVersion): string | undefined {
  return version.worker ?? version.files[0]?.uploadedBy
}

/** 발송 현황 표의 작업자: 단계와 상관없이 버전 작업자 */
export function workerOf(version: DocVersion): string | undefined {
  return versionOwner(version)
}

/** 검토자들: 결정을 했으면 그 검토자들, 아니면 검토를 시작한 검토자들 */
export function reviewersOf(version: DocVersion): string[] {
  return (version.review ?? version.reviewStart)?.reviewers ?? []
}

/** 검토자 요약: shown명까지 이름을 보이고 나머지는 '외 n명'. 없으면 빈 문자열(전체 이름은 부르는 쪽 title로) */
export function reviewerSummary(names: string[], shown = 2): string {
  if (names.length <= shown) return names.join(', ')
  return `${names.slice(0, shown).join(', ')} 외 ${names.length - shown}명`
}

/** 문서에서 가장 최근에 발송된 버전. 없으면 undefined */
export function lastSentVersion(doc: Doc): DocVersion | undefined {
  return [...doc.versions].reverse().find((v) => v.status === 'sent')
}

/** 버전의 현재 수정대조표 파일 */
export function revisionFile(version: DocVersion): FileRev | undefined {
  return currentFile(version)?.revision
}

/** 현재 수정본에 대조표 파일이 있는지 */
export function hasRevision(version: DocVersion) {
  return !!revisionFile(version)
}

/** 버전의 진행 단계 5개: 작업중 → 검토대기 → 검토중 → 발송대기 → 발송 */
export type Stage = 'working' | 'queued' | 'reviewing' | 'ready' | 'sent'

/** 단계를 부르는 이름. 화면의 모든 상태 표시는 이 이름과 versionStage를 쓴다 */
export const STAGE_LABEL: Record<Stage, string> = {
  working: '작업중',
  queued: '검토대기',
  reviewing: '검토중',
  ready: '발송대기',
  sent: '발송',
}

/** 단계표의 5칸(위 순서 그대로) */
export const STEPS: { key: Stage; label: string }[] = (['working', 'queued', 'reviewing', 'ready', 'sent'] as const).map((key) => ({
  key,
  label: STAGE_LABEL[key],
}))

/** 현재 단계가 5칸 중 몇 번째인지 */
export function stageStep(stage: Stage) {
  return STEPS.findIndex((s) => s.key === stage)
}

/** 결정 기록의 대상이 지금의 현재 수정본·대조표와 같은지 */
function matchesTarget(version: DocVersion, mark: { fileId: string; revisionId?: string }) {
  return mark.fileId === currentFile(version)?.id && mark.revisionId === revisionFile(version)?.id
}

/** 승인 기록. 없으면 undefined */
export function activeReview(version: DocVersion): VersionReview | undefined {
  return version.review?.result === 'accept' ? version.review : undefined
}

/** 거절 기록. 다시 작업완료를 하면(저장할 때 review를 지운다) 사라진다 */
export function activeRejection(version: DocVersion): VersionReview | undefined {
  return version.review?.result === 'reject' ? version.review : undefined
}

/**
 * 버전의 지금 단계. 저장된 기록만으로 정하고 이 순서로 판정한다.
 * 발송 → 발송, 승인 → 발송대기, 거절 → 작업중, 검토 시작 → 검토중, 작업완료 → 검토대기, 그 밖은 작업중.
 * 거절은 단계가 아니다: 작업중으로 돌아가고 거절 기록(activeRejection)이 안내로 남는다.
 */
export function versionStage(version: DocVersion): Stage {
  if (version.status === 'sent') return 'sent'
  if (version.review) return version.review.result === 'accept' ? 'ready' : 'working'
  if (version.reviewStart) return 'reviewing'
  if (version.workDone) return 'queued'
  return 'working'
}

/** 거절 뒤 작업중으로 돌아가 있는 버전인지. 작업완료를 다시 하면 거절 기록이 지워져 false가 된다 */
export function isRejected(version: DocVersion) {
  return version.status === 'working' && version.review?.result === 'reject'
}

/** 서버 409 문구(docs/api 8.11)와 같다 */
export const REJECTED_NEEDS_REVISION = '거절된 뒤 수정본을 올려야 작업완료할 수 있습니다.'

/**
 * 거절됨인데 마지막 거절(review.reviewedAt) 뒤에 올린 수정본이 없다. 서버 DocumentVersion.needsRevisionSinceRejection과 같은 판정이다.
 * 현재 파일이 원본뿐이거나 현재 수정본의 uploadedAt이 거절 시각보다 뒤가 아니면 true(v1 포함). 업로드 시각 기준이라 거절 뒤 올린 수정본을 지우면 다시 true다.
 * 거절 취소로 review가 지워지거나 작업완료로 거절 표시가 사라지면 false다.
 */
export function needsRevisionSinceRejection(version: DocVersion): boolean {
  if (!isRejected(version) || !version.review) return false
  if (version.files.length < 2) return true
  return !(Date.parse(currentFile(version)!.uploadedAt) > Date.parse(version.review.reviewedAt))
}

/** 작업자·검토자 이름의 최대 길이 */
export const WORKER_NAME_MAX = 5

/** 작업자 이름의 오류(공백을 지운 뒤 비었거나 5자를 넘으면). 괜찮으면 null */
export function workerNameError(raw: string): string | null {
  const name = raw.trim()
  if (!name) return '작업자 이름을 적어야 합니다.'
  if (name.length > WORKER_NAME_MAX) return `작업자 이름은 ${WORKER_NAME_MAX}자까지 쓸 수 있습니다.`
  return null
}

/** 검토자 이름들: 공백을 지우고 빈 칸은 뺀다. 하나도 없거나 너무 길면 오류 */
export function reviewerNames(raw: string[]): { names: string[]; error: string | null } {
  const names = raw.map((n) => n.trim()).filter(Boolean)
  if (names.length === 0) return { names, error: '검토자 이름을 한 명 이상 적어야 합니다.' }
  if (names.some((n) => n.length > WORKER_NAME_MAX)) return { names, error: `검토자 이름은 ${WORKER_NAME_MAX}자까지 쓸 수 있습니다.` }
  return { names, error: null }
}

/** 서버 409 문구와 같다. 파일 없이 만든 v1은 원본을 올려야 작업완료할 수 있다 */
export const NO_ORIGINAL = '원본을 올려야 작업완료할 수 있습니다.'

const NO_REVISION = '현재 수정본에 수정대조표가 없습니다. 대조표를 붙여야 합니다.'

/** 작업완료를 할 수 없는 이유. 작업중 단계에서만 할 수 있다. 가능하면 null */
export function workBlocker(doc: Doc, version: DocVersion): string | null {
  if (version.status !== 'working') return '이미 발송된 버전입니다.'
  const stage = versionStage(version)
  if (stage !== 'working') return `이미 ${STAGE_LABEL[stage]} 단계입니다.`
  if (version.files.length === 0) return NO_ORIGINAL
  // v1은 원본만으로 작업완료할 수 있고, v2부터는 수정본이 하나 이상 있어야 한다
  if (ownFiles(version).length === 0) return '수정본을 올려야 작업완료할 수 있습니다.'
  if (needsRevisionSinceRejection(version)) return REJECTED_NEEDS_REVISION
  if (hasChanges(doc, version) && !hasRevision(version)) return NO_REVISION
  return null
}

/** 검토 시작을 할 수 없는 이유. 검토대기 단계에서만 할 수 있다. 가능하면 null */
export function startBlocker(version: DocVersion): string | null {
  if (version.status !== 'working') return '이미 발송된 버전입니다.'
  const stage = versionStage(version)
  if (stage === 'working') return '작업완료를 먼저 해야 검토를 시작할 수 있습니다.'
  if (stage !== 'queued') return `이미 ${STAGE_LABEL[stage]} 단계입니다.`
  return null
}

/** 승인·거절을 할 수 없는 이유. 검토중 단계에서만 할 수 있다. 가능하면 null */
export function decideBlocker(version: DocVersion): string | null {
  if (version.status !== 'working') return '이미 발송된 버전입니다.'
  const stage = versionStage(version)
  if (stage === 'working' || stage === 'queued') return '검토를 시작해야 승인하거나 거절할 수 있습니다.'
  if (stage !== 'reviewing') return `이미 ${STAGE_LABEL[stage]} 단계입니다.`
  return null
}

/** 발송(버전 종료)을 할 수 없는 이유. 발송대기 단계에서만 할 수 있다. 가능하면 null */
export function sendBlocker(doc: Doc, version: DocVersion): string | null {
  if (version.status !== 'working') return '이미 발송된 버전입니다.'
  if (hasChanges(doc, version) && !hasRevision(version)) return NO_REVISION
  const stage = versionStage(version)
  if (stage === 'working') {
    return isRejected(version)
      ? '검토에서 거절되었습니다. 승인을 받아야 발송할 수 있습니다.'
      : '작업완료와 검토 승인을 거쳐야 발송할 수 있습니다.'
  }
  if (stage === 'queued') return '검토를 시작하고 승인을 받아야 발송할 수 있습니다.'
  if (stage === 'reviewing') return '검토중입니다. 승인을 받아야 발송할 수 있습니다.'
  return null
}

/** 수정본·수정대조표를 바꿀 수 없는 이유. 바꿀 수 있으면(발송 전 작업중) null */
export function uploadBlocker(version: DocVersion): string | null {
  if (version.status !== 'working') return '발송된 버전은 수정할 수 없습니다. 새 버전을 시작하세요.'
  const stage = versionStage(version)
  if (stage !== 'working') return `${STAGE_LABEL[stage]} 단계에서는 파일을 바꿀 수 없습니다. 작업완료를 취소해 작업중으로 돌아가야 합니다.`
  return null
}

/** 취소 버튼 이름 */
export const CANCEL_LABEL: Record<CancelStep, string> = {
  workDone: '작업완료 취소',
  reviewStart: '검토 취소',
  approve: '승인 취소',
  reject: '거절 취소',
  send: '발송 취소',
}

/** 취소하면 돌아가는 단계 */
export const CANCEL_TO: Record<CancelStep, Stage> = {
  workDone: 'working',
  reviewStart: 'queued',
  approve: 'reviewing',
  reject: 'reviewing',
  send: 'ready',
}

/** 취소를 문장 안에서 부르는 말(조사까지). 취소 이름이 고정이라 조사를 정해 둔다 */
export const CANCEL_PHRASE: Record<CancelStep, string> = {
  workDone: '작업완료를 취소',
  reviewStart: '검토를 취소',
  approve: '승인을 취소',
  reject: '거절을 취소',
  send: '발송을 취소',
}

/** 단계로 돌아간다고 말할 때의 이름(조사까지). 단계 이름이 고정이라 조사를 정해 둔다 */
export const STAGE_TO: Record<Stage, string> = {
  working: '작업중으로',
  queued: '검토대기로',
  reviewing: '검토중으로',
  ready: '발송대기로',
  sent: '발송으로',
}

/**
 * 지금 화면에 둘 취소 하나. 단계마다 취소는 하나뿐이다.
 * 발송 취소는 최신 버전만, 거절 취소는 거절 뒤 현재 수정본·대조표가 바뀌지 않았을 때만.
 */
export function availableCancel(doc: Doc, version: DocVersion): CancelStep | null {
  if (version.status === 'sent') return latestVersion(doc).id === version.id ? 'send' : null
  const stage = versionStage(version)
  if (stage === 'ready') return 'approve'
  if (stage === 'reviewing') return 'reviewStart'
  if (stage === 'queued') return 'workDone'
  if (version.review?.result === 'reject' && matchesTarget(version, version.review)) return 'reject'
  return null
}

/** 버전 문서(원본·수정본)와 수정대조표의 허용 확장자. 서버 VersionRules와 같은 규칙·문구 */
export const DOC_FILE_EXTENSIONS = ['hwpx', 'pdf'] as const
export const DOC_FILE_ACCEPT = '.hwpx,.pdf'

export function docFileError(file: File): string | null {
  return hasExtension(file.name, DOC_FILE_EXTENSIONS) ? null : '.hwpx 또는 .pdf 파일만 올릴 수 있습니다.'
}

/** 버전 이름의 최대 길이 */
export const VERSION_NAME_MAX = 5

/** 이름이 없을 때 버전을 부르는 이름 */
export function defaultVersionName(number: number) {
  return `v${number}`
}

/** 버전을 부르는 이름: 사용자가 정한 이름, 없으면 v{n} */
export function versionLabel(version: DocVersion) {
  return version.name || defaultVersionName(version.number)
}

/** 저장할 버전 이름: 공백을 지우고, 비었거나 기본 이름(v{n})과 같으면 저장하지 않는다(undefined) */
export function storedVersionName(number: number, raw: string): string | undefined {
  const name = raw.trim()
  return !name || name === defaultVersionName(number) ? undefined : name
}

/**
 * 버전 이름 오류. 없으면 null. versions는 같은 문서의 버전들이고,
 * target은 이름을 정하는 버전(새 버전이면 id를 null로)이다. 같은 문서의 다른 버전 이름과 겹치면 안 된다.
 */
export function versionNameError(
  versions: DocVersion[],
  target: { id: string | null; number: number },
  raw: string,
): string | null {
  const name = raw.trim() || defaultVersionName(target.number)
  if (name.length > VERSION_NAME_MAX) return `버전 이름은 ${VERSION_NAME_MAX}자 이내로 입력하세요.`
  if (versions.some((v) => v.id !== target.id && versionLabel(v) === name)) {
    return '이 문서에 같은 이름의 버전이 있습니다.'
  }
  return null
}

export function formatDay(iso: string) {
  return new Date(iso).toLocaleDateString('ko-KR', { year: 'numeric', month: '2-digit', day: '2-digit' })
}

/** 카테고리 하나에는 문서가 하나다. 그 카테고리의 문서 */
export function categoryDoc(docs: Doc[], categoryId: string | null): Doc | undefined {
  return categoryId ? docs.find((d) => d.categoryId === categoryId) : undefined
}

/**
 * 버전 안 파일의 이름표. 첫 파일(files[0])은 모든 버전에서 원본, 나머지는 올린 순서대로 수정본 n.
 * 발송된 버전의 맨 마지막 수정본(발송 파일)은 수정본 n이 아니라 제출본이고, 발송대기 단계에서는 발송예정이다.
 * index가 파일 수와 같으면 다음에 올릴 수정본의 이름표다(예: 파일 1개면 수정본 1).
 */
export function fileRevLabel(version: DocVersion, index: number) {
  if (index === 0) return '원본'
  if (index === version.files.length - 1) {
    if (version.status === 'sent') return '제출본'
    if (versionStage(version) === 'ready') return '발송예정'
  }
  return `수정본 ${index}`
}

/**
 * 파일 사슬·목록 카드의 파일 순서. 모든 단계에서 같다: 맨 위 최신 수정본, 가운데 그 사이 수정본(최신부터), 맨 아래 원본.
 * 수정본이 없으면 원본 한 줄만 맨 위(top)에 있다. 파일이 0개면 top도 없다.
 */
export function chainFiles(version: DocVersion): { top: FileRev | undefined; middle: FileRev[]; base: FileRev | null } {
  if (version.files.length < 2) return { top: version.files[0], middle: [], base: null }
  return { top: currentFile(version)!, middle: version.files.slice(1, -1).reverse(), base: baseFile(version)! }
}

/** 맨 위 파일의 이름표: 수정본이 있으면 발송 '제출본', 발송대기 '발송예정', 그 밖 '현재 수정본'. 수정본이 없으면 발송 '제출본', 그 밖 '원본' */
export function topFileLabel(version: DocVersion) {
  if (version.status === 'sent') return '제출본'
  if (version.files.length < 2) return '원본'
  return versionStage(version) === 'ready' ? '발송예정' : '현재 수정본'
}

/** 검토 결과의 화면 이름 */
export const REVIEW_RESULT_LABEL: Record<ReviewRound['result'], string> = { accept: '승인', reject: '거절' }

/** 이 파일(fileId)을 대상으로 거절하며 올린 검토본이 있는 회차들(회차 순). 파일 사슬에서 그 파일 줄 아래에 검토본 줄로 보인다 */
export function reviewCopiesOf(version: DocVersion, fileId: string): (ReviewRound & { reviewFile: FileRev })[] {
  return (version.reviews ?? [])
    .filter((r): r is ReviewRound & { reviewFile: FileRev } => r.fileId === fileId && !!r.reviewFile)
    .sort((a, b) => a.round - b.round)
}

/** 마지막 회차(거절 취소·승인 취소가 지우는 회차). 없으면 undefined */
export function lastReviewRound(version: DocVersion): ReviewRound | undefined {
  return (version.reviews ?? []).at(-1)
}
