export interface Category {
  id: string
  name: string
  createdAt: string
  /** 사이드바 '문서' 목록에서 숨김 */
  hidden: boolean
}

/** 업로드된 파일 한 건. 본문은 서버가 id를 키로 보관하고 GET /api/files/{id}로 받는다 */
export interface FileRev {
  id: string
  /** 화면 표시 이름. 버전 파일(원본·수정본·수정대조표)은 {문서 제목} {버전 이름}[ 수정대조표].{확장자}(문서 제목은 카테고리 이름, 미분류면 문서 제목. 확장자는 올린 파일 것). 그 파일을 올린 버전의 제목·이름을 따르며, 같은 버전의 파일끼리 같을 수 있다 */
  fileName: string
  /** 다운로드 이름(서버의 저장 이름). 항상 있다. 버전 파일은 표시 이름의 확장자 앞에 _{yyyyMMdd}_{작업자}를 붙인 것({제목} {버전}[ 수정대조표]_{yyyyMMdd}_{작업자}.{확장자}). 화면에는 fileName을 보인다 */
  downloadName: string
  fileSize: number
  mimeType: string
  uploadedAt: string
  /** 올린 사람(작업자 이름). 새 버전의 원본(직전 제출본 복사)은 처음 올린 사람 그대로다 */
  uploadedBy?: string
  /** 이 수정본의 수정대조표(원본 대비). 수정본은 대조표와 함께 올려야 등록된다. 원본에는 없다 */
  revision?: FileRev
}

export type VersionStatus = 'working' | 'sent'

/** 버전의 작업완료 기록. 시각만 남는다 */
export interface VersionStage {
  completedAt: string
}

/** 검토 시작 기록(검토자가 누른다). 검토자 이름들과 시작 시각 */
export interface VersionReviewStart {
  startedAt: string
  reviewers: string[]
}

/**
 * 버전의 검토 결정 기록(승인 또는 거절). 거절해도 작업완료·검토 시작 기록은 지우지 않는다(지나간 결정의 이력은 DocVersion.reviews다).
 * fileId·revisionId는 '거절 취소'를 할 수 있는 때(거절 뒤 현재 수정본·대조표가 그대로인 동안)를 가르는 데만 쓴다.
 */
export interface VersionReview {
  /** accept = 승인, reject = 거절 */
  result: 'accept' | 'reject'
  reviewers: string[]
  /** 결정한 시각 */
  reviewedAt: string
  /** 결정한 때의 현재 파일(수정본, 없으면 원본)의 id */
  fileId: string
  /** 결정한 때의 수정대조표 파일의 id. 없었으면 없음 */
  revisionId?: string
}

/** 검토 이력의 한 회차(승인 또는 거절 결정 한 건) */
export interface ReviewRound {
  /** 회차 번호. 버전 안에서 1부터, 취소로 지워진 번호는 다음 결정이 다시 쓴다 */
  round: number
  /** 결정 당시 검토자 목록(검토 시작 때 적은 순서) */
  reviewers: string[]
  result: 'accept' | 'reject'
  reviewedAt: string
  /** 결정 당시 현재 수정본(없으면 원본)의 id. 그 파일이 지워져도 남는다(그때는 files에 없다) */
  fileId: string
  revisionId?: string
  /** 거절 검토본. 승인 회차에는 없고, 대상 수정본이 지워지면 빠진다. fileName = '{제목} {버전} 검토본.{ext}', downloadName = 저장 이름, uploadedBy = 검토자, revision 없음 */
  reviewFile?: FileRev
}

/** 거절에 함께 보내는 것: 검토 시작 때 적은 검토자 중 한 명과 검토본 파일 한 개(.hwpx/.pdf) */
export interface RejectInput {
  reviewer: string
  file: File
}

/** 취소할 수 있는 작업 5가지: 작업완료, 검토 시작, 승인, 거절, 발송 */
export type CancelStep = 'workDone' | 'reviewStart' | 'approve' | 'reject' | 'send'

/**
 * 문서의 한 버전. 발송하면 종료(sent)되어 더 이상 고칠 수 없다.
 * 원본(대조 기준): v1은 처음 올린 파일, v2부터는 직전 버전의 제출본. files[0]이 언제나 원본이다.
 */
export interface DocVersion {
  id: string
  number: number
  /** 사용자가 정한 버전 이름. 없으면 v{number}로 부른다 */
  name?: string
  status: VersionStatus
  /** 버전 작업자(새 버전 시작 입력, v1은 원본 올린 사람). 발송 전 확인·발송 기록·발송 현황 표의 작업자이고, 수정본을 올리기 전의 '작업중 · 이름'과 수정본 올리기 작업자 칸의 기본값이 된다 */
  worker?: string
  /** 작업완료 기록(시각만 기록). 없으면 작업중. 검토는 작업완료 뒤에만 할 수 있다. 작업완료 뒤에는 파일을 바꿀 수 없다 */
  workDone?: VersionStage
  /** 검토 시작 기록. 작업완료(검토대기) 뒤 '검토 시작'을 누르면 생기고, 이 동안이 검토중이다 */
  reviewStart?: VersionReviewStart
  /** 검토 결정(승인 또는 거절). 없으면 검토 전. 발송은 승인된 버전만 할 수 있다. 거절하면 작업중으로 돌아가고 작업완료·검토 시작 기록은 남는다 */
  review?: VersionReview
  /** 검토 이력. 승인·거절 결정마다 한 건(round 오름차순), 항상 있는 배열. 작업완료는 지우지 않고 승인 취소·거절 취소는 마지막 한 건을 지운다 */
  reviews: ReviewRound[]
  /** 파일 이력. files[0]이 원본(v1은 처음 올린 파일, v2부터는 직전 제출본을 같은 id로 복사), 마지막이 현재 파일 */
  files: FileRev[]
  createdAt: string
  sentAt?: string
  sentTo?: string
  sentMemo?: string
}

export interface SendInfo {
  sentAt: string
  sentTo: string
  sentMemo: string
}

export interface Doc {
  id: string
  title: string
  /** 미분류면 null */
  categoryId: string | null
  /** 오래된 버전부터. 마지막이 최신 버전 */
  versions: DocVersion[]
  createdAt: string
  updatedAt: string
}

/** 카테고리(문서 분류)에 딸린 자료의 종류 */
export type ResourceKind = 'template' | 'reference' | 'etc'

export const RESOURCE_KINDS: { kind: ResourceKind; label: string; hint: string }[] = [
  { kind: 'template', label: '양식', hint: '이 분류의 문서를 작성할 때 쓰는 빈 양식' },
  { kind: 'reference', label: '참고문서', hint: '작성 예시, 지침, 관련 규정 등' },
  { kind: 'etc', label: '기타', hint: '그 밖의 자료' },
]

/** 카테고리(문서 분류)에 저장하는 양식·참고문서 파일 */
export interface CategoryResource {
  id: string
  categoryId: string
  kind: ResourceKind
  title: string
  note: string
  fileName: string
  fileSize: number
  mimeType: string
  createdAt: string
  updatedAt: string
}

export type ResourceInput = Pick<CategoryResource, 'kind' | 'title' | 'note'>

export type CategoryInput = Pick<Category, 'name'>
export type DocInput = Pick<Doc, 'title' | 'categoryId'>

/** 사이드바 선택 상태: 전체 / 일정 / 미분류 / 특정 카테고리 */
export type CategoryFilter =
  | { kind: 'all' }
  | { kind: 'schedule' }
  | { kind: 'uncategorized' }
  | { kind: 'category'; id: string }

/** 분류 지정 대상: 기존 카테고리 id 또는 새로 만들 카테고리 */
export type AssignTarget = string | CategoryInput

/** 발송 문서 한 건, 곧 간트 막대 하나(docs/api/README.md 5.1). 일정(기간 묶음)은 ScheduleGroup이다. startDate·plannedSendDate는 날짜 전용 YYYY-MM-DD */
export interface Schedule {
  id: string
  documentId: string
  /** 소속 일정의 ScheduleGroup.id. 없으면 일정과 관계없는 문서. 수정(PUT)으로 바꿀 수 있고 일정이 지워지면 없어진다 */
  groupId?: string
  documentTitle: string
  /** 미분류면 null */
  categoryId: string | null
  /** 서버가 계산한 문서의 현재 단계. versioning.ts의 Stage와 같은 값(순환 import를 피해 리터럴로 적는다) */
  stage: 'working' | 'queued' | 'reviewing' | 'ready' | 'sent'
  startDate: string
  plannedSendDate: string
  memo?: string
  createdAt: string
  updatedAt: string
}

/** 발송 문서 등록 본문(5.2). groupId를 빼면 일정과 관계없는 문서가 된다 */
export interface ScheduleInput {
  documentId: string
  groupId?: string
  startDate: string
  plannedSendDate: string
  memo?: string
}

/** 발송 문서 수정 본문(5.2, 전체 교체). 문서(D-28)는 바꿀 수 없고, groupId를 빼면 일정과 관계없는 문서가 된다 */
export type ScheduleUpdate = Omit<ScheduleInput, 'documentId'>

/** 일정 한 건, 곧 발송 문서(Schedule)를 묶는 기간(docs/api/README.md 5.1). startDate·endDate는 날짜 전용 YYYY-MM-DD */
export interface ScheduleGroup {
  id: string
  name: string
  startDate: string
  endDate: string
  /** 배경색 번호 0~5. 등록할 때 서버가 정하고 바뀌지 않는다(D-32) */
  colorNo: number
  createdAt: string
  updatedAt: string
}

/** 일정 등록·수정 본문(5.2) */
export interface ScheduleGroupInput {
  name: string
  startDate: string
  endDate: string
}
