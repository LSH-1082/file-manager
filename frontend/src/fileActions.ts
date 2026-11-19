import { getFile } from './api/storage'

/** 파일 본문이 저장소에 있는 항목 (문서, 카테고리 자료 공통) */
export interface StoredFile {
  id: string
  fileName: string
  /** 내려받을 때의 이름. 없으면 fileName */
  downloadName?: string
  mimeType: string
}

export async function downloadFile(item: StoredFile) {
  const blob = await getFile(item.id)
  if (!blob) return alert('저장된 파일을 찾을 수 없습니다.')
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = item.downloadName ?? item.fileName
  a.click()
  setTimeout(() => URL.revokeObjectURL(url), 1000)
}
