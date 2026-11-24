import { useState } from 'react'
import type { FormEvent } from 'react'
import { useData } from '../store/useData'
import type { Category } from '../types'
import Modal from './Modal'

interface Props {
  /** 수정할 카테고리. 없으면 새로 만들기 */
  category?: Category
  onClose: () => void
  onSaved?: (category: Category) => void
}

export default function CategoryDialog({ category, onClose, onSaved }: Props) {
  const { categories, addCategory, editCategory } = useData()
  const [name, setName] = useState(category?.name ?? '')
  const [error, setError] = useState('')

  const submit = async (e: FormEvent) => {
    e.preventDefault()
    e.stopPropagation() // React 이벤트는 포털을 넘어 바깥 폼으로 전파되므로 차단
    const trimmed = name.trim()
    if (!trimmed) return setError('이름을 입력하세요.')
    const duplicate = categories.some((c) => c.id !== category?.id && c.name === trimmed)
    if (duplicate) return setError('같은 이름의 문서가 있습니다.')
    try {
      const input = { name: trimmed }
      if (category) {
        await editCategory(category.id, input)
        onSaved?.({ ...category, ...input })
      } else {
        onSaved?.(await addCategory(input))
      }
      onClose()
    } catch (err) {
      setError(err instanceof Error ? err.message : String(err))
    }
  }

  return (
    <Modal
      title={category ? '문서 수정' : '새 문서'}
      onClose={onClose}
      footer={
        <>
          <button type="button" className="btn" onClick={onClose}>
            취소
          </button>
          <button type="submit" form="category-form" className="btn primary">
            {category ? '저장' : '만들기'}
          </button>
        </>
      }
    >
      <form id="category-form" className="form" onSubmit={submit}>
        <label className="field">
          <span>문서 이름</span>
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

        {error && <p className="form-error">{error}</p>}
      </form>
    </Modal>
  )
}
