import { createContext, useContext } from 'react'
import type {
  AssignTarget,
  CancelStep,
  Category,
  CategoryInput,
  CategoryResource,
  Doc,
  DocInput,
  RejectInput,
  ResourceInput,
  Schedule,
  ScheduleGroup,
  ScheduleGroupInput,
  ScheduleInput,
  ScheduleUpdate,
  SendInfo,
} from '../types'

export interface DataContextValue {
  categories: Category[]
  docs: Doc[]
  resources: CategoryResource[]
  loading: boolean
  addCategory: (input: CategoryInput) => Promise<Category>
  editCategory: (id: string, input: CategoryInput) => Promise<void>
  removeCategory: (id: string) => Promise<void>
  /** 사이드바 숨김 여부를 서버에 저장하고 응답으로 갱신한다 */
  setCategoryHidden: (id: string, hidden: boolean) => Promise<void>
  /** 새 문서. file이 없으면 파일 0개인 v1을 만든다. versionName은 v1의 이름(비우면 v1), worker는 올린 사람(필수) */
  addDoc: (input: DocInput, file: File | undefined, versionName: string, worker: string) => Promise<Doc>
  /** 발송 전 버전에 수정본 업로드. 수정대조표가 함께 있어야 하고 작업자 이름(필수)이 올린 사람으로 남는다 */
  uploadWorkingFile: (id: string, file: File, revision: File | undefined, worker: string) => Promise<Doc>
  /** 작업중인 버전의 가장 최신 수정본과 짝 수정대조표를 함께 삭제한다(fileId = 그 수정본). 원본·다른 수정본·작업중이 아닌 상태는 거절한다 */
  deleteLatestRevision: (id: string, fileId: string) => Promise<Doc>
  /** 발송 전 버전을 작업완료로 한다. 수정대조표 조건이 안 맞으면 거절한다 */
  markWorkDone: (id: string) => Promise<Doc>
  /** 검토 시작(검토대기 → 검토중). 검토자 이름이 한 명 이상 필요하다 */
  startReview: (id: string, reviewers: string[]) => Promise<Doc>
  /** 검토 결정(검토중 → 승인이면 발송대기, 거절이면 작업중). 거절은 검토자 한 명과 검토본 파일(.hwpx/.pdf)이 필요하다 */
  decideReview: (id: string, decision: 'approve' | 'reject', reject?: RejectInput) => Promise<Doc>
  /** 발송대기(승인된) 버전만 발송할 수 있다 */
  sendVersion: (id: string, info: SendInfo) => Promise<Doc>
  /** 단계별 취소(최신 버전만). 지금 단계에서 할 수 있는 취소만 받는다 */
  cancelStep: (id: string, step: CancelStep) => Promise<Doc>
  /** 작업중인 버전의 이름 수정. 비우면 v{n}. 발송된 버전은 거절한다 */
  renameVersion: (id: string, versionId: string, name: string) => Promise<Doc>
  /** 발송본을 원본으로 새 버전 시작(파일 없음). versionName은 새 버전의 이름(비우면 v{n}), worker는 작업자(필수) */
  startVersion: (id: string, versionName: string, worker: string) => Promise<Doc>
  /** 미분류 문서를 문서 없는 카테고리(또는 새 카테고리)에 넣는다. 이미 분류가 있거나 문서가 있는 카테고리면 거절한다 */
  assignCategory: (id: string, target: AssignTarget) => Promise<Doc>
  /** 발송 전 버전 삭제. 문서에 버전이 하나뿐이면 문서가 사라지고 null을 돌려준다 */
  removeVersion: (id: string, versionId: string) => Promise<Doc | null>
  addResource: (categoryId: string, input: ResourceInput, file: File) => Promise<CategoryResource>
  editResource: (id: string, input: ResourceInput, file?: File) => Promise<CategoryResource>
  removeResource: (id: string) => Promise<void>
  /** 발송 문서(간트 막대) 목록 */
  schedules: Schedule[]
  /** 일정(기간 묶음) 목록. schedules와 항상 함께 읽는다 */
  scheduleGroups: ScheduleGroup[]
  /** 발송 문서나 일정 목록을 읽지 못했을 때의 문구. 둘 다 성공하면 빈 문자열 */
  scheduleError: string
  /** 발송 문서와 일정 목록을 읽는 중 */
  scheduleLoading: boolean
  /** 발송 문서와 일정 목록을 함께 다시 읽는다. 읽는 중이면 아무것도 하지 않고, 하나라도 실패하면 둘 다 그대로 둔 채 던지지 않고 scheduleError에 남긴다 */
  reloadSchedules: () => Promise<void>
  addSchedule: (input: ScheduleInput) => Promise<Schedule>
  /** 소속 일정·날짜·메모를 바꾼다(전체 교체, 문서는 고정). groupId를 빼면 일정과 관계없는 문서가 된다 */
  editSchedule: (id: string, input: ScheduleUpdate) => Promise<Schedule>
  removeSchedule: (id: string) => Promise<void>
  /** 일정 등록. 배경색은 서버가 정한다 */
  addScheduleGroup: (input: ScheduleGroupInput) => Promise<ScheduleGroup>
  editScheduleGroup: (id: string, input: ScheduleGroupInput) => Promise<ScheduleGroup>
  /** 일정만 지운다. 소속 발송 문서는 일정과 관계없는 문서로 남는다 */
  removeScheduleGroup: (id: string) => Promise<void>
}

export const DataContext = createContext<DataContextValue | null>(null)

export function useData() {
  const ctx = useContext(DataContext)
  if (!ctx) throw new Error('useData는 DataProvider 안에서 사용해야 합니다.')
  return ctx
}
