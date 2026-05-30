import http from './http'
import {
  USE_MOCK,
  delay,
  mockFriends,
  mockFriendRequests,
  type MockFriend,
  type MockFriendRequest
} from './_mock'

// ───── 后端 VO 形状 ─────
export interface UserVO {
  id: number
  username: string
  nickname: string
  avatarUrl?: string
  email?: string
  phone?: string
}

interface FriendVO {
  user: UserVO
  remark?: string
  createdAt?: string
}

interface FriendRequestVO {
  id: number
  fromUser: UserVO
  toUser: UserVO
  message?: string
  status: number
  createdAt?: string
  updatedAt?: string
}

// ───── 前端 UI 视图模型 ─────
export type Friend = MockFriend & { remark?: string }
export type FriendRequest = MockFriendRequest

function nameOf(u: UserVO): string {
  return u.nickname || u.username || `用户${u.id}`
}

function toFriend(vo: FriendVO): Friend {
  return {
    userId: vo.user.id,
    username: vo.user.username,
    nickname: nameOf(vo.user),
    avatarUrl: vo.user.avatarUrl,
    remark: vo.remark
  }
}

function toFriendFromUser(u: UserVO): Friend {
  return { userId: u.id, username: u.username, nickname: nameOf(u), avatarUrl: u.avatarUrl }
}

function toFriendRequest(vo: FriendRequestVO): FriendRequest {
  return {
    id: vo.id,
    fromUserId: vo.fromUser.id,
    fromNickname: nameOf(vo.fromUser),
    message: vo.message ?? '',
    status: (vo.status ?? 0) as FriendRequest['status'],
    createdAt: vo.createdAt ?? ''
  }
}

// ───── 当前用户 ─────
export function getMe() {
  return http.get<unknown, UserVO>('/user/me')
}

export interface UpdateProfileRequest {
  nickname?: string
  avatarUrl?: string
  email?: string
  phone?: string
}

export function updateProfile(req: UpdateProfileRequest) {
  return http.put<unknown, UserVO>('/user/me', req)
}

export function getUser(id: number) {
  return http.get<unknown, UserVO>(`/user/${id}`)
}

// 批量取公开资料（去重，后端限 200）。供 im 解析群成员/消息发送者名称，避免 N+1。
export async function getUsersByIds(ids: number[]): Promise<UserVO[]> {
  const uniq = [...new Set(ids)].filter((id) => id > 0)
  if (!uniq.length) return []
  if (USE_MOCK) {
    return delay(
      mockFriends
        .filter((f) => uniq.includes(f.userId))
        .map((f) => ({ id: f.userId, username: f.username, nickname: f.nickname, avatarUrl: f.avatarUrl }))
    )
  }
  return http.get<unknown, UserVO[]>('/user/batch', { params: { ids: uniq.join(',') } })
}

// ───── 好友 ─────
export async function listFriends(): Promise<Friend[]> {
  if (USE_MOCK) return delay([...mockFriends])
  const vos = await http.get<unknown, FriendVO[]>('/user/friends')
  return vos.map(toFriend)
}

export async function listFriendRequests(): Promise<FriendRequest[]> {
  if (USE_MOCK) return delay([...mockFriendRequests])
  const vos = await http.get<unknown, FriendRequestVO[]>('/user/friends/requests/incoming')
  return vos.map(toFriendRequest)
}

export async function searchUsers(keyword: string): Promise<Friend[]> {
  if (USE_MOCK) {
    const k = keyword.toLowerCase()
    return delay(mockFriends.filter((f) => f.username.includes(k) || f.nickname.toLowerCase().includes(k)))
  }
  const vos = await http.get<unknown, UserVO[]>('/user/search', { params: { q: keyword } })
  return vos.map(toFriendFromUser)
}

export function sendFriendRequest(toUserId: number, message?: string) {
  if (USE_MOCK) return delay(undefined)
  return http.post<unknown, FriendRequestVO>('/user/friends/requests', { toUserId, message })
}

export function acceptFriendRequest(id: number) {
  if (USE_MOCK) {
    const req = mockFriendRequests.find((r) => r.id === id)
    if (req) req.status = 1
    return delay(undefined)
  }
  return http.post<unknown, void>(`/user/friends/requests/${id}/accept`)
}

export function rejectFriendRequest(id: number) {
  if (USE_MOCK) {
    const req = mockFriendRequests.find((r) => r.id === id)
    if (req) req.status = 2
    return delay(undefined)
  }
  return http.post<unknown, void>(`/user/friends/requests/${id}/reject`)
}

export function removeFriend(friendUserId: number) {
  if (USE_MOCK) return delay(undefined)
  return http.delete<unknown, void>(`/user/friends/${friendUserId}`)
}

export function updateFriendRemark(friendUserId: number, remark: string) {
  if (USE_MOCK) return delay(undefined)
  return http.put<unknown, void>(`/user/friends/${friendUserId}/remark`, { remark })
}
