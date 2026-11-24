/**
 * 간트 차트의 날짜·축 계산(React 없음). 날짜는 'YYYY-MM-DD'를 UTC 기준 일 번호로 바꿔 다루므로
 * 시간대·서머타임의 영향을 받지 않는다. 일정 화면(GanttView와 그 하위, 일정·발송 문서 창)만 쓴다.
 */

export type GanttUnit = 'day' | 'week' | 'month'

/** 하루가 차지하는 폭(px) */
export const DAY_PX: Record<GanttUnit, number> = { day: 40, week: 16, month: 4 }

/** 차트의 가로 자: 단위, 왼쪽 끝 일 번호, 오늘 선의 x(px) */
export interface GanttScale {
  unit: GanttUnit
  start: number
  todayX: number
}

const MS_PER_DAY = 86_400_000

/** 범위 앞 여백(일) */
const PADDING_BEFORE: Record<GanttUnit, number> = { day: 7, week: 28, month: 62 }
/** 범위 뒤 여백(일). 마지막 날 막대의 바깥 글자(20자 메모 + '기간 밖' = 340px)와 일정 칩의 바깥 글자·버튼(이름 20자 + 기간 = 400px)이 들어가는 폭 */
const PADDING_AFTER: Record<GanttUnit, number> = { day: 10, week: 28, month: 100 }

export interface Tick {
  key: string
  label: string
  /** px */
  left: number
  /** px */
  width: number
  weekend?: boolean
}

/** 'YYYY-MM-DD' → 1970-01-01부터의 일 번호 */
export function dayIndex(iso: string): number {
  const [y, m, d] = iso.split('-').map(Number)
  return Math.round(Date.UTC(y, m - 1, d) / MS_PER_DAY)
}

function utcDate(idx: number): Date {
  return new Date(idx * MS_PER_DAY)
}

const pad2 = (n: number) => String(n).padStart(2, '0')

/** 일 번호 → 'YYYY-MM-DD' */
export function isoOfDay(idx: number): string {
  const d = utcDate(idx)
  return `${d.getUTCFullYear()}-${pad2(d.getUTCMonth() + 1)}-${pad2(d.getUTCDate())}`
}

/** 오늘(로컬 날짜)의 'YYYY-MM-DD' */
export function todayIso(): string {
  const d = new Date()
  return `${d.getFullYear()}-${pad2(d.getMonth() + 1)}-${pad2(d.getDate())}`
}

/** 'YYYY-MM-DD' → 'M/D' */
export function formatMD(iso: string): string {
  const [, m, d] = iso.split('-').map(Number)
  return `${m}/${d}`
}

/** 'M/D~M/D' */
export function formatPeriod(start: string, end: string): string {
  return `${formatMD(start)}~${formatMD(end)}`
}

/** 월요일 0 … 일요일 6 */
function weekdayFromMonday(idx: number): number {
  return (utcDate(idx).getUTCDay() + 6) % 7
}

function monthStart(idx: number): number {
  const d = utcDate(idx)
  return Math.round(Date.UTC(d.getUTCFullYear(), d.getUTCMonth(), 1) / MS_PER_DAY)
}

function monthEnd(idx: number): number {
  const d = utcDate(idx)
  return Math.round(Date.UTC(d.getUTCFullYear(), d.getUTCMonth() + 1, 0) / MS_PER_DAY)
}

function alignStart(idx: number, unit: GanttUnit): number {
  if (unit === 'week') return idx - weekdayFromMonday(idx)
  if (unit === 'month') return monthStart(idx)
  return idx
}

function alignEnd(idx: number, unit: GanttUnit): number {
  if (unit === 'week') return idx + (6 - weekdayFromMonday(idx))
  if (unit === 'month') return monthEnd(idx)
  return idx
}

/**
 * 보일 날짜 범위(일 번호, 양 끝 포함). 날짜들과 오늘 가운데 가장 이른 날·늦은 날에 여백을 두고
 * 단위 경계(주는 월~일, 월은 1일~말일)에 맞춘다. 폭이 minWidthPx보다 좁으면 뒤로 늘린다.
 */
export function timelineRange(dates: string[], today: string, unit: GanttUnit, minWidthPx: number): { start: number; end: number } {
  const all = [...dates, today].map(dayIndex)
  const lo = Math.min(...all)
  const hi = Math.max(...all)
  const start = alignStart(lo - PADDING_BEFORE[unit], unit)
  let end = alignEnd(hi + PADDING_AFTER[unit], unit)
  const px = DAY_PX[unit]
  if ((end - start + 1) * px < minWidthPx) {
    end = alignEnd(start + Math.ceil(minWidthPx / px) - 1, unit)
  }
  return { start, end }
}

/** 날마다 키·이름을 매겨 같은 키가 이어지는 구간을 tick 하나로 묶는다 */
function group(
  start: number,
  end: number,
  px: number,
  keyOf: (idx: number) => string,
  labelOf: (idx: number) => string,
  weekendOf?: (idx: number) => boolean,
): Tick[] {
  const ticks: Tick[] = []
  for (let idx = start; idx <= end; idx++) {
    const key = keyOf(idx)
    const last = ticks[ticks.length - 1]
    if (last && last.key === key) {
      last.width += px
      continue
    }
    const tick: Tick = { key, label: labelOf(idx), left: (idx - start) * px, width: px }
    if (weekendOf?.(idx)) tick.weekend = true
    ticks.push(tick)
  }
  return ticks
}

const yearOf = (idx: number) => utcDate(idx).getUTCFullYear()
const monthOf = (idx: number) => utcDate(idx).getUTCMonth() + 1
const monthKey = (idx: number) => `${yearOf(idx)}-${monthOf(idx)}`
const monthLabel = (idx: number) => `${yearOf(idx)}년 ${monthOf(idx)}월`

/** 두 줄 축 눈금(left·width는 px). 범위 끝에서 잘리는 눈금은 범위 안 부분만 폭으로 잡는다 */
export function axisTicks(unit: GanttUnit, start: number, end: number): { top: Tick[]; bottom: Tick[] } {
  const px = DAY_PX[unit]
  if (unit === 'day') {
    return {
      top: group(start, end, px, monthKey, monthLabel),
      bottom: group(
        start,
        end,
        px,
        (idx) => String(idx),
        (idx) => String(utcDate(idx).getUTCDate()),
        (idx) => weekdayFromMonday(idx) >= 5,
      ),
    }
  }
  if (unit === 'week') {
    const monday = (idx: number) => idx - weekdayFromMonday(idx)
    return {
      top: group(start, end, px, monthKey, monthLabel),
      bottom: group(
        start,
        end,
        px,
        (idx) => `w${monday(idx)}`,
        (idx) => formatMD(isoOfDay(monday(idx))),
      ),
    }
  }
  return {
    top: group(start, end, px, (idx) => String(yearOf(idx)), (idx) => `${yearOf(idx)}년`),
    bottom: group(start, end, px, monthKey, (idx) => `${monthOf(idx)}월`),
  }
}

/** 겹치지 않게 줄(lane)을 나눈다. left 순으로 놓고, 앞 항목 오른쪽 끝 + 4px ≤ left인 가장 낮은 줄에 넣는다 */
export function packLanes<T>(items: { item: T; left: number; right: number }[]): { item: T; lane: number }[] {
  const laneEnds: number[] = []
  return [...items]
    .sort((a, b) => a.left - b.left)
    .map(({ item, left, right }) => {
      let lane = laneEnds.findIndex((laneEnd) => laneEnd + 4 <= left)
      if (lane === -1) {
        lane = laneEnds.length
        laneEnds.push(right)
      } else {
        laneEnds[lane] = right
      }
      return { item, lane }
    })
}
