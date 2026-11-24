import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import type { ReactNode } from 'react'
import * as api from '../api/storage'
import type { Category, CategoryResource, Doc, Schedule, ScheduleGroup } from '../types'
import { DataContext } from './useData'
import type { DataContextValue } from './useData'

/** 오류의 문구. 비어 있으면 기본 문구(빈 문자열은 화면에서 '실패 없음'이다) */
function reasonOf(err: unknown): string {
  const reason = err instanceof Error ? err.message : String(err)
  return reason.trim() || '서버 오류가 발생했습니다.'
}

export function DataProvider({ children }: { children: ReactNode }) {
  const [categories, setCategories] = useState<Category[]>([])
  const [docs, setDocs] = useState<Doc[]>([])
  const [resources, setResources] = useState<CategoryResource[]>([])
  const [loading, setLoading] = useState(true)
  const [schedules, setSchedules] = useState<Schedule[]>([])
  const [scheduleGroups, setScheduleGroups] = useState<ScheduleGroup[]>([])
  const [scheduleError, setScheduleError] = useState('')
  const [scheduleLoading, setScheduleLoading] = useState(false)
  /** seq = 마지막으로 시작한 발송 문서·일정 조회 번호, loading = 그 조회가 진행 중, fresh = 마지막 조회가 성공했고 그 뒤 새 조회가 시작되지 않음 */
  const scheduleSync = useRef({ seq: 0, loading: false, fresh: false })

  /** 문서 하나를 바꾸는 API 호출 후 상태 반영 */
  const applyDoc = useCallback(async (request: Promise<Doc>) => {
    const updated = await request
    setDocs((prev) => prev.map((d) => (d.id === updated.id ? updated : d)))
    return updated
  }, [])

  /**
   * 발송 문서와 일정 목록만 함께 읽는다. 던지지 않는다. 실패해도 마지막으로 읽은 두 목록과 실패 안내를 비우지 않는다.
   * 하나만 바꾸면 소속이 엇갈린 화면이 되므로 둘 다 성공했을 때만 함께 바꾼다
   */
  const loadSchedules = useCallback(async () => {
    const sync = scheduleSync.current
    const seq = ++sync.seq
    sync.loading = true
    sync.fresh = false
    setScheduleLoading(true)
    try {
      const [list, groups] = await Promise.all([api.listSchedules(), api.listScheduleGroups()])
      if (seq !== sync.seq) return // 늦게 온 이전 응답은 버린다
      setSchedules(list)
      setScheduleGroups(groups)
      setScheduleError('')
      sync.fresh = true
    } catch (err) {
      if (seq !== sync.seq) return
      setScheduleError(reasonOf(err))
    }
    sync.loading = false
    setScheduleLoading(false)
  }, [])

  const reload = useCallback(async () => {
    const [c, d, r] = await Promise.all([api.listCategories(), api.listDocs(), api.listResources()])
    setCategories(c)
    setDocs(d)
    setResources(r)
    // 발송 문서와 일정은 따로 읽는다. 그 조회가 실패해도 기존 세 목록(화면)과 마지막으로 읽은 두 목록은 비우지 않는다
    await loadSchedules()
  }, [loadSchedules])

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
      setCategoryHidden: async (id, hidden) => {
        const updated = await api.setCategoryHidden(id, hidden)
        setCategories((prev) => prev.map((c) => (c.id === id ? updated : c)))
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
        // 문서가 지워지면 서버가 그 문서의 발송 문서도 지운다(ON DELETE CASCADE)
        if (!updated) setSchedules((prev) => prev.filter((s) => s.documentId !== id))
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
      schedules,
      scheduleGroups,
      scheduleError,
      scheduleLoading,
      reloadSchedules: async () => {
        if (!scheduleSync.current.loading) await loadSchedules()
      },
      addSchedule: async (input) => {
        const created = await api.createSchedule(input)
        // 조회 응답이 추가 응답보다 먼저 왔으면 이미 들어 있으므로 빼고 더한다
        setSchedules((prev) => [...prev.filter((s) => s.id !== created.id), created])
        // 실패한 상태거나 조회가 진행 중이면 다시 읽어, 그 항목만 보이거나 옛 응답에 덮이지 않게 한다.
        // 기다리지 않는다: 저장은 이미 성공했고, 다시 읽기 실패는 안내로만 남는다
        if (!scheduleSync.current.fresh) void loadSchedules()
        return created
      },
      editSchedule: async (id, input) => {
        const updated = await api.updateSchedule(id, input)
        setSchedules((prev) => prev.map((s) => (s.id === id ? updated : s)))
        if (!scheduleSync.current.fresh) void loadSchedules()
        return updated
      },
      removeSchedule: async (id) => {
        await api.deleteSchedule(id)
        setSchedules((prev) => prev.filter((s) => s.id !== id))
        if (!scheduleSync.current.fresh) void loadSchedules()
      },
      addScheduleGroup: async (input) => {
        const created = await api.createScheduleGroup(input)
        setScheduleGroups((prev) => [...prev.filter((g) => g.id !== created.id), created])
        if (!scheduleSync.current.fresh) void loadSchedules()
        return created
      },
      editScheduleGroup: async (id, input) => {
        const updated = await api.updateScheduleGroup(id, input)
        setScheduleGroups((prev) => prev.map((g) => (g.id === id ? updated : g)))
        if (!scheduleSync.current.fresh) void loadSchedules()
        return updated
      },
      removeScheduleGroup: async (id) => {
        await api.deleteScheduleGroup(id)
        setScheduleGroups((prev) => prev.filter((g) => g.id !== id))
        // 서버는 소속 발송 문서를 지우지 않고 groupId만 없앤다(ON DELETE SET NULL)
        setSchedules((prev) =>
          prev.map((s) => {
            if (s.groupId !== id) return s
            const rest = { ...s }
            delete rest.groupId
            return rest
          }),
        )
        if (!scheduleSync.current.fresh) void loadSchedules()
      },
    }),
    [
      categories, docs, resources, loading, schedules, scheduleGroups, scheduleError, scheduleLoading,
      reload, loadSchedules, applyDoc,
    ],
  )

  return <DataContext.Provider value={value}>{children}</DataContext.Provider>
}
