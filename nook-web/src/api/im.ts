import http from './http'
import { getUsersByIds, type UserVO } from './user'
import { useAuthStore } from '@/stores/auth'
import {
  USE_MOCK,
  delay,
  mockConversations,
  mockMessages
} from './_mock'

// 群成员角色：1=普通 2=管理员 3=群主
export const ROLE = { MEMBER: 1, ADMIN: 2, OWNER: 3 } as const

// ───── 后端 VO 形状 ─────
interface ConversationVO {
  id: string // 会话 public_id 字符串
  type: number // 1=单聊 2=群聊
  name?: string
  avatarUrl?: string
  ownerId?: string // user public_id
  lastMessageId?: string // 消息 public_id
  lastMessageAt?: string
  memberIds?: string[] // user public_id 列表
  lastReadMsgId?: string // 消息 public_id
  unreadCount?: number
  myRole?: number // 当前用户在该会话中的角色（单聊恒为普通）
}

interface MessageVO {
  id: string // 消息 public_id
  conversationId: string // 会话 public_id
  senderId: string // user public_id
  contentType: number // 1=text 2=image 3=file 4=系统消息(JSON)
  content?: string
  fileUrl?: string
  fileName?: string
  fileSize?: number
  mediaType?: string
  recalled?: number
  recalledAt?: string
  createdAt?: string
}

interface MemberVO {
  userId: string // user public_id
  role: number
  joinedAt?: string
  username?: string
  nickname?: string
  avatarUrl?: string
}

// ───── 前端 UI 视图模型 ─────
export interface Conversation {
  id: string // 会话 public_id
  type: number
  name: string
  avatarUrl?: string
  lastMessage: string
  lastMessageAt: string
  unread: number
  members?: number
  peerId?: string // user public_id
  ownerId?: string // user public_id
  myRole?: number
  lastMessageId?: string // 消息 public_id
  lastReadMsgId?: string // 消息 public_id
}

export interface Message {
  id: string // 消息 public_id
  conversationId: string
  senderId: string // user public_id
  senderName: string
  contentType: number
  content: string
  fileUrl?: string
  fileName?: string
  fileSize?: number
  mediaType?: string
  createdAt: string
  createdAtMs: number
  mine?: boolean
  recalled?: boolean
  system?: boolean // contentType=4 群系统消息，UI 居中渲染
}

export interface Member {
  userId: string // user public_id
  role: number
  joinedAt?: string
  username?: string
  nickname: string
  avatarUrl?: string
}

// ───── 用户名解析缓存 ─────
// userId 为 user public_id 字符串，仅作不透明句柄做相等/Map key，不做数值运算。
const userCache = new Map<string, UserVO>()

async function resolveUsers(ids: string[]): Promise<void> {
  const missing = [...new Set(ids)].filter((id) => id && !userCache.has(id))
  if (!missing.length) return
  const fetched = await getUsersByIds(missing).catch(() => [] as UserVO[])
  const byId = new Map(fetched.map((u) => [u.id, u]))
  missing.forEach((id) => {
    const u = byId.get(id)
    userCache.set(id, u ?? { id, username: `user${id}`, nickname: `用户${id}` })
  })
}

function userName(id: string): string {
  const u = userCache.get(id)
  return u ? (u.nickname || u.username) : `用户${id}`
}

function myId(): string {
  return useAuthStore().user?.userId ?? ''
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

// ───── 系统消息（contentType=4）解析 ─────
interface SystemPayload {
  action: string
  operatorId?: string // user public_id
  targetId?: string // user public_id
  targetIds?: string[] // user public_id 列表
  role?: number
}

function parseSystem(content?: string): SystemPayload | null {
  if (!content) return null
  try {
    const p = JSON.parse(content)
    return typeof p?.action === 'string' ? p : null
  } catch {
    return null
  }
}

// 系统消息里涉及的所有 userId（提前 resolveUsers，渲染才有名字）
function systemUserIds(vo: MessageVO): string[] {
  if (vo.contentType !== 4) return []
  const p = parseSystem(vo.content)
  if (!p) return []
  const ids: string[] = []
  if (p.operatorId) ids.push(p.operatorId)
  if (p.targetId) ids.push(p.targetId)
  if (Array.isArray(p.targetIds)) ids.push(...p.targetIds)
  return ids
}

function systemText(p: SystemPayload): string {
  const op = userName(p.operatorId ?? '')
  const targets = (p.targetIds ?? []).map(userName).join('、')
  const target = userName(p.targetId ?? '')
  switch (p.action) {
    case 'group_created': return `${op} 创建了群聊`
    case 'members_added': return `${op} 邀请 ${targets} 加入群聊`
    case 'member_removed': return `${op} 将 ${target} 移出群聊`
    case 'member_left': return `${op} 退出了群聊`
    case 'owner_transferred': return `${op} 将群主转让给 ${target}`
    case 'role_changed': return `${op} 将 ${target} ${p.role === ROLE.ADMIN ? '设为管理员' : '取消了管理员'}`
    default: return '系统消息'
  }
}

// 按 MIME 归类，决定气泡如何渲染：图片/视频/音频/普通文件
export function fileKind(mediaType?: string): 'image' | 'video' | 'audio' | 'file' {
  if (!mediaType) return 'file'
  if (mediaType.startsWith('image/')) return 'image'
  if (mediaType.startsWith('video/')) return 'video'
  if (mediaType.startsWith('audio/')) return 'audio'
  return 'file'
}

function previewOf(vo: MessageVO | undefined): string {
  if (!vo) return ''
  if (vo.recalled === 1) return '[消息已撤回]'
  if (vo.contentType === 2) return '[图片]'
  if (vo.contentType === 3) {
    const kind = fileKind(vo.mediaType)
    if (kind === 'video') return '[视频]'
    if (kind === 'audio') return '[语音]'
    return `[文件] ${vo.fileName ?? ''}`.trim()
  }
  if (vo.contentType === 4) {
    const p = parseSystem(vo.content)
    return p ? systemText(p) : '[系统消息]'
  }
  return vo.content ?? ''
}

// WS 推送帧 → 会话列表预览文案：系统消息/文件消息均正确转换，按需补全用户名
export async function previewIncoming(raw: unknown): Promise<string> {
  const vo = raw as MessageVO
  const ids = systemUserIds(vo).filter((id) => id && id !== myId())
  if (ids.length) await resolveUsers(ids)
  return previewOf(vo)
}

// ───── VO → UI 映射 ─────
function mapMessage(vo: MessageVO): Message {
  const ms = vo.createdAt ? new Date(vo.createdAt).getTime() : Date.now()
  const recalled = vo.recalled === 1
  const sys = vo.contentType === 4 ? parseSystem(vo.content) : null
  const mine = !sys && vo.senderId === myId()
  let content: string
  if (sys) content = systemText(sys)
  else if (recalled) content = '[消息已撤回]'
  else content = vo.content ?? ''
  return {
    id: vo.id,
    conversationId: vo.conversationId,
    senderId: vo.senderId,
    senderName: mine ? '我' : userName(vo.senderId),
    contentType: vo.contentType,
    content,
    fileUrl: vo.fileUrl,
    fileName: vo.fileName,
    fileSize: vo.fileSize,
    mediaType: vo.mediaType,
    createdAt: fmtTime(ms),
    createdAtMs: ms,
    mine,
    recalled,
    system: !!sys
  }
}

async function mapConversation(vo: ConversationVO, preview?: MessageVO): Promise<Conversation> {
  const me = myId()
  const memberIds = vo.memberIds ?? []
  let name = vo.name ?? ''
  let avatarUrl = vo.avatarUrl
  let peerId: string | undefined
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
    ownerId: vo.ownerId,
    myRole: vo.myRole,
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
    .filter((id): id is string => id != null)
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

export async function getConversation(id: string): Promise<Conversation> {
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

export function getOrCreateDirect(peerUserId: string): Promise<Conversation> {
  if (USE_MOCK) return delay({ id: `conv-direct-${peerUserId}`, type: 1, name: '新会话', lastMessage: '', lastMessageAt: '', unread: 0 } as Conversation)
  return (async () => {
    const vo = await http.post<unknown, ConversationVO>('/im/conversations/direct', { peerUserId })
    await resolveUsers([peerUserId])
    return mapConversation(vo)
  })()
}

export async function listMessages(conversationId: string): Promise<Message[]> {
  if (USE_MOCK) return delay((mockMessages[conversationId] ?? []) as unknown as Message[])
  const vos = await http.get<unknown, MessageVO[]>('/im/messages', {
    params: { conversationId, limit: 50 }
  })
  await resolveUsers(vos.flatMap((m) => [m.senderId, ...systemUserIds(m)]))
  // id 为不透明 public_id，不做数值大小比较；按创建时间排序保证时序。
  return vos.map(mapMessage).sort((a, b) => a.createdAtMs - b.createdAtMs)
}

export async function sendMessage(conversationId: string, content: string): Promise<Message> {
  if (USE_MOCK) {
    const msg = {
      id: `msg-local-${Date.now()}`,
      conversationId,
      senderId: myId(),
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

// ───── 文件消息：预签名直传 + 发送 ─────
export interface PresignResult {
  uploadUrl: string
  downloadUrl: string
  objectKey: string
  mediaType: string
  expireSeconds: number
}

export interface FileMessageMeta {
  fileUrl: string
  fileName: string
  fileSize: number
  mediaType: string
}

/** 申请预签名上传地址。mock 模式用本地 blob URL 兜底，便于无后端预览。 */
export function presignUpload(file: File): Promise<PresignResult> {
  const mediaType = file.type || 'application/octet-stream'
  if (USE_MOCK) {
    const url = URL.createObjectURL(file)
    return delay({ uploadUrl: url, downloadUrl: url, objectKey: file.name, mediaType, expireSeconds: 600 })
  }
  return http.post<unknown, PresignResult>('/im/files/presign', {
    fileName: file.name,
    mimeType: mediaType,
    size: file.size
  })
}

/** PUT 直传到对象存储，回调上传百分比（0-100）。 */
export function uploadToStorage(
  uploadUrl: string,
  file: File,
  onProgress?: (pct: number) => void
): Promise<void> {
  if (USE_MOCK) {
    onProgress?.(100)
    return delay(undefined) as Promise<void>
  }
  return new Promise<void>((resolve, reject) => {
    const xhr = new XMLHttpRequest()
    xhr.open('PUT', uploadUrl)
    xhr.setRequestHeader('Content-Type', file.type || 'application/octet-stream')
    xhr.upload.onprogress = (e) => {
      if (e.lengthComputable && onProgress) onProgress(Math.round((e.loaded / e.total) * 100))
    }
    xhr.onload = () =>
      xhr.status >= 200 && xhr.status < 300
        ? resolve()
        : reject(new Error(`上传失败 (${xhr.status})`))
    xhr.onerror = () => reject(new Error('上传失败，请检查网络或存储服务'))
    xhr.send(file)
  })
}

/** 发送文件消息：image/* → contentType 2，其余 → 3。 */
export async function sendFileMessage(conversationId: string, meta: FileMessageMeta): Promise<Message> {
  const contentType = meta.mediaType.startsWith('image/') ? 2 : 3
  if (USE_MOCK) {
    const msg = {
      id: `msg-local-${Date.now()}`,
      conversationId,
      senderId: myId(),
      senderName: '我',
      contentType,
      content: meta.fileName,
      fileUrl: meta.fileUrl,
      fileName: meta.fileName,
      fileSize: meta.fileSize,
      mediaType: meta.mediaType,
      createdAt: fmtTime(Date.now()),
      createdAtMs: Date.now(),
      mine: true
    } as Message
    ;(mockMessages[conversationId] ??= []).push(msg as never)
    return delay(msg, 120)
  }
  const vo = await http.post<unknown, MessageVO>('/im/messages', {
    conversationId,
    contentType,
    content: meta.fileName,
    fileUrl: meta.fileUrl,
    fileName: meta.fileName,
    fileSize: meta.fileSize,
    mediaType: meta.mediaType
  })
  return mapMessage(vo)
}

// 在线状态初始快照：返回「当前在线的好友 userId 列表」。
// WS presence 帧只在跳变时推送，进页面时需先拉一次快照，否则已在线的好友会一直显示离线。
export function listOnlineFriends(): Promise<string[]> {
  if (USE_MOCK) return delay([] as string[])
  return http.get<unknown, string[]>('/im/presence/online')
}

export function markRead(conversationId: string, lastReadMsgId: string) {
  if (USE_MOCK) return delay(undefined)
  return http.post<unknown, void>(`/im/conversations/${conversationId}/read`, { lastReadMsgId })
}

export function recallMessage(messageId: string) {
  if (USE_MOCK) return delay(undefined)
  return http.post<unknown, void>(`/im/messages/${messageId}/recall`)
}

// 群聊已读状态：totalRecipients/readCount 均不含发送者本人
export interface ReadStatus {
  messageId: string
  conversationId: string
  totalRecipients: number
  readCount: number
  readerUserIds: string[] // user public_id 列表
}

export function getReadStatus(messageId: string): Promise<ReadStatus> {
  if (USE_MOCK) {
    return delay({ messageId, conversationId: '', totalRecipients: 0, readCount: 0, readerUserIds: [] })
  }
  return http.get<unknown, ReadStatus>(`/im/messages/${messageId}/read-status`)
}

// 供 WS 订阅者把推送帧里的 MessageVO 解码为 UI Message（会按需补全发送者名称）
export async function decodeIncoming(raw: unknown): Promise<Message> {
  const vo = raw as MessageVO
  const ids = [vo.senderId, ...systemUserIds(vo)].filter((id) => id && id !== myId())
  if (ids.length) await resolveUsers(ids)
  return mapMessage(vo)
}

// ───── 群聊管理 ─────
export async function listMembers(conversationId: string): Promise<Member[]> {
  if (USE_MOCK) return delay([] as Member[])
  const vos = await http.get<unknown, MemberVO[]>(`/im/conversations/${conversationId}/members`)
  return vos
    .map((m) => ({
      userId: m.userId,
      role: m.role,
      joinedAt: m.joinedAt,
      username: m.username,
      nickname: m.nickname || m.username || `用户${m.userId}`,
      avatarUrl: m.avatarUrl
    }))
    .sort((a, b) => b.role - a.role)
}

export async function createGroup(name: string, memberIds: string[], avatarUrl?: string): Promise<Conversation> {
  if (USE_MOCK) {
    return delay({ id: `conv-group-${Date.now()}`, type: 2, name, lastMessage: '', lastMessageAt: '', unread: 0, members: memberIds.length } as Conversation)
  }
  const vo = await http.post<unknown, ConversationVO>('/im/conversations/group', { name, memberIds, avatarUrl })
  await resolveUsers(vo.memberIds ?? [])
  return mapConversation(vo)
}

export function updateGroup(conversationId: string, body: { name?: string; avatarUrl?: string }) {
  if (USE_MOCK) return delay(undefined)
  return http.put<unknown, void>(`/im/conversations/${conversationId}`, body)
}

export function addMembers(conversationId: string, memberIds: string[]) {
  if (USE_MOCK) return delay(undefined)
  return http.post<unknown, void>(`/im/conversations/${conversationId}/members`, { memberIds })
}

export function removeMember(conversationId: string, targetUserId: string) {
  if (USE_MOCK) return delay(undefined)
  return http.delete<unknown, void>(`/im/conversations/${conversationId}/members/${targetUserId}`)
}

export function setMemberRole(conversationId: string, targetUserId: string, role: number) {
  if (USE_MOCK) return delay(undefined)
  return http.put<unknown, void>(`/im/conversations/${conversationId}/members/${targetUserId}/role`, { role })
}

export function leaveGroup(conversationId: string) {
  if (USE_MOCK) return delay(undefined)
  return http.post<unknown, void>(`/im/conversations/${conversationId}/leave`)
}

export function transferOwner(conversationId: string, newOwnerId: string) {
  if (USE_MOCK) return delay(undefined)
  return http.post<unknown, void>(`/im/conversations/${conversationId}/owner`, { newOwnerId })
}
