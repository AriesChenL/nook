import http from './http'
import { getUser, type UserVO } from './user'
import { useAuthStore } from '@/stores/auth'
import {
  USE_MOCK,
  delay,
  mockConversations,
  mockMessages
} from './_mock'

// ───── 后端 VO 形状 ─────
interface ConversationVO {
  id: number
  type: number // 1=单聊 2=群聊
  name?: string
  avatarUrl?: string
  ownerId?: number
  lastMessageId?: number
  lastMessageAt?: string
  memberIds?: number[]
  lastReadMsgId?: number
  unreadCount?: number
}

interface MessageVO {
  id: number
  conversationId: number
  senderId: number
  contentType: number // 1=text 2=image 3=file
  content?: string
  recalled?: number
  recalledAt?: string
  createdAt?: string
}

// ───── 前端 UI 视图模型 ─────
export interface Conversation {
  id: number
  type: number
  name: string
  avatarUrl?: string
  lastMessage: string
  lastMessageAt: string
  unread: number
  members?: number
  peerId?: number
  lastMessageId?: number
  lastReadMsgId?: number
}

export interface Message {
  id: number
  conversationId: number
  senderId: number
  senderName: string
  contentType: number
  content: string
  createdAt: string
  createdAtMs: number
  mine?: boolean
  recalled?: boolean
}

// ───── 用户名解析缓存 ─────
const userCache = new Map<number, UserVO>()

async function resolveUsers(ids: number[]): Promise<void> {
  const missing = [...new Set(ids)].filter((id) => id && !userCache.has(id))
  if (!missing.length) return
  const fetched = await Promise.all(
    missing.map((id) => getUser(id).catch(() => null))
  )
  fetched.forEach((u, i) => {
    if (u) userCache.set(missing[i], u)
    else userCache.set(missing[i], { id: missing[i], username: `user${missing[i]}`, nickname: `用户${missing[i]}` })
  })
}

function userName(id: number): string {
  const u = userCache.get(id)
  return u ? (u.nickname || u.username) : `用户${id}`
}

function myId(): number {
  return useAuthStore().user?.userId ?? 0
}

// ───── 时间/预览格式化 ─────
function fmtTime(ms: number): string {
  const d = new Date(ms)
  return d.toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' })
}

function fmtConvTime(iso?: string): string {
  if (!iso) return ''
  const d = new Date(iso)
  const ms = d.getTime()
  if (Number.isNaN(ms)) return ''
  const now = new Date()
  const sameDay = d.toDateString() === now.toDateString()
  if (sameDay) return fmtTime(ms)
  const yesterday = new Date(now)
  yesterday.setDate(now.getDate() - 1)
  if (d.toDateString() === yesterday.toDateString()) return '昨天'
  return `${d.getMonth() + 1}/${d.getDate()}`
}

function previewOf(vo: MessageVO | undefined): string {
  if (!vo) return ''
  if (vo.recalled === 1) return '[消息已撤回]'
  if (vo.contentType === 2) return '[图片]'
  if (vo.contentType === 3) return '[文件]'
  return vo.content ?? ''
}

// ───── VO → UI 映射 ─────
function mapMessage(vo: MessageVO): Message {
  const ms = vo.createdAt ? new Date(vo.createdAt).getTime() : Date.now()
  const recalled = vo.recalled === 1
  const mine = vo.senderId === myId()
  return {
    id: vo.id,
    conversationId: vo.conversationId,
    senderId: vo.senderId,
    senderName: mine ? '我' : userName(vo.senderId),
    contentType: vo.contentType,
    content: recalled ? '[消息已撤回]' : (vo.content ?? ''),
    createdAt: fmtTime(ms),
    createdAtMs: ms,
    mine,
    recalled
  }
}

async function mapConversation(vo: ConversationVO, preview?: MessageVO): Promise<Conversation> {
  const me = myId()
  const memberIds = vo.memberIds ?? []
  let name = vo.name ?? ''
  let avatarUrl = vo.avatarUrl
  let peerId: number | undefined
  if (vo.type === 1) {
    peerId = memberIds.find((id) => id !== me)
    if (peerId != null) {
      const u = userCache.get(peerId)
      name = u ? (u.nickname || u.username) : `用户${peerId}`
      avatarUrl = avatarUrl ?? u?.avatarUrl
    }
  } else if (!name) {
    name = `群聊 (${memberIds.length})`
  }
  return {
    id: vo.id,
    type: vo.type,
    name: name || `会话 #${vo.id}`,
    avatarUrl,
    lastMessage: previewOf(preview),
    lastMessageAt: fmtConvTime(vo.lastMessageAt),
    unread: Number(vo.unreadCount ?? 0),
    members: vo.type === 2 ? memberIds.length : undefined,
    peerId,
    lastMessageId: vo.lastMessageId,
    lastReadMsgId: vo.lastReadMsgId
  }
}

// ───── API ─────
export async function listConversations(): Promise<Conversation[]> {
  if (USE_MOCK) return delay([...mockConversations] as unknown as Conversation[])
  const vos = await http.get<unknown, ConversationVO[]>('/im/conversations')
  const me = myId()
  const peerIds = vos
    .filter((v) => v.type === 1)
    .map((v) => (v.memberIds ?? []).find((id) => id !== me))
    .filter((id): id is number => id != null)
  await resolveUsers(peerIds)
  // 拉每个会话的最后一条消息作为预览（后端会话 VO 不含消息正文）
  const previews = await Promise.all(
    vos.map((v) =>
      v.lastMessageId
        ? http
            .get<unknown, MessageVO[]>('/im/messages', { params: { conversationId: v.id, limit: 1 } })
            .then((arr) => arr[arr.length - 1])
            .catch(() => undefined)
        : Promise.resolve(undefined)
    )
  )
  return Promise.all(vos.map((v, i) => mapConversation(v, previews[i])))
}

export async function getConversation(id: number): Promise<Conversation> {
  if (USE_MOCK) {
    const c = (mockConversations as unknown as Conversation[]).find((x) => x.id === id)
    return delay(c ?? ({ id, type: 1, name: `会话 #${id}`, lastMessage: '', lastMessageAt: '', unread: 0 } as Conversation))
  }
  const vo = await http.get<unknown, ConversationVO>(`/im/conversations/${id}`)
  const me = myId()
  if (vo.type === 1) {
    const peerId = (vo.memberIds ?? []).find((uid) => uid !== me)
    if (peerId != null) await resolveUsers([peerId])
  }
  return mapConversation(vo)
}

export function getOrCreateDirect(peerUserId: number): Promise<Conversation> {
  if (USE_MOCK) return delay({ id: peerUserId, type: 1, name: '新会话', lastMessage: '', lastMessageAt: '', unread: 0 } as Conversation)
  return (async () => {
    const vo = await http.post<unknown, ConversationVO>('/im/conversations/direct', { peerUserId })
    await resolveUsers([peerUserId])
    return mapConversation(vo)
  })()
}

export async function listMessages(conversationId: number): Promise<Message[]> {
  if (USE_MOCK) return delay((mockMessages[conversationId] ?? []) as unknown as Message[])
  const vos = await http.get<unknown, MessageVO[]>('/im/messages', {
    params: { conversationId, limit: 50 }
  })
  await resolveUsers(vos.map((m) => m.senderId))
  return vos.map(mapMessage).sort((a, b) => a.id - b.id)
}

export async function sendMessage(conversationId: number, content: string): Promise<Message> {
  if (USE_MOCK) {
    const msg = {
      id: Date.now(),
      conversationId,
      senderId: 0,
      senderName: '我',
      contentType: 1,
      content,
      createdAt: fmtTime(Date.now()),
      createdAtMs: Date.now(),
      mine: true
    } as Message
    ;(mockMessages[conversationId] ??= []).push(msg as never)
    return delay(msg, 120)
  }
  const vo = await http.post<unknown, MessageVO>('/im/messages', { conversationId, content })
  return mapMessage(vo)
}

export function markRead(conversationId: number, lastReadMsgId: number) {
  if (USE_MOCK) return delay(undefined)
  return http.post<unknown, void>(`/im/conversations/${conversationId}/read`, { lastReadMsgId })
}

export function recallMessage(messageId: number) {
  if (USE_MOCK) return delay(undefined)
  return http.post<unknown, void>(`/im/messages/${messageId}/recall`)
}

// 供 WS 订阅者把推送帧里的 MessageVO 解码为 UI Message（会按需补全发送者名称）
export async function decodeIncoming(raw: unknown): Promise<Message> {
  const vo = raw as MessageVO
  if (vo.senderId !== myId()) await resolveUsers([vo.senderId])
  return mapMessage(vo)
}
