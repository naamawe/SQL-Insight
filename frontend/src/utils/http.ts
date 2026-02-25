import axios, { type AxiosResponse } from 'axios'
import { ElMessage } from 'element-plus'
import type { ApiResponse } from '@/types'

const http = axios.create({
  baseURL: '/api',
  timeout: 30000,
  headers: { 'Content-Type': 'application/json' },
})

// ── 请求拦截器：自动注入 JWT Token ──────────────────────
http.interceptors.request.use(
  (config) => {
    const token = localStorage.getItem('token')
    if (token) {
      config.headers.Authorization = `Bearer ${token}`
    }
    return config
  },
  (error) => Promise.reject(error),
)

// ── 响应拦截器：统一处理错误 ─────────────────────────────
http.interceptors.response.use(
  (response: AxiosResponse<ApiResponse>) => {
    const { code, message, data } = response.data

    if (code === 200) {
      return data as any
    }

    // 401：登录失效，跳转登录页
    if (code === 401) {
      localStorage.removeItem('token')
      localStorage.removeItem('userInfo')
      window.location.href = '/login'
      return Promise.reject(new Error(message))
    }

    // 其他业务错误：Toast 提示
    ElMessage.error(message || '请求失败')
    return Promise.reject(new Error(message))
  },
  (error) => {
    const status = error.response?.status
    const messageMap: Record<number, string> = {
      400: '请求参数错误',
      401: '登录已失效，请重新登录',
      403: '您没有操作权限',
      404: '请求的资源不存在',
      500: '服务器内部错误',
    }
    const msg = messageMap[status] ?? '网络异常，请稍后重试'
    ElMessage.error(msg)

    if (status === 401) {
      localStorage.removeItem('token')
      localStorage.removeItem('userInfo')
      window.location.href = '/login'
    }

    return Promise.reject(error)
  },
)

export default http
