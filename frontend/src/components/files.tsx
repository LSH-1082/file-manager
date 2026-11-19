import { useEffect, useRef, useState } from 'react'
import type { DragEvent, ReactNode } from 'react'
import { getFile } from '../api/storage'
import type { StoredFile } from '../fileActions'
import { fileExt } from '../utils'

const TEXT_PREVIEW_LIMIT = 200 * 1024

function isTextFile(doc: StoredFile) {
  return (
    doc.mimeType.startsWith('text/') ||
    ['application/json', 'application/xml'].includes(doc.mimeType) ||
    ['TXT', 'MD', 'CSV', 'JSON', 'XML', 'LOG'].includes(fileExt(doc.fileName))
  )
}

type PreviewState =
  | { kind: 'loading' }
  | { kind: 'missing' }
  | { kind: 'url'; url: string }
  | { kind: 'text'; text: string; truncated: boolean }

export function FilePreview({ doc }: { doc: StoredFile }) {
  const [state, setState] = useState<PreviewState>({ kind: 'loading' })

  useEffect(() => {
    let cancelled = false
    let url: string | null = null
    getFile(doc.id).then(async (blob) => {
      if (cancelled) return
      if (!blob) return setState({ kind: 'missing' })
      if (isTextFile(doc)) {
        const text = await blob.slice(0, TEXT_PREVIEW_LIMIT).text()
        if (!cancelled) setState({ kind: 'text', text, truncated: blob.size > TEXT_PREVIEW_LIMIT })
        return
      }
      url = URL.createObjectURL(blob)
      setState({ kind: 'url', url })
    })
    return () => {
      cancelled = true
      if (url) URL.revokeObjectURL(url)
    }
  }, [doc])

  if (state.kind === 'loading') return <div className="preview muted">미리보기 불러오는 중…</div>
  if (state.kind === 'missing') return <div className="preview muted">저장된 파일을 찾을 수 없습니다.</div>
  if (state.kind === 'text') {
    return (
      <div className="preview">
        <pre className="preview-text">{state.text}</pre>
        {state.truncated && <p className="muted">앞부분 200KB만 표시합니다.</p>}
      </div>
    )
  }

  const { mimeType } = doc
  if (mimeType.startsWith('image/')) {
    return (
      <div className="preview">
        <img className="preview-media" src={state.url} alt={doc.fileName} />
      </div>
    )
  }
  if (mimeType === 'application/pdf') {
    return <iframe className="preview preview-frame" src={state.url} title={doc.fileName} />
  }
  if (mimeType.startsWith('video/')) {
    return (
      <div className="preview">
        <video className="preview-media" src={state.url} controls />
      </div>
    )
  }
  if (mimeType.startsWith('audio/')) {
    return (
      <div className="preview">
        <audio src={state.url} controls />
      </div>
    )
  }
  return <div className="preview muted">이 형식은 미리보기를 지원하지 않습니다. 다운로드해서 확인하세요.</div>
}

type ThumbState =
  | { kind: 'plate' }
  | { kind: 'image'; url: string }
  | { kind: 'text'; text: string }

/**
 * 파일의 첫 페이지 축소본. 텍스트 파일은 실제 첫 줄들을 종이 위에 그리고, 이미지는 그 이미지를,
 * 나머지 형식은 확장자 표식을 보여준다. 읽기 전용이며 저장소를 바꾸지 않는다.
 */
export function FileThumb({ file, size = 'sm' }: { file: StoredFile; size?: 'sm' | 'md' }) {
  const [state, setState] = useState<ThumbState>({ kind: 'plate' })
  const canvasRef = useRef<HTMLCanvasElement>(null)

  useEffect(() => {
    let cancelled = false
    let url: string | null = null
    getFile(file.id)
      .then(async (blob) => {
        if (cancelled || !blob) return
        if (file.mimeType.startsWith('image/')) {
          url = URL.createObjectURL(blob)
          setState({ kind: 'image', url })
        } else if (isTextFile(file)) {
          const text = await blob.slice(0, 4096).text()
          if (!cancelled) setState({ kind: 'text', text })
        }
      })
      .catch(() => {})
    return () => {
      cancelled = true
      if (url) URL.revokeObjectURL(url)
    }
  }, [file])

  useEffect(() => {
    if (state.kind !== 'text') return
    const canvas = canvasRef.current
    if (!canvas) return
    let cancelled = false
    const draw = () => {
      if (cancelled) return
      const ratio = window.devicePixelRatio || 1
      const { width, height } = canvas.getBoundingClientRect()
      if (!width || !height) return
      canvas.width = Math.round(width * ratio)
      canvas.height = Math.round(height * ratio)
      const ctx = canvas.getContext('2d')
      if (!ctx) return
      ctx.scale(ratio, ratio)
      const styles = getComputedStyle(canvas)
      ctx.fillStyle = styles.getPropertyValue('--paper-ink').trim() || '#2a2a2a'
      const pad = width * 0.12
      const lineSize = Math.max(2.4, width / 26)
      const lead = lineSize * 1.7
      const lines = state.text
        .replace(/\r/g, '')
        .split('\n')
        .map((l) => l.replace(/^#+\s*/, ''))
        .map((l) => (fileExt(file.fileName) === 'CSV' ? l.replace(/,/g, '   ') : l))
      let y = pad + lineSize
      const maxWidth = width - pad * 2
      for (let i = 0; i < lines.length && y < height - pad * 0.6; i++) {
        const first = i === 0
        ctx.font = `${first ? 600 : 400} ${first ? lineSize * 1.35 : lineSize}px "Pretendard Variable", Pretendard, sans-serif`
        let line = lines[i]
        if (!line.trim()) {
          y += lead * 0.6
          continue
        }
        // 한 줄을 종이 폭에 맞춰 자른다 (축소본이므로 줄바꿈 대신 다음 줄로 넘긴다)
        while (line && y < height - pad * 0.6) {
          let cut = line.length
          while (cut > 1 && ctx.measureText(line.slice(0, cut)).width > maxWidth) cut--
          ctx.fillText(line.slice(0, cut), pad, y)
          line = line.slice(cut)
          y += first ? lead * 1.3 : lead
        }
      }
    }
    document.fonts.ready.then(draw)
    return () => {
      cancelled = true
    }
  }, [state, file])

  return (
    <span className={`thumb thumb-${size}`} data-kind={state.kind}>
      {state.kind === 'image' ? (
        <img src={state.url} alt="" />
      ) : state.kind === 'text' ? (
        <canvas ref={canvasRef} aria-hidden="true" />
      ) : (
        <span className="thumb-ext" aria-hidden="true">
          {fileExt(file.fileName)}
        </span>
      )}
    </span>
  )
}

export function Dropzone({
  multiple,
  onFiles,
  accept,
  check,
  onReject,
  children,
  className = 'dropzone',
}: {
  multiple: boolean
  /** 통과한 파일만 넘긴다. 거절이 있으면 이 호출 뒤에 onReject가 불린다 */
  onFiles: (files: File[]) => void
  /** 선택 창 필터(input accept) */
  accept?: string
  /** 파일별 검사. 거절 문구를 돌려주고 통과면 null */
  check?: (file: File) => string | null
  /** 거절된 파일이 있으면 첫 거절 문구로 onFiles 다음에 부른다 */
  onReject?: (message: string) => void
  children: ReactNode
  className?: string
}) {
  const inputRef = useRef<HTMLInputElement>(null)
  const [over, setOver] = useState(false)

  const take = (list: File[]) => {
    const picked = multiple ? list : list.slice(0, 1)
    if (!picked.length) return
    if (!check) {
      onFiles(picked)
      return
    }
    const ok = picked.filter((f) => !check(f))
    const firstError = picked.map(check).find(Boolean)
    if (ok.length) onFiles(ok)
    if (firstError) onReject?.(firstError)
  }

  const onDrop = (e: DragEvent) => {
    e.preventDefault()
    setOver(false)
    take(Array.from(e.dataTransfer.files))
  }

  return (
    <div
      className={`${className} ${over ? 'over' : ''}`}
      role="button"
      tabIndex={0}
      onClick={() => inputRef.current?.click()}
      onKeyDown={(e) => {
        if (e.key === 'Enter' || e.key === ' ') {
          e.preventDefault()
          inputRef.current?.click()
        }
      }}
      onDragOver={(e) => {
        e.preventDefault()
        setOver(true)
      }}
      onDragLeave={() => setOver(false)}
      onDrop={onDrop}
    >
      {children}
      <input
        ref={inputRef}
        type="file"
        multiple={multiple}
        accept={accept}
        hidden
        onChange={(e) => {
          take(Array.from(e.target.files ?? []))
          e.target.value = '' // 같은 파일을 다시 고를 수 있도록
        }}
      />
    </div>
  )
}
