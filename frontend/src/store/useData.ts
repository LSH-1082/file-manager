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
  /** 새 문서. versionName은 v1의 이름(비우면 v1), worker는 올린 사람(필수) */
  addDoc: (input: DocInput, file: File, versionName: string, worker: string) => Promise<Doc>
  /** 발송 전 버전에 수정본 업로드. 수정대조표가 함께 있어야 하고 작업자 이름(필수)이 올린 사람으로 남는다 */
  uploadWorkingFile: (id: string, file: File, revision: File, worker: string) => Promise<Doc>
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
}

export const DataContext = createContext<DataContextValue | null>(null)

export function useData() {
  const ctx = useContext(DataContext)
  if (!ctx) throw new Error('useData는 DataProvider 안에서 사용해야 합니다.')
  return ctx
}
