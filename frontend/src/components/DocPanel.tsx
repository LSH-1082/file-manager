import { useId, useState } from 'react'
import type { FormEvent } from 'react'
import { useData } from '../store/useData'
import type { Doc } from '../types'
import { downloadFile } from '../fileActions'
import { baseName, categoryPath, fileExt, sortCategories } from '../utils'
import {
  baseFile,
  categoryDoc,
  currentFile,
  defaultVersionName,
  DOC_FILE_ACCEPT,
  docFileError,
  fileRevLabel,
  latestVersion,
  storedVersionName,
  versionLabel,
  versionNameError,
  versionWorker,
  WORKER_NAME_MAX,
  workerNameError,
  STAGE_LABEL,
  uploadBlocker,
  VERSION_NAME_MAX,
  versionStage,
} from '../versioning'
import PairPicker from './PairPicker'
import CategoryDialog from './CategoryDialog'
import { Dropzone } from './files'
import { IconClose, IconDownload, IconPlus, IconUpload } from './icons'

// ---------- 공통 입력 ----------

export function CategorySelect({
  value,
  onChange,
  allowNone = true,
}: {
  value: string | null
  onChange: (id: string | null) => void
  /** 미분류 선택 허용 */
  allowNone?: boolean
}) {
  const { categories, docs } = useData()
  const [creating, setCreating] = useState(false)
  const options = sortCategories(categories)

  return (
    <div className="inline">
      <select value={value ?? ''} onChange={(e) => onChange(e.target.value || null)}>
        {allowNone ? (
          <option value="">미분류</option>
        ) : (
          <option value="" disabled>
            분류를 고르세요
          </option>
        )}
        {options.map((c) => {
          const doc = categoryDoc(docs, c.id)
          const last = doc && latestVersion(doc)
          return (
            <option key={c.id} value={c.id}>
              {c.name}
              {last ? ` (${versionLabel(last)} ${STAGE_LABEL[versionStage(last)]})` : ' (문서 없음)'}
            </option>
          )
        })}
      </select>
      <button type="button" className="btn" onClick={() => setCreating(true)}>
        <IconPlus size={14} />
        새 카테고리
      </button>
      {creating && (
        <CategoryDialog
          onClose={() => setCreating(false)}
          onSaved={(c) => onChange(c.id)}
        />
      )}
    </div>
  )
}

// ---------- 업로드 ----------

interface UploaderProps {
  initialFile: File | null
  /** 끌어다 놓은 파일이 여러 개였는지. 첫 파일만 쓴다고 알린다 */
  droppedMany: boolean
  defaultCategoryId: string | null
  onUploaded: (doc: Doc) => void
  /** 없으면 취소 버튼이 없다(문서가 없는 카테고리의 상세 영역에 바로 열린 첫 파일 올리기 화면) */
  onCancel?: () => void
}

/**
 * 파일 하나를 올린다. 카테고리 하나 = 문서 하나이므로
 * - 카테고리에 문서가 없으면 그 카테고리의 문서를 만들고 파일이 v1 원본이 된다(작업자 이름 필수)
 * - 발송 전인 버전이 있으면 그 버전의 수정본이 된다(수정대조표와 작업자 이름 필수)
 * - 최신 버전이 발송됐으면 파일 없이 다음 버전만 시작한다(버전 이름과 작업자 이름. 직전 제출본이 원본)
 */
export function DocUploader({ initialFile, droppedMany, defaultCategoryId, onUploaded, onCancel }: UploaderProps) {
  const { docs, categories, addDoc, uploadWorkingFile, startVersion } = useData()
  const nameHintId = useId()
  const workerHintId = useId()
  const [file, setFile] = useState<File | null>(initialFile)
  const [categoryId, setCategoryId] = useState(defaultCategoryId)
  const [uploading, setUploading] = useState(false)
  const [error, setError] = useState('')
  const [manyNotice, setManyNotice] = useState(droppedMany)
  const [revision, setRevision] = useState<File | null>(null)
  /** 입력한 버전 이름. 아직 입력하지 않았으면 null이고 기본값(v{n})을 보여준다 */
  const [versionNameInput, setVersionNameInput] = useState<string | null>(null)
  /** 입력한 작업자 이름. 아직 입력하지 않았으면 null이고 기본값(이 버전의 작업자)을 보여준다 */
  const [workerInput, setWorkerInput] = useState<string | null>(null)

  const target = categoryDoc(docs, categoryId)
  const latest = target ? latestVersion(target) : undefined
  const category = categories.find((c) => c.id === categoryId)
  // 작업완료 뒤(검토대기~발송대기)에는 수정본을 올릴 수 없다. 작업완료를 취소해야 한다
  const lock = latest && latest.status === 'working' ? uploadBlocker(latest) : null
  // 새 버전(첫 파일이거나 발송된 뒤 시작)이면 버전 이름을 입력받는다. 기본값은 v{n}
  const startsVersion = !latest || latest.status === 'sent'
  // 최신 버전이 발송된 문서: 파일 없이 새 버전만 시작한다
  const newVersionOnly = latest?.status === 'sent'
  const worker = workerInput ?? (latest?.status === 'working' ? (versionWorker(latest) ?? '') : '')
  const nextNumber = latest ? latest.number + 1 : 1
  const defaultName = defaultVersionName(nextNumber)
  const versionName = versionNameInput ?? defaultName
  // 입력 중인 이름이 잘못(5자 초과·중복)이면 안내에는 기본 이름을 쓰고, 오류는 올리기를 누를 때 알린다
  const nameInvalid = startsVersion && versionNameError(target?.versions ?? [], { id: null, number: nextNumber }, versionName) !== null
  const nextLabel = !startsVersion ? versionLabel(latest!) : (!nameInvalid && storedVersionName(nextNumber, versionName)) || defaultName
  const nextRev = !latest ? '원본' : latest.status === 'working' ? fileRevLabel(latest, latest.files.length) : '시작'
  // 기존 문서에 올리면 수정본이므로 수정대조표가 함께 있어야 한다. 대조 기준(원본) 파일 이름
  const pairBase = latest?.status === 'working' ? baseFile(latest).fileName : null

  const submit = async (e: FormEvent) => {
    e.preventDefault()
    if (!categoryId) return setError('문서를 올릴 분류를 고르세요. 분류 하나가 문서 하나입니다.')
    if (!newVersionOnly && !file) return setError('업로드할 파일을 고르세요.')
    if (lock) return setError(lock)
    if (pairBase && !revision) return setError('수정본은 수정대조표와 함께 올려야 등록됩니다.')
    const workerInvalid = workerNameError(worker)
    if (workerInvalid) return setError(workerInvalid)
    if (startsVersion) {
      const invalid = versionNameError(target?.versions ?? [], { id: null, number: nextNumber }, versionName)
      if (invalid) return setError(invalid)
    }
    setUploading(true)
    setError('')
    try {
      let doc: Doc
      if (!target || !latest) {
        // 새 문서의 제목은 카테고리 이름이다
        doc = await addDoc({ title: category?.name || baseName(file!.name), categoryId }, file!, versionName, worker)
      } else if (latest.status === 'working') {
        doc = await uploadWorkingFile(target.id, file!, revision!, worker)
      } else {
        doc = await startVersion(target.id, versionName, worker)
      }
      onUploaded(doc)
    } catch (err) {
      setUploading(false)
      setError(`업로드 실패: ${err instanceof Error ? err.message : String(err)}`)
    }
  }

  return (
    <form className="doc-panel editor" onSubmit={submit}>
      <header className="doc-panel-header">
        <h2>{newVersionOnly ? '새 버전 시작' : '파일 업로드'}</h2>
        <div className="actions">
          {onCancel && (
            <button type="button" className="btn" onClick={onCancel} disabled={uploading}>
              취소
            </button>
          )}
          <button
            type="submit"
            className="btn primary"
            disabled={uploading || (!newVersionOnly && !file) || !categoryId || !!lock || (!!pairBase && !revision) || !worker.trim()}
          >
            {uploading ? (newVersionOnly ? '시작 중…' : '업로드 중…') : newVersionOnly ? '새 버전 시작' : '업로드'}
          </button>
        </div>
      </header>

      <div className="field">
        <span>분류(문서)</span>
        <CategorySelect value={categoryId} onChange={setCategoryId} allowNone={false} />
      </div>

      {categoryId && (
        <div className={`upload-target ${latest ? latest.status : 'new'}`} aria-live="polite">
          <span className="upload-target-version num">{nextLabel}</span>
          <span className="upload-target-text">
            <strong>
              {categoryPath(categories, categoryId)} · {nextLabel} {nextRev}
            </strong>
            <span className="muted">
              {!latest
                ? '이 분류의 첫 파일입니다. 올린 파일이 문서의 원본이 됩니다.'
                : lock && latest.status === 'working'
                  ? lock
                  : latest.status === 'working'
                  ? `발송 전인 버전(${versionLabel(latest)})에 수정본으로 올라갑니다. 지금 파일(${currentFile(latest).fileName})은 이력에 남습니다.`
                  : `${versionLabel(latest)} 제출본을 원본으로 새 버전(${nextLabel})을 만듭니다. 수정본은 만든 뒤 올립니다.`}
            </span>
          </span>
        </div>
      )}

      {categoryId && startsVersion && (
        <label className="field">
          <span>버전 이름</span>
          <input
            value={versionName}
            maxLength={VERSION_NAME_MAX}
            placeholder={defaultName}
            aria-describedby={nameHintId}
            onChange={(e) => {
              setVersionNameInput(e.target.value)
              setError('')
            }}
          />
          <span className="field-hint" id={nameHintId}>
            최대 {VERSION_NAME_MAX}자
          </span>
        </label>
      )}

      {categoryId && (
        <label className="field">
          <span>작업자 이름</span>
          <input
            name="worker"
            aria-label="작업자 이름"
            aria-describedby={workerHintId}
            required
            aria-required="true"
            value={worker}
            maxLength={WORKER_NAME_MAX}
            onChange={(e) => {
              setWorkerInput(e.target.value)
              setError('')
            }}
          />
          <span className="field-hint" id={workerHintId}>
            {newVersionOnly
              ? `필수 · 최대 ${WORKER_NAME_MAX}자 · 수정본 올리기 작업자 칸의 기본값이 됩니다`
              : `필수 · 최대 ${WORKER_NAME_MAX}자 · 올린 사람으로 남고 작업중 표시에 보입니다`}
          </span>
        </label>
      )}

      {newVersionOnly ? (
        initialFile && (
          <p className="hint">새 버전을 먼저 시작해야 수정본을 올릴 수 있어 놓은 파일은 쓰지 않았습니다.</p>
        )
      ) : pairBase ? (
        <PairPicker
          label={nextRev}
          baseName={pairBase}
          file={file}
          revision={revision}
          onFile={(f) => {
            setFile(f)
            setManyNotice(false)
            setError('')
          }}
          onRevision={setRevision}
        />
      ) : file ? (
        // 파일을 고르면 끌어다 놓기 영역은 숨기고 고른 파일 줄만 보인다. ×로 지우면 영역이 다시 나온다
        <ul className="upload-list">
          <li>
            <span className="ext-chip">{fileExt(file.name)}</span>
            <span className="file-name" title={file.name}>
              {file.name}
            </span>
            <button type="button" className="icon-btn small" onClick={() => setFile(null)} disabled={uploading} aria-label={`${file.name} 제거`}>
              <IconClose size={14} />
            </button>
          </li>
        </ul>
      ) : (
        <Dropzone
          multiple={false}
          accept={DOC_FILE_ACCEPT}
          check={docFileError}
          onReject={(m) => {
            setError(m)
            setManyNotice(false)
          }}
          onFiles={([f]) => {
            setFile(f)
            setManyNotice(false)
            setError('')
          }}
        >
          <IconUpload className="dz-icon" size={20} />
          <p>
            <strong>파일을 끌어다 놓거나 클릭해서 고르세요</strong>
          </p>
          <p className="muted">파일은 하나만 올립니다 · .hwpx·.pdf 파일만 올릴 수 있습니다</p>
        </Dropzone>
      )}
      {manyNotice && !newVersionOnly && <p className="hint">파일은 하나만 올릴 수 있어 끌어다 놓은 파일 중 첫 파일만 골랐습니다.</p>}
      {error && <p className="form-error">{error}</p>}

      <TemplateHint categoryId={categoryId} />
    </form>
  )
}

/** 선택한 분류에 등록된 양식을 바로 내려받을 수 있게 보여준다 */
function TemplateHint({ categoryId }: { categoryId: string | null }) {
  const { resources } = useData()
  const templates = resources.filter((r) => r.categoryId === categoryId && r.kind === 'template')
  if (!templates.length) return null
  return (
    <div className="template-hint">
      <span className="muted small">이 분류의 양식</span>
      {templates.map((t) => (
        <button key={t.id} type="button" className="chip-btn" onClick={() => downloadFile(t)} title={`${t.fileName} 다운로드`}>
          <IconDownload size={14} />
          {t.title}
        </button>
      ))}
    </div>
  )
}
