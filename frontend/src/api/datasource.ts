import http from '@/utils/http'
import type { DataSourceVO, DataSourceSaveDTO, PageResult } from '@/types'

export interface DataSourceUpdateDTO extends DataSourceSaveDTO {
  id: number
}

export const dataSourceApi = {
  page: (current: number, size: number, connName?: string) =>
    http.get<PageResult<DataSourceVO>>('/data-sources/admin/page', {
      params: { current, size, connName },
    }),

  list: () => http.get<DataSourceVO[]>('/data-sources/admin/list'),

  myList: () => http.get<DataSourceVO[]>('/data-sources/my'),

  save: (dto: DataSourceSaveDTO) =>
    http.post<void>('/data-sources/admin', dto),

  update: (dto: DataSourceUpdateDTO) =>
    http.put<void>('/data-sources/admin', dto),

  remove: (id: number) =>
    http.delete<void>(`/data-sources/admin/${id}`),

  batchRemove: (ids: number[]) =>
    http.delete<void>('/data-sources/admin/batch', { data: ids }),

  test: (dto: DataSourceSaveDTO) =>
    http.post<string>('/data-sources/admin/test', dto),

  getTables: (id: number) =>
    http.get<string[]>(`/data-sources/admin/${id}/tables`),

  refreshTables: (id: number) =>
    http.post<string[]>(`/data-sources/admin/${id}/tables/refresh`),
}
