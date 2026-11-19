import { useEffect, useMemo, useRef, useState } from 'react'
import { useData } from '../store/useData'
import type { Category, CategoryFilter } from '../types'
import { sortCategories } from '../utils'
import { categoryDoc, latestVersion, STAGE_LABEL, versionStage } from '../versioning'
import type { Stage } from '../versioning'
import CategoryDialog from './CategoryDialog'
import { BrandMark, IconClose, IconEdit, IconInbox, IconMenu, IconPlus, IconStack, IconTrash } from './icons'
import { ConfirmDialog } from './Modal'

interface Props {
  filter: CategoryFilter
  onFilterChange: (filter: CategoryFilter) => void
}

type DialogState =
  | { kind: 'create' }
  | { kind: 'edit'; category: Category }
  | { kind: 'delete'; category: Category }
  | null

export default function Sidebar({ filter, onFilterChange }: Props) {
  const { categories, docs, resources, removeCategory } = useData()
  const [dialog, setDialog] = useState<DialogState>(null)
  /** 좁은 화면에서 카테고리 시트가 열려 있는지 */
  const [open, setOpen] = useState(false)
  const menuBtnRef = useRef<HTMLButtonElement>(null)
  const closeBtnRef = useRef<HTMLButtonElement>(null)

  const sorted = useMemo(() => sortCategories(categories), [categories])

  // 각 카테고리 문서의 최신 버전 단계(거절됨은 작업중). 문서가 없으면 null
  const stages = useMemo(
    () =>
      new Map<string, Stage | null>(
        categories.map((c) => {
          const d = categoryDoc(docs, c.id)
          return [c.id, d ? versionStage(latestVersion(d)) : null] as const
        }),
      ),
    [categories, docs],
  )

  const uncategorizedCount = docs.filter(
    (d) => !d.categoryId || !categories.some((c) => c.id === d.categoryId),
  ).length

  const choose = (next: CategoryFilter) => {
    onFilterChange(next)
    setOpen(false)
  }

  /** 닫기 버튼·Escape·가림막으로 닫을 때는 메뉴 버튼으로 포커스를 돌려준다 */
  const dismiss = () => {
    setOpen(false)
    menuBtnRef.current?.focus()
  }

  // 시트가 열리면 닫기 버튼으로 포커스를 옮긴다
  useEffect(() => {
    if (open) closeBtnRef.current?.focus()
  }, [open])

  // 열린 시트는 Escape로 닫힌다. 다이얼로그가 열려 있으면 다이얼로그만 닫히도록 등록하지 않는다
  useEffect(() => {
    if (!open || dialog !== null) return
    const onKey = (e: KeyboardEvent) => {
      if (e.key !== 'Escape') return
      setOpen(false)
      menuBtnRef.current?.focus()
    }
    window.addEventListener('keydown', onKey)
    return () => window.removeEventListener('keydown', onKey)
  }, [open, dialog])

  const renderItem = (node: Category) => {
    const active = filter.kind === 'category' && filter.id === node.id
    const stage = stages.get(node.id) ?? null
    const stageLabel = stage ? STAGE_LABEL[stage] : '문서 없음'
    return (
      <li key={node.id}>
        <div className={`nav-item category-item ${active ? 'active' : ''}`}>
          <button
            type="button"
            className="nav-main"
            aria-current={active ? 'page' : undefined}
            onClick={() => choose({ kind: 'category', id: node.id })}
          >
            <span className={`dot stage-dot ${stage ?? 'none'}`} role="img" aria-label={stageLabel} title={stageLabel} />
            <span className="nav-label" title={node.name}>
              {node.name}
            </span>
          </button>
          <span className="item-actions">
            <button
              type="button"
              className="icon-btn"
              title="수정"
              aria-label={`${node.name} 수정`}
              onClick={() => setDialog({ kind: 'edit', category: node })}
            >
              <IconEdit size={14} />
            </button>
            <button
              type="button"
              className="icon-btn"
              title="삭제"
              aria-label={`${node.name} 삭제`}
              onClick={() => setDialog({ kind: 'delete', category: node })}
            >
              <IconTrash size={14} />
            </button>
          </span>
        </div>
      </li>
    )
  }

  return (
    <>
      <div className="topbar">
        <button ref={menuBtnRef} type="button" className="icon-btn menu-btn" aria-label="카테고리 메뉴 열기" aria-expanded={open} onClick={() => setOpen(true)}>
          <IconMenu />
        </button>
        <span className="brand-name">
          <BrandMark size={20} />
          문서 관리
        </span>
      </div>

      {open && <div className="sheet-scrim" aria-hidden="true" onClick={dismiss} />}
    <aside className={`sidebar ${open ? 'open' : ''}`} aria-label="카테고리">
      <div className="brand">
        <BrandMark />
        <div className="brand-text">
          <span className="brand-name">문서 관리</span>
        </div>
        <button ref={closeBtnRef} type="button" className="icon-btn sheet-close" aria-label="메뉴 닫기" onClick={dismiss}>
          <IconClose />
        </button>
      </div>

      <ul className="nav">
        <li>
          <div className={`nav-item ${filter.kind === 'all' ? 'active' : ''}`}>
            <button type="button" className="nav-main" aria-current={filter.kind === 'all' ? 'page' : undefined} onClick={() => choose({ kind: 'all' })}>
              <IconStack className="nav-icon" />
              <span className="nav-label">전체 문서</span>
              <span className="count num">{docs.length}</span>
            </button>
          </div>
        </li>
        <li>
          <div className={`nav-item ${filter.kind === 'uncategorized' ? 'active' : ''}`}>
            <button
              type="button"
              className="nav-main"
              aria-current={filter.kind === 'uncategorized' ? 'page' : undefined}
              onClick={() => choose({ kind: 'uncategorized' })}
            >
              <IconInbox className="nav-icon" />
              <span className="nav-label">미분류</span>
              <span className="count num">{uncategorizedCount}</span>
            </button>
          </div>
        </li>
      </ul>

      <div className="section-header">
        <span>카테고리</span>
        <button
          type="button"
          className="icon-btn"
          title="카테고리 추가"
          aria-label="카테고리 추가"
          onClick={() => setDialog({ kind: 'create' })}
        >
          <IconPlus size={14} />
        </button>
      </div>

      {sorted.length === 0 ? (
        <div className="sidebar-empty">
          <p>아직 카테고리가 없습니다. 계약서, 회의록처럼 문서를 묶을 이름을 만들어 두세요.</p>
          <button type="button" className="btn" onClick={() => setDialog({ kind: 'create' })}>
            <IconPlus size={14} />첫 카테고리 만들기
          </button>
        </div>
      ) : (
        <ul className="nav tree">{sorted.map(renderItem)}</ul>
      )}
    </aside>

      {dialog?.kind === 'create' && (
        <CategoryDialog
          onClose={() => setDialog(null)}
          onSaved={(c) => choose({ kind: 'category', id: c.id })}
        />
      )}
      {dialog?.kind === 'edit' && (
        <CategoryDialog category={dialog.category} onClose={() => setDialog(null)} />
      )}
      {dialog?.kind === 'delete' && (
        <ConfirmDialog
          title="카테고리 삭제"
          message={
            <>
              <strong>{dialog.category.name}</strong> 카테고리를 삭제할까요?
              <br />
              소속 문서는 삭제되지 않고 미분류로 이동합니다.
              {resources.some((r) => r.categoryId === dialog.category.id) && (
                <>
                  <br />
                  <strong>이 분류의 양식·참고자료 {resources.filter((r) => r.categoryId === dialog.category.id).length}개는 함께 삭제됩니다.</strong>
                </>
              )}
            </>
          }
          onConfirm={async () => {
            const { id } = dialog.category
            await removeCategory(id)
            if (filter.kind === 'category' && filter.id === id) onFilterChange({ kind: 'all' })
          }}
          onClose={() => setDialog(null)}
        />
      )}
    </>
  )
}
