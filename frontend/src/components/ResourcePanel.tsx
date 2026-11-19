import { useState } from 'react'
import type { FormEvent } from 'react'
import { useData } from '../store/useData'
import { RESOURCE_KINDS } from '../types'
import type { Category, CategoryResource, ResourceKind } from '../types'
import { baseName, categoryPath, fileExt, formatDate, hasExtension } from '../utils'
import { downloadFile } from '../fileActions'
import { Dropzone, FilePreview, FileThumb } from './files'
import { IconDownload, IconEdit, IconTrash, IconUpload } from './icons'
import Modal, { ConfirmDialog } from './Modal'

interface Props {
  category: Category
  onClose: () => void
}

/** 빈 섹션 문구의 주어(조사 포함). 표시 문구라서 화면 옆에 둔다 */
const EMPTY_SUBJECT: Record<ResourceKind, string> = {
  template: '양식이',
  reference: '참고문서가',
  etc: '기타 자료가',
}

/** 양식·참고자료는 .exe만 막는다. 서버 ResourceService와 같은 규칙·문구 */
function resourceFileError(file: File): string | null {
  return hasExtension(file.name, ['exe']) ? '.exe 파일은 올릴 수 없습니다.' : null
}

/** 카테고리(문서 분류)별 양식·참고문서 관리. 종류마다 그 섹션에서 바로 올린다 */
export default function ResourcePanel({ category, onClose }: Props) {
  const { categories, resources, addResource } = useData()
  const [uploading, setUploading] = useState<{ kind: ResourceKind; done: number; total: number } | null>(null)
  const [error, setError] = useState<{ kind: ResourceKind; message: string } | null>(null)

  const own = resources.filter((r) => r.categoryId === category.id)

  const upload = async (files: File[], kind: ResourceKind) => {
    setError(null)
    setUploading({ kind, done: 0, total: files.length })
    try {
      for (let i = 0; i < files.length; i++) {
        await addResource(category.id, { kind, title: baseName(files[i].name), note: '' }, files[i])
        setUploading({ kind, done: i + 1, total: files.length })
      }
    } catch (err) {
      setError({ kind, message: `업로드 실패: ${err instanceof Error ? err.message : String(err)}` })
    } finally {
      setUploading(null)
    }
  }

  return (
    <section className="doc-panel">
      <header className="doc-panel-header">
        <div>
          {/* 머리에는 단계색 점을 두지 않는다(점은 사이드바·문서 상세 머리에만) */}
          <div className="breadcrumb">{categoryPath(categories, category.id)}</div>
          <h2 className="doc-title">양식·참고자료</h2>
          <div className="doc-dates">이 분류의 문서를 작성할 때 필요한 양식과 참고문서를 보관합니다.</div>
        </div>
        <div className="actions">
          <button type="button" className="btn resource-close" onClick={onClose}>
            닫기
          </button>
        </div>
      </header>

      {RESOURCE_KINDS.map(({ kind, label, hint }) => {
        const items = own
          .filter((r) => r.kind === kind)
          .sort((a, b) => a.title.localeCompare(b.title, 'ko'))
        const busy = uploading?.kind === kind ? uploading : null
        const busyText = busy && `업로드 중… (${busy.done}/${busy.total})`
        return (
          <div key={kind} className="resource-section">
            <div className="resource-section-head">
              <h3>
                {label} <span className="count num">{items.length}건</span>
              </h3>
              {items.length > 0 && (
                <Dropzone
                  multiple
                  onFiles={(files) => upload(files, kind)}
                  check={resourceFileError}
                  onReject={(message) => setError({ kind, message })}
                  className="btn small dz-btn"
                >
                  <IconUpload size={14} />
                  {busyText ?? `${label} 올리기`}
                </Dropzone>
              )}
            </div>
            {items.length === 0 ? (
              <Dropzone
                multiple
                onFiles={(files) => upload(files, kind)}
                check={resourceFileError}
                onReject={(message) => setError({ kind, message })}
                className="dropzone resource-drop"
              >
                <IconUpload className="dz-icon" size={16} />
                <span className="resource-drop-text">
                  {busyText ?? `${EMPTY_SUBJECT[kind]} 없습니다. 파일을 끌어다 놓거나 클릭해서 올리세요.`}
                  <span className="hint">{hint}</span>
                </span>
              </Dropzone>
            ) : (
              <ul className="resource-list">
                {items.map((r) => (
                  <ResourceItem key={r.id} resource={r} />
                ))}
              </ul>
            )}
            {error?.kind === kind && <p className="form-error">{error.message}</p>}
          </div>
        )
      })}
    </section>
  )
}

function ResourceItem({ resource: r }: { resource: CategoryResource }) {
  const { removeResource } = useData()
  const [previewing, setPreviewing] = useState(false)
  const [editing, setEditing] = useState(false)
  const [deleting, setDeleting] = useState(false)

  return (
    <li className="resource-item">
      <div className="resource-row">
        <FileThumb file={r} />
        <div className="file-info">
          <div className="file-name" title={r.title}>
            {r.title}
          </div>
          <div className="meta ellipsis num" title={r.fileName}>
            {r.fileName} · {fileExt(r.fileName)} · {formatDate(r.updatedAt)}
          </div>
          {r.note && <div className="resource-note">{r.note}</div>}
        </div>
        <div className="actions">
          <button type="button" className="btn small" aria-expanded={previewing} onClick={() => setPreviewing((v) => !v)}>
            {previewing ? '접기' : '미리보기'}
          </button>
          <button type="button" className="btn small" onClick={() => downloadFile(r)}>
            <IconDownload size={14} />
            다운로드
          </button>
          <button type="button" className="icon-btn" title="수정" aria-label={`${r.title} 수정`} onClick={() => setEditing(true)}>
            <IconEdit size={14} />
          </button>
          <button type="button" className="icon-btn" title="삭제" aria-label={`${r.title} 삭제`} onClick={() => setDeleting(true)}>
            <IconTrash size={14} />
          </button>
        </div>
      </div>

      {previewing && <FilePreview key={r.updatedAt} doc={r} />}

      {editing && <ResourceDialog resource={r} onClose={() => setEditing(false)} />}
      {deleting && (
        <ConfirmDialog
          title="자료 삭제"
          message={
            <>
              <strong>{r.title}</strong> 자료를 삭제할까요? 되돌릴 수 없습니다.
            </>
          }
          onConfirm={() => removeResource(r.id)}
          onClose={() => setDeleting(false)}
        />
      )}
    </li>
  )
}

function ResourceDialog({ resource, onClose }: { resource: CategoryResource; onClose: () => void }) {
  const { editResource } = useData()
  const [title, setTitle] = useState(resource.title)
  const [kind, setKind] = useState(resource.kind)
  const [note, setNote] = useState(resource.note)
  const [replacement, setReplacement] = useState<File | null>(null)
  const [error, setError] = useState('')

  const submit = async (e: FormEvent) => {
    e.preventDefault()
    e.stopPropagation()
    const fileName = replacement?.name ?? resource.fileName
    try {
      await editResource(
        resource.id,
        { kind, title: title.trim() || baseName(fileName), note: note.trim() },
        replacement ?? undefined,
      )
      onClose()
    } catch (err) {
      setError(`저장 실패: ${err instanceof Error ? err.message : String(err)}`)
    }
  }

  return (
    <Modal
      title="자료 수정"
      onClose={onClose}
      footer={
        <>
          <button type="button" className="btn" onClick={onClose}>
            취소
          </button>
          <button type="submit" form="resource-form" className="btn primary">
            저장
          </button>
        </>
      }
    >
      <form id="resource-form" className="form" onSubmit={submit}>
        <label className="field">
          <span>제목</span>
          <input autoFocus value={title} maxLength={200} onChange={(e) => setTitle(e.target.value)} />
        </label>
        <label className="field">
          <span>종류</span>
          <select value={kind} onChange={(e) => setKind(e.target.value as ResourceKind)}>
            {RESOURCE_KINDS.map((k) => (
              <option key={k.kind} value={k.kind}>
                {k.label}
              </option>
            ))}
          </select>
        </label>
        <label className="field">
          <span>메모</span>
          <textarea
            rows={3}
            value={note}
            maxLength={500}
            onChange={(e) => setNote(e.target.value)}
            placeholder="예: 2026년 개정판, 갑/을 서명란 필수"
          />
        </label>
        <div className="field">
          <span>파일</span>
          <div className="file-card">
            <div className="file-info">
              <div className="file-name">{replacement?.name ?? resource.fileName}</div>
              <div className="muted small">
                {fileExt(replacement?.name ?? resource.fileName)}
                {replacement && ' · 저장 시 교체됨'}
              </div>
            </div>
            {replacement ? (
              <button type="button" className="btn small" onClick={() => setReplacement(null)}>
                교체 취소
              </button>
            ) : (
              <Dropzone
                multiple={false}
                onFiles={([f]) => {
                  setError('')
                  setReplacement(f)
                }}
                check={resourceFileError}
                onReject={setError}
                className="dropzone-inline"
              >
                <span className="btn small">파일 교체</span>
              </Dropzone>
            )}
          </div>
        </div>
        {error && <p className="form-error">{error}</p>}
      </form>
    </Modal>
  )
}
