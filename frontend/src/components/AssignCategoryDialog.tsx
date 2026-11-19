import { useState } from 'react'
import type { FormEvent } from 'react'
import { useData } from '../store/useData'
import type { Doc } from '../types'
import { sortCategories } from '../utils'
import Modal from './Modal'

const NEW = '__new__'

interface Props {
  doc: Doc
  onClose: () => void
  /** 지정이 끝난 문서(카테고리가 채워져 있다) */
  onAssigned: (doc: Doc) => void
}

/** 미분류 문서의 분류 지정: 문서가 없는 카테고리 목록에서 고르거나 새 카테고리 이름을 적는다 */
export default function AssignCategoryDialog({ doc, onClose, onAssigned }: Props) {
  const { categories, docs, assignCategory } = useData()
  const free = sortCategories(categories.filter((c) => !docs.some((d) => d.categoryId === c.id)))
  const [choice, setChoice] = useState(free[0]?.id ?? NEW)
  const [name, setName] = useState('')
  const [error, setError] = useState('')

  const submit = async (e: FormEvent) => {
    e.preventDefault()
    e.stopPropagation() // 포털을 넘어 바깥 폼으로 전파되지 않게
    try {
      onAssigned(await assignCategory(doc.id, choice === NEW ? { name } : choice))
      onClose()
    } catch (err) {
      setError(err instanceof Error ? err.message : String(err))
    }
  }

  return (
    <Modal
      title="분류 지정"
      onClose={onClose}
      footer={
        <>
          <button type="button" className="btn" onClick={onClose}>
            취소
          </button>
          <button type="submit" form="assign-form" className="btn primary">
            지정
          </button>
        </>
      }
    >
      <form id="assign-form" className="form" onSubmit={submit}>
        <p className="muted small">문서가 없는 카테고리에만 넣을 수 있습니다. 문서 제목은 카테고리 이름이 됩니다.</p>
        <label className="field">
          <span>카테고리</span>
          <select
            value={choice}
            onChange={(e) => {
              setChoice(e.target.value)
              setError('')
            }}
          >
            {free.map((c) => (
              <option key={c.id} value={c.id}>
                {c.name}
              </option>
            ))}
            <option value={NEW}>새 카테고리 만들기</option>
          </select>
        </label>
        {choice === NEW && (
          <label className="field">
            <span>새 카테고리 이름</span>
            <input
              autoFocus
              value={name}
              maxLength={40}
              onChange={(e) => {
                setName(e.target.value)
                setError('')
              }}
              placeholder="예: 계약서, 회의록, 인사"
            />
          </label>
        )}
        {error && <p className="form-error">{error}</p>}
      </form>
    </Modal>
  )
}
