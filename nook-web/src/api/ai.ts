import http from './http'
import { USE_MOCK, delay, mockAiPresets } from './_mock'
import { useAuthStore } from '@/stores/auth'

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

export interface ChatMessage {
  id: string // 消息 public_id
  role: 'user' | 'assistant'
  content: string
  trace?: string // assistant 推理过程 steps 的 JSON（思考分段 + 工具调用）；前端解析后还原
  createdAt?: string
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

// ───── 流式对话（SSE：边生成边推增量）─────
// 工具调用过程事件：按 id 聚合（call 起调带 name，args/result 为增量，end 结束）
export interface ToolStreamEvent {
  phase: 'call' | 'args' | 'result' | 'end'
  id: string
  name?: string // phase=call 时带工具名
  t?: string // phase=args/result 时带增量文本
}

export interface ChatStreamHandlers {
  onDelta: (text: string) => void // 收到一段答案增量
  onThinking?: (id: string, text: string) => void // 模型思考增量（id=blockId，区分多段思考）
  onTool?: (ev: ToolStreamEvent) => void // 工具调用过程
  onDone: (sessionId: string, text: string) => void // 生成结束：回传会话 public_id + 权威全文（用于校正）
  onError: (message: string) => void // 服务端 error 事件
}

/**
 * 经 SSE 流式对话。用 fetch 手动读流（EventSource 不支持自定义 Authorization 头 + POST body）。
 * 后端事件：delta `{t}` / done `{sessionId}` / error `{message}`。
 */
export async function chatStream(
  agentId: string,
  content: string,
  sessionId: string | undefined,
  handlers: ChatStreamHandlers
): Promise<void> {
  if (USE_MOCK) {
    const full = `（mock 流式回复）收到你的问题：**${content}**\n\n逐字流出示例……`
    for (const ch of full) {
      await delay(undefined, 18)
      handlers.onDelta(ch)
    }
    handlers.onDone(sessionId ?? 'sess-1', full)
    return
  }

  const auth = useAuthStore()
  const resp = await fetch(`/api/ai/agents/${agentId}/chat/stream`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      Accept: 'text/event-stream',
      ...(auth.token ? { Authorization: `Bearer ${auth.token}` } : {})
    },
    body: JSON.stringify({ sessionId, content })
  })
  if (resp.status === 401) {
    handlers.onError('登录已过期')
    return
  }
  if (!resp.ok || !resp.body) {
    handlers.onError(`请求失败（HTTP ${resp.status}）`)
    return
  }

  const reader = resp.body.getReader()
  const decoder = new TextDecoder()
  let buffer = ''
  for (;;) {
    const { value, done } = await reader.read()
    if (done) break
    buffer += decoder.decode(value, { stream: true })
    let sep: number
    // SSE 以空行（\n\n）分隔事件
    while ((sep = buffer.indexOf('\n\n')) !== -1) {
      const frame = buffer.slice(0, sep)
      buffer = buffer.slice(sep + 2)
      dispatchSseFrame(frame, handlers)
    }
  }
}

function dispatchSseFrame(frame: string, handlers: ChatStreamHandlers) {
  let event = 'message'
  let data = ''
  for (const rawLine of frame.split('\n')) {
    const line = rawLine.replace(/\r$/, '')
    if (line.startsWith('event:')) event = line.slice(6).trim()
    else if (line.startsWith('data:')) data += line.slice(5).trim()
  }
  if (!data) return
  let payload: {
    t?: string
    sessionId?: string
    text?: string
    message?: string
    phase?: string
    id?: string
    name?: string
  }
  try {
    payload = JSON.parse(data)
  } catch {
    return
  }
  if (event === 'delta') handlers.onDelta(payload.t ?? '')
  else if (event === 'thinking') handlers.onThinking?.(payload.id ?? '', payload.t ?? '')
  else if (event === 'tool')
    handlers.onTool?.({
      phase: (payload.phase as ToolStreamEvent['phase']) ?? 'end',
      id: payload.id ?? '',
      name: payload.name,
      t: payload.t
    })
  else if (event === 'done') handlers.onDone(payload.sessionId ?? '', payload.text ?? '')
  else if (event === 'error') handlers.onError(payload.message ?? 'AI 服务异常')
}

// 该 Agent 当前会话的历史消息（打开 Agent 时加载之前的对话）
export async function listAgentMessages(agentId: string): Promise<ChatMessage[]> {
  if (USE_MOCK) return delay([])
  return http.get<unknown, ChatMessage[]>(`/ai/agents/${agentId}/messages`)
}
