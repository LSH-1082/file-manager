import { useRef, useState } from 'react'
import type { FormEvent } from 'react'
import { useData } from '../store/useData'
import type { ScheduleGroup } from '../types'
import { formatPeriod, todayIso } from './ganttScale'
import Modal, { ConfirmDialog } from './Modal'
import { docRows, isOutOfPeriod } from './scheduleModel'

/** 일정 이름 최대 글자 수(앞뒤 공백 제외). 서버 검사(docs/api/README.md 8.27~8.30 ②)와 같다 */
const NAME_MAX = 20

interface Props {
  /** 수정할 일정. 없으면 등록 */
  group?: ScheduleGroup
  onClose: () => void
}

/** 일정(기간 묶음) 등록·수정·삭제 창. 배경색은 서버가 정하므로 고르지 않는다 */
export default function ScheduleGroupDialog({ group, onClose }: Props) {
  const { docs, categories, schedules, addScheduleGroup, editScheduleGroup, removeScheduleGroup } = useData()
  const [name, setName] = useState(group?.name ?? '')
  const [startDate, setStartDate] = useState(group?.startDate ?? todayIso())
  const [endDate, setEndDate] = useState(group?.endDate ?? '')
  const [error, setError] = useState('')
  const [confirming, setConfirming] = useState(false)
  /** 저장·삭제 요청 중에는 버튼을 막아 중복 요청을 보내지 않는다. 다시 그리기 전의 연속 클릭은 ref로 막는다 */
  const [busy, setBusy] = useState(false)
  const busyRef = useRef(false)
  const begin = () => {
    if (busyRef.current) return false
    busyRef.current = true
    setBusy(true)
    return true
  }
  const fail = (err: unknown) => {
    busyRef.current = false
    setBusy(false)
    setError(err instanceof Error ? err.message : String(err))
  }

  const clearError = () => setError('')

  /** 소속 막대. 화면에 보이는 것(문서가 있는 것)만, 화면의 행 순서로 */
  const bars = group
    ? docRows(
        schedules.filter((s) => s.groupId === group.id),
        docs,
        categories,
      ).flatMap((row) => row.items.map((schedule) => ({ title: row.title, schedule })))
    : []
  const periodChanged = !!group && (startDate !== group.startDate || endDate !== group.endDate)
  const outCount =
    periodChanged && startDate && endDate && startDate <= endDate
      ? bars.filter((b) => isOutOfPeriod(b.schedule, { startDate, endDate })).length
      : 0

  const submit = async (e: FormEvent) => {
    e.preventDefault()
    e.stopPropagation() // React 이벤트는 포털을 넘어 바깥 폼으로 전파되므로 차단
    // 서버(8.27~8.30)와 같은 순서·문구로 먼저 막는다. 날짜 형식은 date 입력이라 서버에 맡긴다
    const trimmed = name.trim()
    if (!trimmed) return setError('일정 이름을 입력하세요.')
    if (trimmed.length > NAME_MAX) return setError('일정 이름은 20자까지 쓸 수 있습니다.')
    if (!startDate) return setError('시작일을 입력하세요.')
    if (!endDate) return setError('종료일을 입력하세요.')
    if (startDate > endDate) return setError('종료일은 시작일과 같거나 뒤여야 합니다.')
    if (!begin()) return
    const input = { name: trimmed, startDate, endDate }
    try {
      if (group) await editScheduleGroup(group.id, input)
      else await addScheduleGroup(input)
      onClose()
    } catch (err) {
      fail(err)
    }
  }

  const remove = async () => {
    if (!group || !begin()) return
    try {
      await removeScheduleGroup(group.id)
      onClose()
    } catch (err) {
      fail(err)
    }
  }

  return (
    <>
      <Modal
        title={group ? '일정 수정' : '일정 등록'}
        // 삭제 확인 창이 열려 있는 동안에는 Esc가 확인 창만 닫도록 수정 창 닫기를 막는다(두 창 모두 window Esc 리스너를 건다)
        onClose={() => {
          if (!confirming) onClose()
        }}
        footer={
          <>
            {group && (
              <button type="button" className="btn danger schedule-delete" disabled={busy} onClick={() => setConfirming(true)}>
                삭제
              </button>
            )}
            <button type="button" className="btn" onClick={onClose}>
              취소
            </button>
            <button type="submit" form="schedule-group-form" className="btn primary" disabled={busy}>
              {group ? '저장' : '등록'}
            </button>
          </>
        }
      >
        {/* 검사는 submit 한 곳에서 서버와 같은 문구로 한다. 브라우저 기본 검사 말풍선이 가로채지 않게 noValidate */}
        <form id="schedule-group-form" className="form" noValidate onSubmit={submit}>
          <label className="field">
            <span>일정 이름</span>
            <input
              value={name}
              maxLength={NAME_MAX}
              required
              autoFocus={!group}
              placeholder="예: 연말 정산"
              onChange={(e) => {
                setName(e.target.value)
                clearError()
              }}
            />
            <span className="field-hint">20자 이하</span>
          </label>

          <div className="schedule-dates">
            <label className="field">
              <span>시작일</span>
              <input
                type="date"
                value={startDate}
                onChange={(e) => {
                  setStartDate(e.target.value)
                  clearError()
                }}
              />
            </label>

            <label className="field">
              <span>종료일</span>
              <input
                type="date"
                value={endDate}
                min={startDate || undefined}
                onChange={(e) => {
                  setEndDate(e.target.value)
                  clearError()
                }}
              />
            </label>
          </div>

          {/* 안내 자리는 항상 두어 글자가 생길 때 읽어 준다(빈 자리는 CSS가 숨긴다) */}
          <p className="schedule-warn" role="status">
            {!error && outCount > 0 && `이 기간을 벗어나는 막대가 ${outCount}건 있습니다. 저장은 됩니다.`}
          </p>
          {error && <p className="form-error">{error}</p>}
        </form>
      </Modal>

      {confirming && group && (
        <ConfirmDialog
          title="일정 삭제"
          message={
            <>
              <strong>{group.name}</strong> 일정을 삭제할까요?
              {bars.length > 0 && (
                <>
                  {` 아래 막대 ${bars.length}건은 일정과 관계없는 문서로 남습니다.`}
                  {/* ConfirmDialog가 <p> 안에 그리므로 ul 대신 span을 쓴다 */}
                  <span className="schedule-confirm-list" role="list">
                    {bars.map(({ title, schedule }) => (
                      <span key={schedule.id} role="listitem">
                        {[title, schedule.memo, formatPeriod(schedule.startDate, schedule.plannedSendDate)].filter(Boolean).join(' · ')}
                      </span>
                    ))}
                  </span>
                </>
              )}
            </>
          }
          onConfirm={() => void remove()}
          onClose={() => setConfirming(false)}
        />
      )}
    </>
  )
}
