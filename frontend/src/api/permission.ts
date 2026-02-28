import http from '@/utils/http'
import type { QueryPolicy } from '@/types'

export const queryPolicyApi = {
  getByRoleId: (roleId: number) =>
    http.get<QueryPolicy>('/query-policies', { params: { roleId } }),

  save: (policy: QueryPolicy) =>
    http.post<void>('/query-policies', policy),

  remove: (roleId: number) =>
    http.delete<void>('/query-policies', { params: { roleId } }),
}

export const userAuthApi = {
  getAuthorizedIds: (userId: number) =>
    http.get<number[]>(`/user-authorizations/${userId}`),

  getMyAuthorizedIds: () =>
    http.get<number[]>('/user-authorizations/me'),

  assign: (userId: number, dataSourceIds: number[]) =>
    http.post<void>(`/user-authorizations/${userId}`, dataSourceIds),
}

export const rolePermissionApi = {
  getTables: (roleId: number, dataSourceId: number) =>
    http.get<string[]>('/role-permissions/tables', { params: { roleId, dataSourceId } }),

  getSummary: (roleId: number) =>
    http.get<Record<number, string[]>>('/role-permissions/summary', { params: { roleId } }),

  mySummary: () =>
    http.get<Record<number, string[]>>('/role-permissions/my-summary'),

  assign: (roleId: number, dataSourceId: number, tableNames: string[]) =>
    http.post<void>('/role-permissions', { roleId, dataSourceId, tableNames }),

  remove: (roleId: number, dataSourceId: number) =>
    http.delete<void>('/role-permissions', { params: { roleId, dataSourceId } }),
}
