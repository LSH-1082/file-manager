import type { Category, Doc } from './types'

/** 카테고리는 한 단계다. 이름순으로 정렬한 목록 */
export function sortCategories(categories: Category[]): Category[] {
  return [...categories].sort((a, b) => a.name.localeCompare(b.name, 'ko'))
}

/** 카테고리 이름. 없으면 빈 문자열 */
export function categoryPath(categories: Category[], id: string | null): string {
  return categories.find((c) => c.id === id)?.name ?? ''
}

/** 문서 제목은 카테고리 이름이다. 카테고리가 없는(미분류) 문서만 저장된 제목을 쓴다 */
export function docTitle(categories: Category[], doc: Doc): string {
  return categories.find((c) => c.id === doc.categoryId)?.name ?? doc.title
}

/** 카테고리가 없거나 지워진 문서(미분류) */
export function isUncategorized(categories: Category[], doc: Doc): boolean {
  return !categories.some((c) => c.id === doc.categoryId)
}

/** 숨긴 카테고리의 문서. 미분류 문서는 숨길 수 없다 */
export function isHiddenDoc(categories: Category[], doc: Doc): boolean {
  return categories.some((c) => c.id === doc.categoryId && c.hidden)
}

export function formatDate(iso: string) {
  return new Date(iso).toLocaleString('ko-KR', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
    hourCycle: 'h23',
  })
}

export function formatSize(bytes: number) {
  if (bytes < 1024) return `${bytes} B`
  const units = ['KB', 'MB', 'GB']
  let value = bytes / 1024
  let i = 0
  while (value >= 1024 && i < units.length - 1) {
    value /= 1024
    i++
  }
  return `${value.toFixed(value < 10 ? 1 : 0)} ${units[i]}`
}

/** 확장자 (대문자, 점 제외). 없으면 'FILE' */
export function fileExt(fileName: string) {
  const dot = fileName.lastIndexOf('.')
  return dot > 0 ? fileName.slice(dot + 1).toUpperCase() : 'FILE'
}

/**
 * 파일 이름의 확장자가 extensions(점 없는 소문자)에 드는지.
 * 서버 StoredFileService.hasExtension과 같은 판정(마지막 . 뒤, 대소문자 무시, .이 없으면 확장자 없음).
 * 판정은 여기 한 곳에만 둔다. fileExt는 표시용이라 기준이 다르다.
 */
export function hasExtension(fileName: string, extensions: readonly string[]): boolean {
  const name = fileName.slice(Math.max(fileName.lastIndexOf('/'), fileName.lastIndexOf('\\')) + 1)
  const dot = name.lastIndexOf('.')
  if (dot < 0) return false
  return extensions.includes(name.slice(dot + 1).toLowerCase())
}

/** 파일명에서 확장자를 뺀 이름 (문서 제목 기본값) */
export function baseName(fileName: string) {
  const dot = fileName.lastIndexOf('.')
  return dot > 0 ? fileName.slice(0, dot) : fileName
}
