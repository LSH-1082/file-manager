import { useState } from 'react'
import type { DragEvent, ReactNode } from 'react'
import { useData } from '../store/useData'
import type { Schedule } from '../types'
import GanttCellText from './GanttCellText'
import { DAY_PX, dayIndex, formatPeriod } from './ganttScale'
import GanttRow from './GanttRow'
import { IconEdit, IconPlus } from './icons'
import { docRows, groupColorClass, groupOf } from './scheduleModel'
import type { GanttLayoutProps } from './scheduleModel'

/** 묶음 칸 보기: 일정마다 기간만큼의 색칸에 그 일정의 문서 행, 맨 아래에 일정과 관계없는 문서(없으면 끄는 동안만 보인다).
 *  막대를 다른 묶음에 끌어 놓으면 소속 일정을 바꾼다(날짜·메모는 그대로) */
export default function GanttGroupLayout({ groups, scale, onEditGroup, onAdd, onEdit }: GanttLayoutProps) {
  const { docs, categories, schedules, editSchedule } = useData()
  /** 끌고 있는 막대의 지금 묶음 키(일정 id, 일정과 관계없음은 ''). 끌지 않으면 null */
  const [dragFrom, setDragFrom] = useState<string | null>(null)
  const [moveError, setMoveError] = useState('')
  const px = DAY_PX[scale.unit]
  const noDocs = docs.length === 0
  const rowsIn = (items: Schedule[]) => docRows(items, docs, categories)
  const freeRows = rowsIn(schedules.filter((s) => !groupOf(s, groups)))

  const keyOf = (s: Schedule) => groupOf(s, groups)?.id ?? ''

  /** 묶음(key)이 놓을 곳이 되게 하는 속성. 끄는 막대의 지금 묶음은 놓을 곳이 아니다 */
  const dropProps = (key: string) => {
    const droppable = dragFrom !== null && dragFrom !== key
    return {
      droppable,
      onDragOver: (e: DragEvent) => {
        if (!droppable) return
        e.preventDefault()
        e.dataTransfer.dropEffect = 'move'
      },
      onDrop: (e: DragEvent) => {
        if (!droppable) return
        e.preventDefault()
        setDragFrom(null)
        const s = schedules.find((x) => x.id === e.dataTransfer.getData('text/plain'))
        if (!s || keyOf(s) === key) return
        editSchedule(s.id, {
          ...(key ? { groupId: key } : {}),
          startDate: s.startDate,
          plannedSendDate: s.plannedSendDate,
          ...(s.memo ? { memo: s.memo } : {}),
        }).catch((err: unknown) => setMoveError(`일정을 옮기지 못했습니다. ${err instanceof Error ? err.message : String(err)}`))
      },
    }
  }
  const dragProps = {
    onDragStart: (s: Schedule) => {
      setMoveError('')
      setDragFrom(keyOf(s))
    },
    onDragEnd: () => setDragFrom(null),
  }
  const free = dropProps('')

  const addButton = (label: string, title: string, groupId?: string) => (
    <button
      type="button"
      className="icon-btn"
      aria-label={label}
      title={noDocs ? '올린 문서가 없습니다' : title}
      disabled={noDocs}
      onClick={() => onAdd(groupId)}
    >
      <IconPlus />
    </button>
  )

  return (
    <>
      {/* 옮기기 실패 안내 자리. 빈 자리는 CSS가 숨긴다 */}
      <p className="gantt-move-notice" role="status">
        {moveError}
      </p>
      {groups.map((group) => {
        const rows = rowsIn(schedules.filter((s) => s.groupId === group.id))
        const left = (dayIndex(group.startDate) - scale.start) * px
        const width = (dayIndex(group.endDate) - dayIndex(group.startDate) + 1) * px
        const cell = { left, width }
        const { droppable, ...drop } = dropProps(group.id)
        return (
          <div
            key={group.id}
            className={`gantt-group ${groupColorClass(group.colorNo)}${droppable ? ' droppable' : ''}`}
            role="group"
            aria-label={`${group.name} 일정`}
            {...drop}
          >
            <div className="gantt-group-cell" style={cell} />
            <div className="gantt-row gantt-group-head">
              <div className="gantt-name">
                <span className="gantt-name-text" title={`${group.name} ${formatPeriod(group.startDate, group.endDate)}`}>
                  {group.name}
                </span>
                <button type="button" className="icon-btn" aria-label={`${group.name} 일정 수정`} title="일정 수정" onClick={() => onEditGroup(group)}>
                  <IconEdit />
                </button>
                {addButton(`${group.name} 일정에 문서 추가`, '이 일정에 문서 추가', group.id)}
              </div>
              <div className="gantt-track">
                <GanttCellText cell={cell} todayX={scale.todayX} text={`${formatPeriod(group.startDate, group.endDate)} · 문서 ${rows.length}건`} />
              </div>
            </div>
            {rows.length > 0 ? (
              rows.map((row) => <GanttRow key={`${group.id}:${row.doc.id}`} row={row} scale={scale} groups={groups} onEdit={onEdit} {...dragProps} />)
            ) : (
              <EmptyRow>
                <GanttCellText cell={cell} todayX={scale.todayX} text="보낼 문서가 없습니다." />
              </EmptyRow>
            )}
          </div>
        )
      })}

      {(freeRows.length > 0 || free.droppable) && (
        <div
          className={`gantt-group free${free.droppable ? ' droppable' : ''}`}
          role="group"
          aria-label="일정과 관계없는 문서"
          onDragOver={free.onDragOver}
          onDrop={free.onDrop}
        >
          <div className="gantt-row gantt-group-head">
            <div className="gantt-name">
              <span className="gantt-name-text">일정과 관계없는 문서</span>
            </div>
            <div className="gantt-track" />
          </div>
          {freeRows.length > 0 ? (
            freeRows.map((row) => <GanttRow key={`free:${row.doc.id}`} row={row} scale={scale} groups={groups} onEdit={onEdit} {...dragProps} />)
          ) : (
            <EmptyRow>
              <span className="gantt-cell-text crossed" style={{ left: 0, right: 0 }}>
                <span className="gantt-whole-text">여기에 놓으면 일정과 관계없는 문서가 됩니다.</span>
              </span>
            </EmptyRow>
          )}
        </div>
      )}
    </>
  )
}

function EmptyRow({ children }: { children: ReactNode }) {
  return (
    <div className="gantt-row gantt-group-empty">
      <div className="gantt-name" />
      <div className="gantt-track">{children}</div>
    </div>
  )
}
