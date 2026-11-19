import { useState } from 'react'
import { DOC_FILE_ACCEPT, docFileError } from '../versioning'
import { Dropzone } from './files'
import { IconCheck, IconUpload } from './icons'

interface Props {
  /** 이 수정본의 이름표. 예: 수정본 2 */
  label: string
  /** 대조 기준인 원본 파일 이름 */
  baseName: string
  file: File | null
  revision: File | null
  onFile: (file: File) => void
  onRevision: (file: File) => void
}

/**
 * 수정본과 그 수정대조표를 한 쌍으로 고른다. 둘 다 있어야 등록된다.
 * 왼쪽이 수정본, 오른쪽이 그 수정본의 원본 대비 대조표.
 */
export default function PairPicker({ label, baseName, file, revision, onFile, onRevision }: Props) {
  const [rejected, setRejected] = useState<string | null>(null)
  return (
    <div className="pair-picker">
      <p className="pair-picker-base">
        원본 <strong className="ellipsis">{baseName}</strong> 대비
      </p>
      <div className="pair-picker-slots">
        <FileSlot
          title={`${label} 파일`}
          file={file}
          onFile={(f) => {
            setRejected(null)
            onFile(f)
          }}
          onReject={setRejected}
        />
        <FileSlot
          title={`${label}의 수정대조표`}
          file={revision}
          onFile={(f) => {
            setRejected(null)
            onRevision(f)
          }}
          onReject={setRejected}
        />
      </div>
      {rejected && <p className="form-error">{rejected}</p>}
      <p className="hint">수정본은 수정대조표와 함께 올려야 등록됩니다. 두 파일 모두 .hwpx·.pdf만 올릴 수 있습니다.</p>
    </div>
  )
}

interface FileSlotProps {
  title: string
  file: File | null
  onFile: (f: File) => void
  onReject: (message: string) => void
}

/** 파일 한 개를 고르는 칸. 수정본·대조표·거절 검토본에 같이 쓴다(.hwpx/.pdf만) */
export function FileSlot({ title, file, onFile, onReject }: FileSlotProps) {
  return (
    <Dropzone
      multiple={false}
      accept={DOC_FILE_ACCEPT}
      check={docFileError}
      onReject={onReject}
      onFiles={([f]) => onFile(f)}
      className={`dropzone pair-slot ${file ? 'filled' : ''}`}
    >
      {/* 체크는 제목 흐름 밖(칸 오른쪽 위)에 두어, 파일을 골라도 제목·파일명의 왼쪽 선이 움직이지 않는다 */}
      <span className="pair-slot-title">{title}</span>
      {file && (
        <span className="pair-slot-check">
          <IconCheck size={14} />
        </span>
      )}
      {file ? (
        <span className="pair-slot-file">
          <strong className="ellipsis" title={file.name}>
            {file.name}
          </strong>
          <span className="muted">클릭해서 바꾸기</span>
        </span>
      ) : (
        <span className="pair-slot-file muted">
          <IconUpload size={16} />
          끌어다 놓거나 클릭해서 고르세요
        </span>
      )}
    </Dropzone>
  )
}
