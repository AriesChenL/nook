import http from './http'
import { USE_MOCK, delay, mockAiPresets } from './_mock'

// 欢迎页的建议 prompt（纯前端文案，复用 mock 里的预设）
export const aiPresets = mockAiPresets

// ───── 后端 VO 形状（对齐 nook-ai DTO）─────
export interface Agent {
  id: number
  ownerUserId: number
  name: string
  persona?: string
  avatarUrl?: string
  modelName: string
  status: number
  createdAt?: string
  updatedAt?: string
}

export interface ChatSession {
  id: number
  agentId: number
  title?: string
  createdAt?: string
  updatedAt?: string
}

export interface ChatReply {
  sessionId: number
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
  { id: 1, ownerUserId: 0, name: '小柚', persona: '元气满满的桌面助手', modelName: 'deepseek-v4-flash', status: 1 }
]
let mockSessions: ChatSession[] = [{ id: 1, agentId: 1, title: '默认会话' }]
let mockSeq = 2

// ───── Agent CRUD ─────
export async function listAgents(): Promise<Agent[]> {
  if (USE_MOCK) return delay([...mockAgents])
  return http.get<unknown, Agent[]>('/ai/agents')
}

export async function createAgent(body: CreateAgentBody): Promise<Agent> {
  if (USE_MOCK) {
    const a: Agent = {
      id: mockSeq++,
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

export async function updateAgent(id: number, body: UpdateAgentBody): Promise<Agent> {
  if (USE_MOCK) {
    const a = mockAgents.find((x) => x.id === id)!
    Object.assign(a, body)
    return delay({ ...a })
  }
  return http.put<unknown, Agent>(`/ai/agents/${id}`, body)
}

export async function deleteAgent(id: number): Promise<void> {
  if (USE_MOCK) {
    mockAgents = mockAgents.filter((x) => x.id !== id)
    mockSessions = mockSessions.filter((s) => s.agentId !== id)
    return delay(undefined)
  }
  return http.delete<unknown, void>(`/ai/agents/${id}`)
}

// ───── 会话线程 ─────
export async function listSessions(agentId: number): Promise<ChatSession[]> {
  if (USE_MOCK) return delay(mockSessions.filter((s) => s.agentId === agentId))
  return http.get<unknown, ChatSession[]>(`/ai/agents/${agentId}/sessions`)
}

export async function createSession(agentId: number, title?: string): Promise<ChatSession> {
  if (USE_MOCK) {
    const s: ChatSession = { id: mockSeq++, agentId, title: title || '新会话' }
    mockSessions.push(s)
    return delay(s)
  }
  return http.post<unknown, ChatSession>(`/ai/agents/${agentId}/sessions`, { title })
}

// ───── 对话（后端同步 .block()，非流式）─────
export async function chat(agentId: number, content: string, sessionId?: number): Promise<ChatReply> {
  if (USE_MOCK) {
    await delay(undefined, 500)
    return { sessionId: sessionId ?? 1, reply: `（mock）我收到了：「${content}」` }
  }
  return http.post<unknown, ChatReply>(`/ai/agents/${agentId}/chat`, { sessionId, content })
}
