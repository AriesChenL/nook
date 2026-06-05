import http from './http'

export interface LoginRequest {
  username: string
  password: string
}

export interface RegisterRequest {
  username: string
  password: string
  nickname?: string
}

export interface LoginResponse {
  userId: string // user public_id（字段名仍叫 userId，值=public_id 字符串）
  username: string
  nickname: string
  token: string
  expireSeconds: number
}

export function login(req: LoginRequest) {
  return http.post<unknown, LoginResponse>('/auth/login', req)
}

export function register(req: RegisterRequest) {
  return http.post<unknown, number>('/auth/register', req)
}

export function logout() {
  return http.post<unknown, void>('/auth/logout')
}
