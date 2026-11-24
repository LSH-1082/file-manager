/**
 * 백엔드 REST API(docs/api/README.md)를 부르는 저장 계층.
 * 기본 주소는 /api(VITE_API_BASE로 바꾼다). 도메인 규칙 검사는 서버가 정본이고, 서버 오류는 응답의 message로 Error를 던진다.
 */
import type {
  AssignTarget,
  Category,
  CategoryInput,
  CategoryResource,
  Doc,
  DocInput,
  RejectInput,
  ResourceInput,
  SendInfo,
  CancelStep,
  Schedule,
  ScheduleGroup,
  ScheduleGroupInput,
  ScheduleInput,
  ScheduleUpdate,
} from '../types'

const BASE: string = import.meta.env.VITE_API_BASE ?? '/api'

async function raw(method: string, path: string, body?: object | FormData): Promise<Response> {
  const init: RequestInit = { method }
  if (body instanceof FormData) init.body = body
  else if (body) {
    init.body = JSON.stringify(body)
    init.headers = { 'Content-Type': 'application/json' }
  }
  try {
    return await fetch(BASE + path, init)
  } catch {
    throw new Error('서버에 연결할 수 없습니다.')
  }
}

/** 성공 응답만 돌려주고, 실패는 본문의 message로 Error를 던진다 */
async function call(method: string, path: string, body?: object | FormData): Promise<Response> {
  return ok(await raw(method, path, body))
}

async function ok(res: Response): Promise<Response> {
  if (res.ok) return res
  const err = await res.json().catch(() => null)
  throw new Error(typeof err?.message === 'string' ? err.message : '서버 오류가 발생했습니다.')
}

async function json<T>(method: string, path: string, body?: object | FormData): Promise<T> {
  return (await call(method, path, body)).json()
}

const enc = encodeURIComponent

function form(fields: Record<string, string | File | undefined>): FormData {
  const fd = new FormData()
  for (const [k, v] of Object.entries(fields)) if (v !== undefined) fd.append(k, v)
  return fd
}

// ---------- 카테고리 ----------

export async function listCategories(): Promise<Category[]> {
  return json('GET', '/categories')
}

export async function createCategory(input: CategoryInput): Promise<Category> {
  return json('POST', '/categories', input)
}

// 문서 제목은 카테고리 이름이므로 서버가 이름이 바뀌면 그 카테고리 문서의 제목도 함께 바꾼다
export async function updateCategory(id: string, input: CategoryInput): Promise<Category> {
  return json('PUT', `/categories/${enc(id)}`, input)
}

/** 사이드바 숨김 여부 저장 */
export async function setCategoryHidden(id: string, hidden: boolean): Promise<Category> {
  return json('PUT', `/categories/${enc(id)}/hidden`, { hidden })
}

/** 카테고리 삭제. 소속 문서는 미분류가 되고 양식·참고자료는 함께 지워진다 */
export async function deleteCategory(id: string): Promise<void> {
  await call('DELETE', `/categories/${enc(id)}`)
}

/** 미분류 문서를 카테고리에 넣는다(분류 지정). 대상은 기존 카테고리 id이거나 새로 만들 카테고리({ name }) */
export async function assignCategory(docId: string, target: AssignTarget): Promise<Doc> {
  return json('POST', `/documents/${enc(docId)}/category`, typeof target === 'string' ? { categoryId: target } : { newCategory: target })
}

// ---------- 파일 본문 ----------

/** 파일 본문. 없으면 undefined */
export async function getFile(id: string): Promise<Blob | undefined> {
  const res = await raw('GET', `/files/${enc(id)}`)
  if (res.status === 404) return undefined
  return (await ok(res)).blob()
}

// ---------- 문서 ----------

export async function listDocs(): Promise<Doc[]> {
  return json('GET', '/documents')
}

/** 새 문서. 올린 파일이 v1의 원본이 된다. 제목은 서버가 카테고리 이름으로 정한다 */
/** 새 문서. file이 없으면 파일 0개인 v1을 만든다(원본은 uploadWorkingFile로 나중에 올린다) */
export async function createDoc(input: DocInput, file: File | undefined, versionName = '', worker = ''): Promise<Doc> {
  return json('POST', '/documents', form({ file, categoryId: input.categoryId ?? '', versionName, worker }))
}

/** 작업중인 버전에 수정본과 그 수정대조표를 쌍으로 추가. 버전에 파일이 0개면 revision 없이 file이 원본이 된다 */
export async function uploadWorkingFile(id: string, file: File, revision: File | undefined, worker: string): Promise<Doc> {
  return json('POST', `/documents/${enc(id)}/revisions`, form({ file, revision, worker }))
}

/** 작업중인 버전의 가장 최신 수정본과 그 짝 수정대조표를 함께 지운다. fileId는 지우려는 수정본의 id */
export async function deleteLatestRevision(id: string, fileId: string): Promise<Doc> {
  return json('DELETE', `/documents/${enc(id)}/revisions/${enc(fileId)}`)
}

/** 작업완료(작업중 → 검토대기). 작업자는 받지 않는다 */
export async function markWorkDone(id: string): Promise<Doc> {
  return json('POST', `/documents/${enc(id)}/work-done`, {})
}

/** 검토 시작(검토대기 → 검토중) */
export async function startReview(id: string, reviewers: string[]): Promise<Doc> {
  return json('POST', `/documents/${enc(id)}/review-start`, { reviewers })
}

/** 검토 결정(검토중 → 발송대기 또는 작업중). 승인은 JSON, 거절은 multipart(decision=reject, reviewer, 검토본 file) */
export async function decideReview(id: string, decision: 'approve' | 'reject', reject?: RejectInput): Promise<Doc> {
  if (decision === 'approve') return json('POST', `/documents/${enc(id)}/review-decision`, { decision })
  return json('POST', `/documents/${enc(id)}/review-decision`, form({ decision: 'reject', reviewer: reject?.reviewer, file: reject?.file }))
}

/** 발송처 최대 글자 수(공백 제거 뒤) */
export const SENT_TO_MAX = 20

/** 발송(발송대기 → 발송) */
export async function sendVersion(id: string, info: SendInfo): Promise<Doc> {
  return json('POST', `/documents/${enc(id)}/send`, info)
}

/** 단계별 취소(최신 버전만) */
export async function cancelStep(id: string, step: CancelStep): Promise<Doc> {
  return json('POST', `/documents/${enc(id)}/cancel`, { step })
}

/** 작업중인 버전의 이름 수정. 비우면 v{n}으로 돌아간다 */
export async function renameVersion(id: string, versionId: string, name: string): Promise<Doc> {
  return json('PATCH', `/documents/${enc(id)}/versions/${enc(versionId)}`, { name })
}

/** 발송된 최신 버전을 원본으로 새 작업 버전 시작. 파일은 받지 않는다 */
export async function startVersion(id: string, versionName: string, worker: string): Promise<Doc> {
  return json('POST', `/documents/${enc(id)}/versions`, { versionName, worker })
}

/** 발송 전 버전 삭제. 유일한 v1을 지우면 문서가 지워지고 null(204)을 돌려준다 */
export async function deleteVersion(id: string, versionId: string): Promise<Doc | null> {
  const res = await call('DELETE', `/documents/${enc(id)}/versions/${enc(versionId)}`)
  return res.status === 204 ? null : res.json()
}

// ---------- 카테고리 자료 (양식·참고문서) ----------

export async function listResources(): Promise<CategoryResource[]> {
  return json('GET', '/resources')
}

export async function createResource(categoryId: string, input: ResourceInput, file: File): Promise<CategoryResource> {
  return json('POST', `/categories/${enc(categoryId)}/resources`, form({ ...input, file }))
}

/** 자료 정보 수정. file을 주면 파일도 교체한다 */
export async function updateResource(id: string, input: ResourceInput, file?: File): Promise<CategoryResource> {
  return json('PUT', `/resources/${enc(id)}`, form({ ...input, file }))
}

export async function deleteResource(id: string): Promise<void> {
  await call('DELETE', `/resources/${enc(id)}`)
}

// ---------- 발송 문서(간트 막대) ----------

export async function listSchedules(): Promise<Schedule[]> {
  return json('GET', '/schedules')
}

export async function createSchedule(input: ScheduleInput): Promise<Schedule> {
  return json('POST', '/schedules', input)
}

/** 날짜·메모만 바꾼다(문서·소속 일정은 고정). memo를 빼면 메모가 지워진다(전체 교체) */
export async function updateSchedule(id: string, input: ScheduleUpdate): Promise<Schedule> {
  return json('PUT', `/schedules/${enc(id)}`, input)
}

export async function deleteSchedule(id: string): Promise<void> {
  await call('DELETE', `/schedules/${enc(id)}`)
}

// ---------- 일정(기간 묶음) ----------

export async function listScheduleGroups(): Promise<ScheduleGroup[]> {
  return json('GET', '/schedule-groups')
}

/** 배경색 번호(colorNo)는 서버가 정한다 */
export async function createScheduleGroup(input: ScheduleGroupInput): Promise<ScheduleGroup> {
  return json('POST', '/schedule-groups', input)
}

/** 이름·기간을 전체 교체한다. 배경색과 소속 발송 문서는 그대로다 */
export async function updateScheduleGroup(id: string, input: ScheduleGroupInput): Promise<ScheduleGroup> {
  return json('PUT', `/schedule-groups/${enc(id)}`, input)
}

/** 일정만 지운다. 소속 발송 문서는 일정과 관계없는 문서로 남는다 */
export async function deleteScheduleGroup(id: string): Promise<void> {
  await call('DELETE', `/schedule-groups/${enc(id)}`)
}
