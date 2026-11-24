import './GanttView.css'
import { useEffect, useLayoutEffect, useMemo, useReducer, useRef, useState } from 'react'
import { dueWarnText, IMMINENT_DAYS } from '../deadline'
import { useData } from '../store/useData'
import type { Schedule, ScheduleGroup } from '../types'
import { STEPS } from '../versioning'
import GanttGroupLayout from './GanttGroupLayout'
import { axisTicks, DAY_PX, dayIndex, formatMD, timelineRange, todayIso } from './ganttScale'
import type { GanttUnit } from './ganttScale'
import { IconPlus } from './icons'
import ScheduleDialog from './ScheduleDialog'
import ScheduleGroupDialog from './ScheduleGroupDialog'
import { docRows, groupColorClass, groupOf, isOutOfPeriod, sortGroups } from './scheduleModel'

/** 왼쪽 문서 이름 열 폭(px). GanttView.css의 .gantt-name과 같다 */
const NAME_W = 220
/** 차트 영역 최소 폭(px) */
const MIN_CHART_W = 1000

const UNITS: { unit: GanttUnit; label: string }[] = [
  { unit: 'day', label: '일' },
  { unit: 'week', label: '주' },
  { unit: 'month', label: '월' },
]

type DialogState =
  | null
  | { kind: 'create'; groupId?: string }
  | { kind: 'edit'; schedule: Schedule }
  | { kind: 'createGroup' }
  | { kind: 'editGroup'; group: ScheduleGroup }

/** 앞부분이 문서 열에 가려진 글자를 통째로 숨기고, 다 보이면 되돌린다. 스크롤마다 React로 다시 그리지 않고 표시 속성만 바꾼다 */
function hideCutTexts(scroll: HTMLElement) {
  const edge = scroll.getBoundingClientRect().left + NAME_W
  for (const el of scroll.querySelectorAll('.gantt-whole-text')) {
    const left = el.getBoundingClientRect().left + parseFloat(getComputedStyle(el).paddingLeft)
    el.toggleAttribute('data-cut', left < edge - 0.01)
  }
}

export default function GanttView() {
  const { docs, categories, schedules, scheduleGroups, scheduleError, scheduleLoading, reloadSchedules } = useData()
  const [unit, setUnit] = useState<GanttUnit>('week')
  /** 글꼴이 늦게 오면 글자 폭을 다시 재도록 다시 그린다 */
  const [, redraw] = useReducer((n: number) => n + 1, 0)
  const [dialog, setDialog] = useState<DialogState>(null)
  const scrollRef = useRef<HTMLDivElement>(null)
  const titleRef = useRef<HTMLHeadingElement>(null)
  const retryRef = useRef<HTMLButtonElement>(null)
  /** '다시 시도'에 초점이 있는 채 눌렀다. 진행 중 disabled로 빠진 초점을 끝나면 되돌린다 */
  const retryFocused = useRef(false)
  /** 보이는 내용(일정·막대) 없이 실패했거나 실패한 채 화면을 연 뒤의 복구. at = 복구 중 맞춘 스크롤 위치(아직 내용이 없었으면 null) */
  const recovery = useRef<{ at: number | null } | null>(null)

  const groups = useMemo(() => sortGroups(scheduleGroups), [scheduleGroups])
  // 축 범위·범례·건수에 쓰는 모든 문서 행(행을 놓는 일은 레이아웃이 한다)
  const rows = useMemo(() => docRows(schedules, docs, categories), [schedules, docs, categories])

  const today = todayIso()
  const shownSchedules = rows.flatMap((r) => r.items)
  const range = timelineRange(
    [...groups.flatMap((g) => [g.startDate, g.endDate]), ...shownSchedules.flatMap((s) => [s.startDate, s.plannedSendDate])],
    today,
    unit,
    MIN_CHART_W,
  )
  // 기간 밖이거나 임박(3일 이하)·기한 넘김인데 아직 발송하지 않은 막대가 하나라도 있는 서로 다른 문서 수
  const attentionCount = new Set(
    rows
      .filter((r) =>
        r.items.some((s) => {
          const group = groupOf(s, groups)
          return (group !== undefined && isOutOfPeriod(s, group)) || dueWarnText(s, r.doc) !== ''
        }),
      )
      .map((r) => r.doc.id),
  ).size
  const px = DAY_PX[unit]
  const chartWidth = (range.end - range.start + 1) * px
  const ticks = axisTicks(unit, range.start, range.end)
  const todayX = (dayIndex(today) - range.start) * px + px / 2
  const isTodayTick = (t: { left: number; width: number }) => todayX >= t.left && todayX < t.left + t.width

  const scrollToToday = () => {
    const el = scrollRef.current
    if (!el) return
    el.scrollLeft = Math.max(0, NAME_W + todayX - el.clientWidth / 3)
    if (recovery.current && recovery.current.at !== null) recovery.current.at = el.scrollLeft
  }

  // 단위를 바꿀 때마다(처음 포함) 오늘 선이 보이도록 스크롤한다. 데이터가 바뀔 때는 옮기지 않는다
  useLayoutEffect(() => {
    scrollToToday()
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [unit])

  const failed = scheduleError !== ''
  const hasContent = rows.length > 0 || groups.length > 0

  // 실패한 채 화면을 다시 열었으면(나갔다 돌아옴) 처음 맞춘 위치에서 복구를 시작한다. 위의 처음 스크롤 맞춤 뒤에 돌아야 한다
  useLayoutEffect(() => {
    if (failed && scrollRef.current) recovery.current = { at: scrollRef.current.scrollLeft }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  // 예외: 보이는 내용 없이 조회가 실패한 화면에 내용이 들어오면 축이 새로 정해지므로 처음 스크롤을 다시 맞춘다.
  // 실패 중 이미 보이는 내용이 있으면, 그 뒤 사용자가 옮기지 않았을 때만 조회 성공에 한 번 더 맞춘다
  useLayoutEffect(() => {
    if (failed && !hasContent) {
      recovery.current = { at: null }
      return
    }
    const el = scrollRef.current
    if (!recovery.current || !el) return
    if (hasContent && (recovery.current.at === null || recovery.current.at === el.scrollLeft)) {
      scrollToToday()
      recovery.current.at = el.scrollLeft
    }
    if (!failed) recovery.current = null
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [failed, hasContent])

  // 다시 시도가 끝나면 초점을 버튼으로, 성공해 버튼이 사라졌으면 제목으로 되돌린다. 그 사이 다른 곳으로 옮겼으면 두지 않는다
  useLayoutEffect(() => {
    if (scheduleLoading || !retryFocused.current) return
    retryFocused.current = false
    if (document.activeElement && document.activeElement !== document.body) return
    ;(retryRef.current ?? titleRef.current)?.focus({ preventScroll: true })
  }, [scheduleLoading])

  // 그릴 때마다, 그리고 스크롤·창 폭이 바뀔 때 가려진 글자를 다시 정한다. 위의 스크롤 맞춤 뒤에 돌아야 한다
  useLayoutEffect(() => {
    if (scrollRef.current) hideCutTexts(scrollRef.current)
  })
  useEffect(() => {
    const el = scrollRef.current
    if (!el) return
    const update = () => hideCutTexts(el)
    const resize = new ResizeObserver(update)
    resize.observe(el)
    el.addEventListener('scroll', update, { passive: true })
    document.fonts.addEventListener('loadingdone', redraw)
    return () => {
      resize.disconnect()
      el.removeEventListener('scroll', update)
      document.fonts.removeEventListener('loadingdone', redraw)
    }
  }, [])

  const retry = () => {
    retryFocused.current = document.activeElement === retryRef.current
    void reloadSchedules()
  }

  return (
    <section className="gantt" aria-labelledby="gantt-title">
      <header className="gantt-head">
        <h1 id="gantt-title" ref={titleRef} tabIndex={-1}>일정</h1>
        <div className="gantt-actions">
          <div className="gantt-units" role="group" aria-label="시간 축 단위">
            {UNITS.map((u) => (
              <button key={u.unit} type="button" className="gantt-unit" aria-pressed={unit === u.unit} onClick={() => setUnit(u.unit)}>
                {u.label}
              </button>
            ))}
          </div>
          <button type="button" className="btn" onClick={() => setDialog({ kind: 'createGroup' })}>
            <IconPlus />
            일정 등록
          </button>
          <button
            type="button"
            className="btn primary"
            disabled={docs.length === 0}
            title={docs.length === 0 ? '올린 문서가 없습니다' : undefined}
            onClick={() => setDialog({ kind: 'create' })}
          >
            <IconPlus />
            문서 추가
          </button>
        </div>
      </header>

      <div className="gantt-frame">
        {scheduleError && (
          <div className="gantt-notice">
            <p role="status">
              일정을 불러오지 못했습니다. {scheduleError}
              {hasContent && <span className="gantt-notice-stale">보이는 일정과 문서가 최신이 아닐 수 있습니다.</span>}
            </p>
            <button type="button" ref={retryRef} className="btn small" disabled={scheduleLoading} onClick={retry}>
              다시 시도
            </button>
          </div>
        )}

        {attentionCount > 0 && (
          <p className="gantt-out-notice" role="status">
            확인이 필요한 문서가 {attentionCount}건 있습니다.
          </p>
        )}

        <div className="gantt-scroll" ref={scrollRef}>
          <div className={`gantt-grid unit-${unit}`} style={{ width: NAME_W + chartWidth }}>
            <div className="gantt-row gantt-axis">
              <div className="gantt-name">문서</div>
              <div className="gantt-track">
                {ticks.top.map((t) => (
                  <div key={`top-${t.key}`} className="gantt-tick top" style={{ left: t.left, width: t.width }}>
                    <span className="gantt-tick-label">{t.label}</span>
                  </div>
                ))}
                {ticks.bottom.map((t) => (
                  <div
                    key={`bottom-${t.key}`}
                    className={`gantt-tick${t.weekend ? ' weekend' : ''}${isTodayTick(t) ? ' today' : ''}`}
                    style={{ left: t.left, width: t.width }}
                  >
                    {t.label}
                  </div>
                ))}
              </div>
            </div>

            {hasContent ? (
              <>
                {ticks.bottom.map((t) => (
                  <div
                    key={`line-${t.key}`}
                    className={`gantt-gridline${t.weekend ? ' weekend' : ''}`}
                    style={{ left: NAME_W + t.left, width: t.width }}
                    aria-hidden="true"
                  />
                ))}
                <GanttGroupLayout
                  groups={groups}
                  scale={{ unit, start: range.start, todayX }}
                  onEditGroup={(group) => setDialog({ kind: 'editGroup', group })}
                  onAdd={(groupId) => setDialog({ kind: 'create', groupId })}
                  onEdit={(schedule) => setDialog({ kind: 'edit', schedule })}
                />
              </>
            ) : (
              !failed && (
                <p className="gantt-empty">
                  {docs.length === 0
                    ? '올린 문서가 없습니다. 문서를 먼저 올리면 발송 문서를 등록할 수 있습니다.'
                    : '등록된 일정과 발송 문서가 없습니다. 일정 등록이나 문서 추가로 시작하세요.'}
                </p>
              )
            )}

            <div className="gantt-today" style={{ left: NAME_W + todayX }} title={`오늘 ${formatMD(today)}`} aria-hidden="true">
              <span className="gantt-today-label">오늘 {formatMD(today)}</span>
            </div>
          </div>
        </div>
      </div>

      {hasContent && (
        <ul className="gantt-legend" aria-label="범례">
          {STEPS.filter((st) => rows.some((r) => r.stage === st.key)).map((st) => (
            <li key={st.key}>
              <span className={`gantt-legend-mark ${st.key}`} aria-hidden="true" />
              {st.label}
            </li>
          ))}
          {groups.length > 0 && (
            <li className="sep">
              <span className={`gantt-legend-mark area ${groupColorClass(groups[0].colorNo)}`} aria-hidden="true" />
              일정
            </li>
          )}
          {attentionCount > 0 && (
            <li className="sep">
              <span className="gantt-legend-mark working out" aria-hidden="true" />
              주의 필요
              <span>(일정 기간 밖이거나 발송 기한이 {IMMINENT_DAYS}일 이내·지난 문서)</span>
            </li>
          )}
        </ul>
      )}

      {(dialog?.kind === 'create' || dialog?.kind === 'edit') && (
        <ScheduleDialog
          schedule={dialog.kind === 'edit' ? dialog.schedule : undefined}
          groupId={dialog.kind === 'create' ? dialog.groupId : undefined}
          onClose={() => setDialog(null)}
        />
      )}
      {(dialog?.kind === 'createGroup' || dialog?.kind === 'editGroup') && (
        <ScheduleGroupDialog group={dialog.kind === 'editGroup' ? dialog.group : undefined} onClose={() => setDialog(null)} />
      )}
    </section>
  )
}
