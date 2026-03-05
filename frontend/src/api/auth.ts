import http from '@/utils/http'
import type { LoginResult, UserInfo } from '@/types'

export const authApi = {
  login: (data: { username: string; password: string }) =>
    http.post<LoginResult>('/auth/login', data),

  register: (data: { username: string; password: string }) =>
    http.post<void>('/auth/register', data),

  logout: () =>
    http.post<void>('/auth/logout'),

  me: () =>
    http.get<UserInfo>('/auth/me'),
}
