/**
 * 발송 예정일(제출 기한)이 다가오는 정도. 사이드바 정렬·문서 제목 색·간트 '임박/기한 넘김' 표시가 함께 쓴다.
 * 이미 발송(sent) 단계인 문서는 기한이 없다.
 */
import { dayIndex, todayIso } from './components/ganttScale'
import type { Doc, Schedule } from './types'
import { latestVersion, versionStage } from './versioning'

/** 제목이 붉어지기 시작하는 남은 날 수. ponytail: 고정값, 사용자가 바꾸고 싶어 하면 설정으로 옮긴다 */
export const WARN_DAYS = 7
/** 간트에서 '임박'으로 표시하는 남은 날 수(이하) */
export const IMMINENT_DAYS = 3

/** 문서의 가장 빠른 발송 예정일. 발송한 문서이거나 발송 문서가 없으면 undefined */
export function nextDue(doc: Doc, schedules: Schedule[]): string | undefined {
  if (versionStage(latestVersion(doc)) === 'sent') return undefined
  let due: string | undefined
  for (const s of schedules) if (s.documentId === doc.id && (due === undefined || s.plannedSendDate < due)) due = s.plannedSendDate
  return due
}

/** 오늘부터 date까지 남은 날 수. 지났으면 음수 */
export function daysLeft(date: string, today = todayIso()): number {
  return dayIndex(date) - dayIndex(today)
}

/**
 * 문서 제목 글자색. 여유(WARN_DAYS 초과)면 undefined(원래 색).
 * 4~7일: 점점 노랑(--due-warn)에 가까워짐, 2~3일(IMMINENT_DAYS 이하): 노랑, 1일 이하·지남: 빨강(--negative)
 */
export function dueTitleColor(due: string | undefined, today = todayIso()): string | undefined {
  if (due === undefined) return undefined
  const left = daysLeft(due, today)
  if (left <= 1) return 'var(--negative)'
  if (left <= IMMINENT_DAYS) return 'var(--due-warn)'
  if (left > WARN_DAYS) return undefined
  const pct = Math.round(((WARN_DAYS - left + 1) / (WARN_DAYS - IMMINENT_DAYS + 1)) * 100)
  return `color-mix(in srgb, var(--due-warn) ${pct}%, currentColor)`
}

/** 간트 막대를 '기간 밖'처럼 표시할 임박·기한 넘김 문구. 발송한 문서이거나 여유가 있으면 빈 문자열 */
export function dueWarnText(schedule: Schedule, doc: Doc, today = todayIso()): string {
  if (versionStage(latestVersion(doc)) === 'sent') return ''
  const left = daysLeft(schedule.plannedSendDate, today)
  if (left < 0) return '기한 넘김'
  if (left <= IMMINENT_DAYS) return left === 0 ? '오늘 기한' : `기한 D-${left}`
  return ''
}
