import http from '@/utils/http'
import type { UserVO, Role, PageResult } from '@/types'

export interface UserSaveDTO {
  userName: string
  password: string
  roleId: number
}

export interface UserUpdateDTO {
  id: number
  roleId: number
  status: number
}

export interface UserPasswordUpdateDTO {
  oldPassword: string
  newPassword: string
}

export const userApi = {
  me: () => http.get<UserVO>('/users/me'),

  page: (current: number, size: number, username?: string) =>
    http.get<PageResult<UserVO>>('/users/page', { params: { current, size, username } }),

  save: (dto: UserSaveDTO) => http.post<void>('/users', dto),

  update: (dto: UserUpdateDTO) => http.put<void>('/users', dto),

  remove: (id: number) => http.delete<void>(`/users/${id}`),

  resetPassword: (id: number) => http.put<void>(`/users/${id}/password/reset`),

  updatePassword: (dto: UserPasswordUpdateDTO) => http.put<void>('/users/password', dto),

  updateSystemPermission: (id: number, systemPermission: string) =>
    http.put<void>(`/users/${id}/system-permission`, null, { params: { systemPermission } }),
}

export const roleApi = {
  list: () => http.get<Role[]>('/roles/list'),

  page: (current: number, size: number, roleName?: string) =>
    http.get<PageResult<Role>>('/roles/page', { params: { current, size, roleName } }),

  save: (dto: { roleName: string; description: string }) =>
    http.post<number>('/roles', dto),

  update: (dto: { id: number; roleName: string; description: string }) =>
    http.put<void>('/roles', dto),

  remove: (id: number) => http.delete<void>(`/roles/${id}`),
}

