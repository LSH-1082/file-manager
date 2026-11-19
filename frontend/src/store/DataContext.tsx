import { useCallback, useEffect, useMemo, useState } from 'react'
import type { ReactNode } from 'react'
import * as api from '../api/storage'
import type { Category, CategoryResource, Doc } from '../types'
import { DataContext } from './useData'
import type { DataContextValue } from './useData'

export function DataProvider({ children }: { children: ReactNode }) {
  const [categories, setCategories] = useState<Category[]>([])
  const [docs, setDocs] = useState<Doc[]>([])
  const [resources, setResources] = useState<CategoryResource[]>([])
  const [loading, setLoading] = useState(true)

  /** 문서 하나를 바꾸는 API 호출 후 상태 반영 */
  const applyDoc = useCallback(async (request: Promise<Doc>) => {
    const updated = await request
    setDocs((prev) => prev.map((d) => (d.id === updated.id ? updated : d)))
    return updated
  }, [])

  const reload = useCallback(async () => {
    const [c, d, r] = await Promise.all([api.listCategories(), api.listDocs(), api.listResources()])
    setCategories(c)
    setDocs(d)
    setResources(r)
  }, [])

  useEffect(() => {
    const load = async () => {
      try {
        await reload()
      } finally {
        setLoading(false)
      }
    }
    load()
  }, [reload])

  const value = useMemo<DataContextValue>(
    () => ({
      categories,
      docs,
      resources,
      loading,
      addCategory: async (input) => {
        const created = await api.createCategory(input)
        setCategories((prev) => [...prev, created])
        return created
      },
      editCategory: async (id, input) => {
        await api.updateCategory(id, input)
        // 문서 제목도 함께 바뀌므로 전체를 다시 읽는다
        await reload()
      },
      removeCategory: async (id) => {
        await api.deleteCategory(id)
        // 문서 분류 해제, 자료 삭제가 함께 일어나므로 전체를 다시 읽는다
        await reload()
      },
      addDoc: async (input, file, versionName, worker) => {
        const created = await api.createDoc(input, file, versionName, worker)
        setDocs((prev) => [...prev, created])
        return created
      },
      uploadWorkingFile: (id, file, revision, worker) => applyDoc(api.uploadWorkingFile(id, file, revision, worker)),
      deleteLatestRevision: (id, fileId) => applyDoc(api.deleteLatestRevision(id, fileId)),
      markWorkDone: (id) => applyDoc(api.markWorkDone(id)),
      startReview: (id, reviewers) => applyDoc(api.startReview(id, reviewers)),
      decideReview: (id, decision, reject) => applyDoc(api.decideReview(id, decision, reject)),
      sendVersion: (id, info) => applyDoc(api.sendVersion(id, info)),
      cancelStep: (id, step) => applyDoc(api.cancelStep(id, step)),
      renameVersion: (id, versionId, name) => applyDoc(api.renameVersion(id, versionId, name)),
      startVersion: (id, versionName, worker) => applyDoc(api.startVersion(id, versionName, worker)),
      assignCategory: async (id, target) => {
        const updated = await api.assignCategory(id, target)
        // 새 카테고리가 생길 수 있어 전체를 다시 읽는다
        await reload()
        return updated
      },
      removeVersion: async (id, versionId) => {
        const updated = await api.deleteVersion(id, versionId)
        setDocs((prev) => (updated ? prev.map((d) => (d.id === id ? updated : d)) : prev.filter((d) => d.id !== id)))
        return updated
      },
      addResource: async (categoryId, input, file) => {
        const created = await api.createResource(categoryId, input, file)
        setResources((prev) => [...prev, created])
        return created
      },
      editResource: async (id, input, file) => {
        const updated = await api.updateResource(id, input, file)
        setResources((prev) => prev.map((r) => (r.id === id ? updated : r)))
        return updated
      },
      removeResource: async (id) => {
        await api.deleteResource(id)
        setResources((prev) => prev.filter((r) => r.id !== id))
      },
    }),
    [categories, docs, resources, loading, reload, applyDoc],
  )

  return <DataContext.Provider value={value}>{children}</DataContext.Provider>
}
