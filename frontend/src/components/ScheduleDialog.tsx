import { useRef, useState } from 'react'
import type { FormEvent } from 'react'
import { useData } from '../store/useData'
import type { Schedule } from '../types'
import { docTitle, isUncategorized } from '../utils'
import { formatPeriod, todayIso } from './ganttScale'
import Modal, { ConfirmDialog } from './Modal'
import { groupOf, outOfPeriodText, sortGroups } from './scheduleModel'

/** 메모 최대 글자 수(앞뒤 공백 제외). 서버 검사(docs/api/README.md 8.23~8.26 ⑤)와 같다 */
const MEMO_MAX = 20

interface Props {
  /** 수정할 발송 문서. 없으면 새로 만들기 */
  schedule?: Schedule
  /** 새로 만들 때 처음 골라 둘 일정. 없으면 '일정과 관계없음' */
  groupId?: string
  onClose: () => void
}

/** 발송 문서(간트 막대) 추가·수정·삭제 창. 문서는 추가할 때만 고르고(D-28) 소속 일정은 수정할 때도 바꿀 수 있다 */
export default function ScheduleDialog({ schedule, groupId: initialGroupId = '', onClose }: Props) {
  const { docs, categories, scheduleGroups, addSchedule, editSchedule, removeSchedule } = useData()
  const [documentId, setDocumentId] = useState('')
  // 수정이면 지금 소속 일정(목록에서 사라졌으면 '일정과 관계없음')에서 시작한다
  const [groupId, setGroupId] = useState(schedule ? (groupOf(schedule, scheduleGroups)?.id ?? '') : initialGroupId)
  /** 사용자가 고친 시작일. 고치기 전(null)에는 고른 일정에 따라 기본값이 바뀐다 */
  const [startInput, setStartInput] = useState<string | null>(schedule?.startDate ?? null)
  const [plannedSendDate, setPlannedSendDate] = useState(schedule?.plannedSendDate ?? '')
  const [memo, setMemo] = useState(schedule?.memo ?? '')
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

  const byTitle = (a: { title: string }, b: { title: string }) => a.title.localeCompare(b.title, 'ko')
  // 문서 없는 카테고리는 docs에 없으므로 목록에 나오지 않는다
  const categorized = docs
    .filter((d) => !isUncategorized(categories, d))
    .map((d) => ({ id: d.id, title: docTitle(categories, d) }))
    .sort(byTitle)
  const uncategorized = docs
    .filter((d) => isUncategorized(categories, d))
    .map((d) => ({ id: d.id, title: d.title }))
    .sort(byTitle)

  const scheduleDoc = schedule ? docs.find((d) => d.id === schedule.documentId) : undefined
  const scheduleTitle = schedule ? (scheduleDoc ? docTitle(categories, scheduleDoc) : schedule.documentTitle) : ''

  // 고르는 중에 목록에서 사라진 일정은 화면(select)과 같이 '일정과 관계없음'으로 본다
  const group = scheduleGroups.find((g) => g.id === groupId)
  const today = todayIso()
  const startDate = startInput ?? (group && (today < group.startDate || today > group.endDate) ? group.startDate : today)
  const outText = group && startDate && plannedSendDate && startDate <= plannedSendDate ? outOfPeriodText({ startDate, plannedSendDate }, group) : ''

  const clearError = () => setError('')

  const submit = async (e: FormEvent) => {
    e.preventDefault()
    e.stopPropagation() // React 이벤트는 포털을 넘어 바깥 폼으로 전파되므로 차단
    // 서버(8.23~8.26)와 같은 순서·문구로 먼저 막는다
    if (!schedule && !documentId) return setError('문서를 고르세요.')
    if (!startDate) return setError('시작일을 입력하세요.')
    if (!plannedSendDate) return setError('발송 예정일을 입력하세요.')
    if (startDate > plannedSendDate) return setError('발송 예정일은 시작일과 같거나 뒤여야 합니다.')
    const trimmed = memo.trim()
    if (trimmed.length > MEMO_MAX) return setError('메모는 20자까지 쓸 수 있습니다.')
    const memoPart = trimmed ? { memo: trimmed } : {}
    const groupPart = group ? { groupId: group.id } : {}
    if (!begin()) return
    try {
      // 수정은 전체 교체라 메모를 비우면 메모가, 일정과 관계없음을 고르면 소속 일정이 지워진다
      if (schedule) await editSchedule(schedule.id, { ...groupPart, startDate, plannedSendDate, ...memoPart })
      else await addSchedule({ documentId, ...groupPart, startDate, plannedSendDate, ...memoPart })
      onClose()
    } catch (err) {
      fail(err)
    }
  }

  const remove = async () => {
    if (!schedule || !begin()) return
    try {
      await removeSchedule(schedule.id)
      onClose()
    } catch (err) {
      fail(err)
    }
  }

  return (
    <>
      <Modal
        title={schedule ? '발송 문서 수정' : '문서 추가'}
        // 삭제 확인 창이 열려 있는 동안에는 Esc가 확인 창만 닫도록 수정 창 닫기를 막는다(두 창 모두 window Esc 리스너를 건다)
        onClose={() => {
          if (!confirming) onClose()
        }}
        footer={
          <>
            {schedule && (
              <button type="button" className="btn danger schedule-delete" disabled={busy} onClick={() => setConfirming(true)}>
                삭제
              </button>
            )}
            <button type="button" className="btn" onClick={onClose}>
              취소
            </button>
            <button type="submit" form="schedule-form" className="btn primary" disabled={busy}>
              {schedule ? '저장' : '추가'}
            </button>
          </>
        }
      >
        {/* 검사는 submit의 앱 검사 한 곳에서 서버와 같은 문구로 한다. 브라우저 기본 검사(required·min) 말풍선이 가로채지 않게 noValidate */}
        <form id="schedule-form" className="form" noValidate onSubmit={submit}>
          {schedule ? (
            <div className="field">
              <span>문서</span>
              <p className="field-static">{scheduleTitle}</p>
            </div>
          ) : (
            <label className="field">
              <span>문서</span>
              <select
                required
                autoFocus
                value={documentId}
                onChange={(e) => {
                  setDocumentId(e.target.value)
                  clearError()
                }}
              >
                <option value="">문서를 고르세요</option>
                {categorized.length > 0 && (
                  <optgroup label="카테고리">
                    {categorized.map((d) => (
                      <option key={d.id} value={d.id}>
                        {d.title}
                      </option>
                    ))}
                  </optgroup>
                )}
                {uncategorized.length > 0 && (
                  <optgroup label="미분류">
                    {uncategorized.map((d) => (
                      <option key={d.id} value={d.id}>
                        {d.title}
                      </option>
                    ))}
                  </optgroup>
                )}
              </select>
            </label>
          )}
          <label className="field">
            <span>일정</span>
            <select
              value={group?.id ?? ''}
              onChange={(e) => {
                setGroupId(e.target.value)
                clearError()
              }}
            >
              <option value="">일정과 관계없음</option>
              {sortGroups(scheduleGroups).map((g) => (
                <option key={g.id} value={g.id}>
                  {g.name} ({formatPeriod(g.startDate, g.endDate)})
                </option>
              ))}
            </select>
          </label>

          <div className="schedule-dates">
            <label className="field">
              <span>시작일</span>
              <input
                type="date"
                value={startDate}
                onChange={(e) => {
                  setStartInput(e.target.value)
                  clearError()
                }}
              />
            </label>

            <label className="field">
              <span>발송 예정일</span>
              <input
                type="date"
                value={plannedSendDate}
                min={startDate || undefined}
                onChange={(e) => {
                  setPlannedSendDate(e.target.value)
                  clearError()
                }}
              />
            </label>
          </div>

          <label className="field">
            <span>메모</span>
            <input
              value={memo}
              maxLength={MEMO_MAX}
              placeholder="예: 2차 발송"
              onChange={(e) => {
                setMemo(e.target.value)
                clearError()
              }}
            />
            <span className="field-hint">선택, 20자 이하</span>
          </label>

          {/* 안내 자리는 항상 두어 글자가 생길 때 읽어 준다(빈 자리는 CSS가 숨긴다) */}
          <p className="schedule-warn" role="status">
            {!error && outText && `${outText} 저장은 됩니다.`}
          </p>
          {error && <p className="form-error">{error}</p>}
        </form>
      </Modal>

      {confirming && schedule && (
        <ConfirmDialog
          title="발송 문서 삭제"
          message={
            <>
              <strong>{scheduleTitle}</strong> 발송 문서({schedule.memo ? `${schedule.memo} · ` : ''}
              {formatPeriod(schedule.startDate, schedule.plannedSendDate)})를 삭제할까요?
            </>
          }
          onConfirm={() => void remove()}
          onClose={() => setConfirming(false)}
        />
      )}
    </>
  )
}
