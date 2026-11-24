/**
 * 일정(ScheduleGroup)과 발송 문서(Schedule)의 화면 계산(React 없음): 문서 행 묶기, 일정 순서, 소속, 기간 밖 판정, 색.
 * 날짜는 'YYYY-MM-DD'라 문자열 비교로 앞뒤를 가른다.
 */
import type { Category, Doc, Schedule, ScheduleGroup } from '../types'
import { docTitle } from '../utils'
import { latestVersion, versionStage } from '../versioning'
import type { Stage } from '../versioning'
import { formatMD, formatPeriod } from './ganttScale'
import type { GanttScale } from './ganttScale'

/** 간트의 문서 행 하나 */
export interface Row {
  doc: Doc
  title: string
  stage: Stage
  items: Schedule[]
}

/** 일정 레이아웃이 받는 값 */
export interface GanttLayoutProps {
  /** 보이는 순서(sortGroups)로 정렬된 일정 */
  groups: ScheduleGroup[]
  scale: GanttScale
  onEditGroup: (group: ScheduleGroup) => void
  /** 문서 추가. groupId가 없으면 일정과 관계없는 문서 */
  onAdd: (groupId?: string) => void
  onEdit: (schedule: Schedule) => void
}

function compare(a: string, b: string): number {
  return a < b ? -1 : a > b ? 1 : 0
}

/**
 * 발송 문서를 문서별 행으로 묶는다. 가장 빠른 발송 예정일순, 같으면 문서 이름순이다.
 * 문서 이름·단계는 현재 문서 상태로 계산해 새로고침 없이 따라 바뀌고, 지워진 문서의 발송 문서는 뺀다
 */
export function docRows(schedules: Schedule[], docs: Doc[], categories: Category[]): Row[] {
  const byDoc = new Map<string, Schedule[]>()
  for (const s of schedules) {
    const list = byDoc.get(s.documentId)
    if (list) list.push(s)
    else byDoc.set(s.documentId, [s])
  }
  const rows: Row[] = []
  for (const [documentId, items] of byDoc) {
    const doc = docs.find((d) => d.id === documentId)
    if (!doc) continue
    rows.push({ doc, title: docTitle(categories, doc), stage: versionStage(latestVersion(doc)), items })
  }
  const earliest = (row: Row) => row.items.reduce((min, s) => (s.plannedSendDate < min ? s.plannedSendDate : min), row.items[0].plannedSendDate)
  return rows.sort((a, b) => compare(earliest(a), earliest(b)) || a.title.localeCompare(b.title, 'ko'))
}

/** 일정 순서: 시작일, 종료일, 등록 시각(서버 목록 정렬과 같다) */
export function sortGroups(groups: ScheduleGroup[]): ScheduleGroup[] {
  return [...groups].sort(
    (a, b) => compare(a.startDate, b.startDate) || compare(a.endDate, b.endDate) || compare(a.createdAt, b.createdAt) || compare(a.id, b.id),
  )
}

/** 소속 일정. 소속이 없거나 목록에 없는 일정이면 undefined이고 '일정과 관계없는 문서'로 본다 */
export function groupOf(schedule: Schedule, groups: ScheduleGroup[]): ScheduleGroup | undefined {
  return schedule.groupId === undefined ? undefined : groups.find((g) => g.id === schedule.groupId)
}

type SchedulePeriod = Pick<Schedule, 'startDate' | 'plannedSendDate'>
type GroupPeriod = Pick<ScheduleGroup, 'startDate' | 'endDate'>

function isOutside(date: string, group: GroupPeriod): boolean {
  return date < group.startDate || date > group.endDate
}

/** 시작일이나 발송 예정일이 일정 기간을 벗어났는가(D-34) */
export function isOutOfPeriod(schedule: SchedulePeriod, group: GroupPeriod): boolean {
  return isOutside(schedule.startDate, group) || isOutside(schedule.plannedSendDate, group)
}

/** 벗어난 날짜를 적은 문구. 벗어나지 않았으면 빈 문자열 */
export function outOfPeriodText(schedule: SchedulePeriod, group: GroupPeriod & Pick<ScheduleGroup, 'name'>): string {
  const parts: string[] = []
  if (isOutside(schedule.startDate, group)) parts.push(`시작일(${formatMD(schedule.startDate)})`)
  if (isOutside(schedule.plannedSendDate, group)) parts.push(`발송 예정일(${formatMD(schedule.plannedSendDate)})`)
  if (parts.length === 0) return ''
  return `${parts.join('과 ')}이 '${group.name}' 일정 기간(${formatPeriod(group.startDate, group.endDate)}) 밖입니다.`
}

const GROUP_COLORS = 6

/** 배경색 번호의 CSS 클래스(GanttView.css의 group-color-0~5). 번호가 벗어나면 나머지로 돌린다 */
export function groupColorClass(colorNo: number): string {
  return `group-color-${((colorNo % GROUP_COLORS) + GROUP_COLORS) % GROUP_COLORS}`
}
