import { useMemo, useState } from 'react'
import { DocUploader } from './components/DocPanel'
import DocView from './components/DocView'
import { IconBack } from './components/icons'
import Overview from './components/Overview'
import ResourcePanel from './components/ResourcePanel'
import Sidebar from './components/Sidebar'
import StatusBoard from './components/StatusBoard'
import VersionList from './components/VersionList'
import { useData } from './store/useData'
import type { CategoryFilter, Doc } from './types'
import { categoryPath, isUncategorized } from './utils'
import { categoryDoc } from './versioning'

type PanelMode =
  | { kind: 'view' }
  | { kind: 'resources' }
  | { kind: 'upload'; file: File | null; droppedMany: boolean; key: number }

export default function App() {
  const { categories, docs, resources, loading } = useData()
  const [filter, setFilter] = useState<CategoryFilter>({ kind: 'all' })
  const [selectedId, setSelectedId] = useState<string | null>(null)
  const [selectedVersionId, setSelectedVersionId] = useState<string | null>(null)
  const [mode, setMode] = useState<PanelMode>({ kind: 'view' })

  const visibleDocs = useMemo(
    () => (filter.kind === 'uncategorized' ? docs.filter((d) => isUncategorized(categories, d)) : docs),
    [docs, categories, filter],
  )

  const listTitle =
    filter.kind === 'all'
      ? '전체 문서'
      : filter.kind === 'uncategorized'
        ? '미분류'
        : categoryPath(categories, filter.id) || '카테고리'

  const defaultCategoryId = filter.kind === 'category' ? filter.id : null
  const filterCategory = filter.kind === 'category' ? categories.find((c) => c.id === filter.id) : undefined
  // 카테고리 하나 = 문서 하나. 카테고리를 고르면 그 문서가 선택된다
  const selectedDoc = filterCategory ? categoryDoc(docs, filterCategory.id) : docs.find((d) => d.id === selectedId)

  const changeFilter = (next: CategoryFilter) => {
    setFilter(next)
    setSelectedVersionId(null)
    if (next.kind === 'category') setSelectedId(categoryDoc(docs, next.id)?.id ?? null)
    else if (mode.kind === 'view') setSelectedId(null)
    // 업로드 중에 다른 분류로 옮기면 그 분류의 문서를 보여준다
    if (mode.kind === 'upload') setMode({ kind: 'view' })
    // 카테고리를 벗어나면 자료 화면을 닫고, 다른 카테고리로 옮기면 그 카테고리 자료를 보여준다
    if (mode.kind === 'resources' && next.kind !== 'category') setMode({ kind: 'view' })
  }

  if (loading)
    return (
      <div className="loading" role="status">
        불러오는 중…
      </div>
    )

  // 좁은 화면에서는 목록과 상세 중 한 영역만 보인다
  // 문서가 없는 카테고리를 고르면 상세 영역에 첫 파일 올리기 화면이 바로 열린다
  const firstUpload = mode.kind === 'view' && !!filterCategory && !selectedDoc
  const showDetail = mode.kind !== 'view' || !!selectedDoc || firstUpload

  const openUpload = (files: File[] = []) =>
    setMode({ kind: 'upload', file: files[0] ?? null, droppedMany: files.length > 1, key: Date.now() })

  /** 목록·개요에서 문서를 고르면 그 문서의 분류로 이동한다 */
  const selectDoc = (id: string, versionId: string | null) => {
    const doc = docs.find((d) => d.id === id)
    if (doc?.categoryId && categories.some((c) => c.id === doc.categoryId)) {
      setFilter({ kind: 'category', id: doc.categoryId })
    }
    setSelectedId(id)
    setSelectedVersionId(versionId)
    setMode({ kind: 'view' })
  }
  /** 분류 지정이 끝나면 그 카테고리 화면으로 옮긴다 */
  const assigned = (doc: Doc) => {
    if (doc.categoryId) setFilter({ kind: 'category', id: doc.categoryId })
    setSelectedId(doc.id)
    setSelectedVersionId(null)
    setMode({ kind: 'view' })
  }
  const backToList = () => {
    setSelectedId(null)
    setMode({ kind: 'view' })
  }

  return (
    <div className={`layout ${filterCategory ? '' : 'board'} ${showDetail ? 'show-detail' : ''}`}>
      <Sidebar filter={filter} onFilterChange={changeFilter} />

      {filterCategory ? (
        <VersionList
          category={filterCategory}
          doc={selectedDoc}
          selectedVersionId={selectedVersionId}
          onSelect={(versionId) => {
            setSelectedId(selectedDoc?.id ?? null)
            setSelectedVersionId(versionId)
            setMode({ kind: 'view' })
          }}
          onOpenResources={() => setMode({ kind: 'resources' })}
          resourceCount={resources.filter((r) => r.categoryId === filterCategory.id).length}
        />
      ) : null}

      <main className="detail">
        {showDetail && (
          <button type="button" className="back-btn" onClick={backToList}>
            <IconBack />
            {listTitle}
          </button>
        )}
        <div className="detail-body" key={mode.kind === 'view' ? (selectedDoc?.id ?? (firstUpload ? `first-${filterCategory!.id}` : 'board')) : mode.kind}>
          {mode.kind === 'resources' && filterCategory ? (
            <ResourcePanel key={filterCategory.id} category={filterCategory} onClose={() => setMode({ kind: 'view' })} />
          ) : mode.kind === 'upload' ? (
            <DocUploader
              key={mode.key}
              initialFile={mode.file}
              droppedMany={mode.droppedMany}
              defaultCategoryId={defaultCategoryId}
              onUploaded={(doc) => {
                if (doc.categoryId) setFilter({ kind: 'category', id: doc.categoryId })
                setSelectedId(doc.id)
                setSelectedVersionId(null)
                setMode({ kind: 'view' })
              }}
              onCancel={() => setMode({ kind: 'view' })}
            />
          ) : firstUpload ? (
            <DocUploader
              key={filterCategory!.id}
              initialFile={null}
              droppedMany={false}
              defaultCategoryId={filterCategory!.id}
              onUploaded={(doc) => {
                if (doc.categoryId) setFilter({ kind: 'category', id: doc.categoryId })
                setSelectedId(doc.id)
                setSelectedVersionId(null)
              }}
            />
          ) : selectedDoc ? (
            <DocView
              doc={selectedDoc}
              versionId={selectedVersionId}
              onVersionChange={setSelectedVersionId}
              onDeleted={() => setSelectedId(null)}
              onAssigned={assigned}
            />
          ) : docs.length === 0 ? (
            <Overview onCreate={openUpload} />
          ) : (
            <StatusBoard docs={visibleDocs} scopeLabel={listTitle} onSelect={selectDoc} onCreate={openUpload} onAssigned={assigned} />
          )}
        </div>
      </main>
    </div>
  )
}
