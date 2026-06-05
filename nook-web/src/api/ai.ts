import http from './http'
import { USE_MOCK, delay, mockAiPresets } from './_mock'

// 欢迎页的建议 prompt（纯前端文案，复用 mock 里的预设）
export const aiPresets = mockAiPresets

// ───── 后端 VO 形状（对齐 nook-ai DTO）─────
export interface Agent {
  id: string // agent public_id 字符串
  ownerUserId: number // 后端仍输出数字，前端不展示，忽略
  name: string
  persona?: string
  avatarUrl?: string
  modelName: string
  status: number
  createdAt?: string
  updatedAt?: string
}

export interface ChatSession {
  id: string // session public_id 字符串
  agentId: string // agent public_id
  title?: string
  createdAt?: string
  updatedAt?: string
}

export interface ChatReply {
  sessionId: string // session public_id
  reply: string
}

export interface CreateAgentBody {
  name: string
  persona?: string
  avatarUrl?: string
  modelName?: string
}

export interface UpdateAgentBody {
  name?: string
  persona?: string
  avatarUrl?: string
}

// ───── 轻量 mock（仅 VITE_USE_MOCK=true 时启用，便于无后端预览）─────
let mockAgents: Agent[] = [
  { id: 'agent-1', ownerUserId: 0, name: '小柚', persona: '元气满满的桌面助手', modelName: 'deepseek-v4-flash', status: 1 }
]
let mockSessions: ChatSession[] = [{ id: 'sess-1', agentId: 'agent-1', title: '默认会话' }]
let mockSeq = 2

// ───── Agent CRUD ─────
export async function listAgents(): Promise<Agent[]> {
  if (USE_MOCK) return delay([...mockAgents])
  return http.get<unknown, Agent[]>('/ai/agents')
}

export async function createAgent(body: CreateAgentBody): Promise<Agent> {
  if (USE_MOCK) {
    const a: Agent = {
      id: `agent-${mockSeq++}`,
      ownerUserId: 0,
      name: body.name,
      persona: body.persona,
      avatarUrl: body.avatarUrl,
      modelName: body.modelName || 'deepseek-v4-flash',
      status: 1
    }
    mockAgents.push(a)
    return delay(a)
  }
  return http.post<unknown, Agent>('/ai/agents', body)
}

export async function updateAgent(id: string, body: UpdateAgentBody): Promise<Agent> {
  if (USE_MOCK) {
    const a = mockAgents.find((x) => x.id === id)!
    Object.assign(a, body)
    return delay({ ...a })
  }
  return http.put<unknown, Agent>(`/ai/agents/${id}`, body)
}

export async function deleteAgent(id: string): Promise<void> {
  if (USE_MOCK) {
    mockAgents = mockAgents.filter((x) => x.id !== id)
    mockSessions = mockSessions.filter((s) => s.agentId !== id)
    return delay(undefined)
  }
  return http.delete<unknown, void>(`/ai/agents/${id}`)
}

// ───── 会话线程 ─────
export async function listSessions(agentId: string): Promise<ChatSession[]> {
  if (USE_MOCK) return delay(mockSessions.filter((s) => s.agentId === agentId))
  return http.get<unknown, ChatSession[]>(`/ai/agents/${agentId}/sessions`)
}

export async function createSession(agentId: string, title?: string): Promise<ChatSession> {
  if (USE_MOCK) {
    const s: ChatSession = { id: `sess-${mockSeq++}`, agentId, title: title || '新会话' }
    mockSessions.push(s)
    return delay(s)
  }
  return http.post<unknown, ChatSession>(`/ai/agents/${agentId}/sessions`, { title })
}

// ───── 对话（后端同步 .block()，非流式）─────
export async function chat(agentId: string, content: string, sessionId?: string): Promise<ChatReply> {
  if (USE_MOCK) {
    await delay(undefined, 500)
    const reply = [
      `收到你的问题：**${content}**`,
      '',
      '下面是一个 Markdown 渲染示例：',
      '',
      '## 要点',
      '1. 支持 **加粗**、*斜体*、`行内代码`',
      '2. 列表、引用、链接（如 [Vue 文档](https://vuejs.org)）',
      '3. 代码块带语法高亮：',
      '',
      '```ts',
      'const greet = (name: string): string => `Hi, ${name}!`',
      'console.log(greet("Nook"))',
      '```',
      '',
      '> 这是一条引用，说明记忆会被持久化。',
      '',
      '（mock 数据，接入真实后端后由 AI 返回）'
    ].join('\n')
    return { sessionId: sessionId ?? 'sess-1', reply }
  }
  return http.post<unknown, ChatReply>(`/ai/agents/${agentId}/chat`, { sessionId, content })
}
