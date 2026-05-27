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
  userId: number
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
