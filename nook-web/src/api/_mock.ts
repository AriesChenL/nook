// 后端 user/im 已落地，默认走真实接口（经 vite 代理到网关 8080）。
// 想用假数据演示：在 nook-web 根目录加 .env.local 写 VITE_USE_MOCK=true
export const USE_MOCK = import.meta.env.VITE_USE_MOCK === 'true'

export function delay<T>(data: T, ms = 220): Promise<T> {
  return new Promise((r) => setTimeout(() => r(data), ms))
}

// ───── 联系人 mock ─────
export interface MockFriend {
  userId: string // user public_id 字符串
  username: string
  nickname: string
  avatarUrl?: string
  signature?: string
  online?: boolean
}

export const mockFriends: MockFriend[] = [
  { userId: 'u-1001', username: 'alice', nickname: 'Alice', signature: '产品经理 · 喜欢徒步', online: true },
  { userId: 'u-1002', username: 'bruno', nickname: 'Bruno', signature: '前端工程师', online: true },
  { userId: 'u-1003', username: 'cathy', nickname: 'Cathy', signature: '设计师 · UX', online: false },
  { userId: 'u-1004', username: 'david', nickname: 'David', signature: '后端 · Java/Kotlin' },
  { userId: 'u-1005', username: 'eva',   nickname: 'Eva',   signature: '运营策划' },
  { userId: 'u-1006', username: 'frank', nickname: 'Frank', signature: 'DevOps' },
  { userId: 'u-1007', username: 'grace', nickname: 'Grace', signature: 'AI 研究员', online: true }
]

export interface MockFriendRequest {
  id: string // friend_request public_id 字符串
  fromUserId: string
  fromNickname: string
  message: string
  status: 0 | 1 | 2 | 3 // 0 待处理 1 已接受 2 已拒绝 3 已撤回
  createdAt: string
}

export const mockFriendRequests: MockFriendRequest[] = [
  { id: 'req-9001', fromUserId: 'u-2001', fromNickname: 'Helen', message: '我是 Alice 推荐的，希望加好友', status: 0, createdAt: '2026-05-27 14:02' },
  { id: 'req-9002', fromUserId: 'u-2002', fromNickname: 'Ivan',  message: '上次会议聊得很愉快', status: 0, createdAt: '2026-05-26 09:11' }
]

// ───── 会话 mock ─────
export interface MockConversation {
  id: string // conversation public_id 字符串
  type: 1 | 2 // 1 单聊 2 群聊
  name: string
  avatarUrl?: string
  lastMessage: string
  lastMessageAt: string
  unread: number
  members?: number
}

export const mockConversations: MockConversation[] = [
  { id: 'conv-10', type: 1, name: 'Alice',  lastMessage: '晚点视频聊一下？',           lastMessageAt: '14:32', unread: 2 },
  { id: 'conv-11', type: 2, name: '前端开发组', members: 8, lastMessage: 'Bruno: vite 5 的 HMR 修了', lastMessageAt: '13:55', unread: 12 },
  { id: 'conv-12', type: 1, name: 'Bruno',  lastMessage: '收到，明天交付',             lastMessageAt: '昨天', unread: 0 },
  { id: 'conv-13', type: 2, name: 'Nook 全员',  members: 24, lastMessage: 'Grace: AI 接口下周联调', lastMessageAt: '昨天', unread: 0 },
  { id: 'conv-14', type: 1, name: 'Cathy',  lastMessage: '图我已经画了三版了…',         lastMessageAt: '周一', unread: 0 },
  { id: 'conv-15', type: 1, name: 'David',  lastMessage: '[图片]',                     lastMessageAt: '5/22', unread: 0 }
]

export interface MockMessage {
  id: string // message public_id 字符串
  conversationId: string
  senderId: string
  senderName: string
  contentType: 1 | 2 | 3 | 4
  content: string
  createdAt: string
  mine?: boolean
}

export const mockMessages: Record<string, MockMessage[]> = {
  'conv-10': [
    { id: 'msg-10-1', conversationId: 'conv-10', senderId: 'u-1001', senderName: 'Alice', contentType: 1, content: '在吗？', createdAt: '14:20' },
    { id: 'msg-10-2', conversationId: 'conv-10', senderId: 'u-001',  senderName: 'me',    contentType: 1, content: '在的，怎么了', createdAt: '14:21', mine: true },
    { id: 'msg-10-3', conversationId: 'conv-10', senderId: 'u-1001', senderName: 'Alice', contentType: 1, content: '晚点视频聊一下？关于 IM 那块的设计有几个点想对齐', createdAt: '14:32' }
  ],
  'conv-11': [
    { id: 'msg-11-1', conversationId: 'conv-11', senderId: 'u-1002', senderName: 'Bruno', contentType: 1, content: 'vite 5 的 HMR 修了，可以拉一下', createdAt: '13:55' },
    { id: 'msg-11-2', conversationId: 'conv-11', senderId: 'u-001',  senderName: 'me',    contentType: 1, content: '👍 我这边升级一下', createdAt: '13:58', mine: true }
  ]
}

// ───── AI mock ─────
export const mockAiPresets = [
  '帮我润色一段产品文档',
  '用 Vue 3 写一个图片懒加载组件',
  '把这段会议纪要总结成 5 条要点',
  '解释下 PostgreSQL 的 MVCC'
]

export async function* mockAiStream(prompt: string): AsyncGenerator<string> {
  const reply = `好的，针对「${prompt}」我会从以下几个角度展开：\n\n1. 核心概念回顾\n2. 实际应用场景\n3. 常见坑与最佳实践\n\n（这是 mock 流式输出，接入真实 AI 后将由后端 SSE 推送。）`
  for (const ch of reply) {
    await new Promise((r) => setTimeout(r, 20))
    yield ch
  }
}
