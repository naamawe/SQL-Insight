import http from '@/utils/http'
import type { LoginResult, UserInfo } from '@/types'

export const authApi = {
  login: (data: { username: string; password: string }) =>
    http.post<unknown, LoginResult>('/auth/login', data),

  register: (data: { username: string; password: string }) =>
    http.post<unknown, void>('/auth/register', data),

  logout: () =>
    http.post<unknown, void>('/auth/logout'),

  me: () =>
    http.get<unknown, UserInfo>('/auth/me'),
}
