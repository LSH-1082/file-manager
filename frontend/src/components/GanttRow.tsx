import { Fragment } from 'react'
import { dueTitleColor, dueWarnText, nextDue } from '../deadline'
import { useData } from '../store/useData'
import type { Schedule, ScheduleGroup } from '../types'
import { STAGE_LABEL } from '../versioning'
import { DAY_PX, dayIndex, formatPeriod, packLanes } from './ganttScale'
import type { GanttScale } from './ganttScale'
import { groupOf, outOfPeriodText } from './scheduleModel'
import type { Row } from './scheduleModel'

/** 막대 한 줄 높이와 행 위아래 여백(px) */
const LANE_H = 28
const ROW_PAD = 6
/** 막대 오른쪽 태그 폭(px): '기간 밖', 임박·기한 넘김 문구 */
const OUT_TAG_W = 60
const DUE_TAG_W = 72

interface Props {
  row: Row
  scale: GanttScale
  groups: ScheduleGroup[]
  onEdit: (schedule: Schedule) => void
  /** 막대에 더할 클래스. group은 소속 일정(없으면 undefined) */
  barClass?: (schedule: Schedule, group: ScheduleGroup | undefined) => string
  /** 주면 막대를 끌 수 있다(HTML5 drag&drop, dataTransfer에 발송 문서 id) */
  onDragStart?: (schedule: Schedule) => void
  onDragEnd?: () => void
}

/** 간트의 문서 행 하나: 문서 이름 칸과 막대·메모·'기간 밖'·임박/기한 넘김 표시 */
export default function GanttRow({ row, scale, groups, onEdit, barClass, onDragStart, onDragEnd }: Props) {
  const { schedules } = useData()
  const px = DAY_PX[scale.unit]
  const placed = row.items.map((schedule) => {
    const startIdx = dayIndex(schedule.startDate)
    const left = (startIdx - scale.start) * px
    const width = Math.max((dayIndex(schedule.plannedSendDate) - startIdx + 1) * px, 8)
    const memo = schedule.memo
    // 메모 폭은 글자 수로 어림하고, 막대에 들어가지 않으면 막대 오른쪽에 둔다(D-29)
    const textW = memo ? memo.length * 13 + 16 : 0
    const inside = !!memo && textW <= width
    const group = groupOf(schedule, groups)
    const outText = group ? outOfPeriodText(schedule, group) : ''
    const dueText = dueWarnText(schedule, row.doc)
    // 태그는 하나만: 기한 문구가 있으면 그것, 없고 기간 밖이면 '기간 밖'. 사유 전체는 title·aria-label에 둔다
    const tag = dueText || (outText ? '기간 밖' : '')
    const besideW = (memo && !inside ? textW : 0) + (dueText ? DUE_TAG_W : outText ? OUT_TAG_W : 0)
    return {
      item: { schedule, left, width, inside, group, outText, dueText, tag },
      left,
      right: left + width + (besideW > 0 ? 4 + besideW : 0),
    }
  })
  const laned = packLanes(placed)
  const laneCount = laned.reduce((max, l) => Math.max(max, l.lane + 1), 1)

  return (
    <div className="gantt-row" style={{ height: laneCount * LANE_H + ROW_PAD * 2 }}>
      <div className="gantt-name" title={row.title}>
        <span className={`dot stage-dot ${row.stage}`} role="img" aria-label={STAGE_LABEL[row.stage]} />
        <span className="gantt-name-text" style={{ color: dueTitleColor(nextDue(row.doc, schedules)) }}>{row.title}</span>
      </div>
      <div className="gantt-track">
        {laned.map(({ item: { schedule, left, width, inside, group, outText, dueText, tag }, lane }) => {
          const top = ROW_PAD + lane * LANE_H
          const memo = schedule.memo
          const where = group ? `일정 ${group.name}` : '일정과 관계없음'
          return (
            <Fragment key={schedule.id}>
              <button
                type="button"
                className={`gantt-bar ${row.stage}${outText || dueText ? ' out' : ''}${barClass ? ` ${barClass(schedule, group)}` : ''}`}
                style={{ left, width, top }}
                title={[memo, formatPeriod(schedule.startDate, schedule.plannedSendDate), where, outText, dueText].filter(Boolean).join(' · ')}
                aria-label={`${row.title} 발송 문서 ${memo ? `${memo} ` : ''}${schedule.startDate}부터 ${schedule.plannedSendDate}까지, ${where}${outText ? ', 기간 밖' : ''}${dueText ? `, ${dueText}` : ''}, 수정`}
                onClick={() => onEdit(schedule)}
                draggable={!!onDragStart}
                onDragStart={(e) => {
                  e.dataTransfer.setData('text/plain', schedule.id)
                  e.dataTransfer.effectAllowed = 'move'
                  onDragStart?.(schedule)
                }}
                onDragEnd={onDragEnd}
              >
                {inside && <span className="gantt-bar-label">{memo}</span>}
              </button>
              {((memo && !inside) || tag) && (
                <span className="gantt-bar-label outside" style={{ left: left + width + 4, top }} aria-hidden="true">
                  {memo && !inside && <span className="gantt-bar-memo">{memo}</span>}
                  {tag && <span className="gantt-out-tag">{tag}</span>}
                </span>
              )}
            </Fragment>
          )
        })}
      </div>
    </div>
  )
}
