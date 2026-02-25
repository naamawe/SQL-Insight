// =============================================
// 全局通用类型定义
// =============================================

/** 后端统一响应结构 */
export interface ApiResponse<T = unknown> {
  code: number
  message: string
  data: T
}

/** 分页响应 */
export interface PageResult<T> {
  records: T[]
  total: number
  current: number
  size: number
}

/** 用户信息 */
export interface UserInfo {
  userId: number
  username: string
  permissions: string[]
}

/** 登录响应 */
export interface LoginResult {
  token: string
  user: UserInfo
}

/** 数据源 */
export interface DataSourceVO {
  id: number
  connName: string
  dbType: string
  host: string
  port: number
  databaseName: string
  username: string
  gmtCreated: string
  gmtModified: string
}

/** 数据源保存请求 */
export interface DataSourceSaveDTO {
  connName: string
  dbType: string
  host: string
  port: number
  databaseName: string
  username: string
  password: string
}

/** 会话 */
export interface ChatSession {
  id: number
  userId: number
  dataSourceId: number
  title: string
  createTime: string
}

/** SSE 聊天请求 */
export interface SqlChatRequest {
  sessionId?: number
  dataSourceId?: number
  question: string
}

/** 聊天消息（前端维护） */
export interface ChatMessage {
  id: string
  role: 'user' | 'ai'
  content: string
  sql?: string
  sqlCorrected?: boolean
  tableData?: Record<string, unknown>[]
  total?: number
  loading?: boolean
  stage?: string
}

/** 角色 */
export interface Role {
  id: number
  roleName: string
  description: string
  gmtCreated: string
}

/** 用户 */
export interface UserVO {
  id: number
  userName: string
  roleId: number
  roleName?: string
  systemPermission: string
  status: number
  gmtCreated: string
}

/** 查询策略 */
export interface QueryPolicy {
  id?: number
  roleId: number
  allowJoin: 0 | 1
  allowSubquery: 0 | 1
  allowAggregation: 0 | 1
  maxLimit: number
}
