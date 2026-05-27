import http from './http'
import {
  USE_MOCK,
  delay,
  mockFriends,
  mockFriendRequests,
  type MockFriend,
  type MockFriendRequest
} from './_mock'

export type Friend = MockFriend
export type FriendRequest = MockFriendRequest

export function listFriends() {
  if (USE_MOCK) return delay([...mockFriends])
  return http.get<unknown, Friend[]>('/user/friends')
}

export function listFriendRequests() {
  if (USE_MOCK) return delay([...mockFriendRequests])
  return http.get<unknown, FriendRequest[]>('/user/friend-requests')
}

export function searchUsers(keyword: string) {
  if (USE_MOCK) {
    const k = keyword.toLowerCase()
    return delay(mockFriends.filter((f) => f.username.includes(k) || f.nickname.toLowerCase().includes(k)))
  }
  return http.get<unknown, Friend[]>('/user/search', { params: { keyword } })
}

export function acceptFriendRequest(id: number) {
  if (USE_MOCK) {
    const req = mockFriendRequests.find((r) => r.id === id)
    if (req) req.status = 1
    return delay(undefined)
  }
  return http.post<unknown, void>(`/user/friend-requests/${id}/accept`)
}

export function rejectFriendRequest(id: number) {
  if (USE_MOCK) {
    const req = mockFriendRequests.find((r) => r.id === id)
    if (req) req.status = 2
    return delay(undefined)
  }
  return http.post<unknown, void>(`/user/friend-requests/${id}/reject`)
}
