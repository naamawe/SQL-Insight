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

  assign: (userId: number, dataSourceIds: number[]) =>
    http.post<void>(`/user-authorizations/${userId}`, dataSourceIds),
}
