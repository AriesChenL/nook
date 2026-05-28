import { mockAiStream, mockAiPresets } from './_mock'

export const aiPresets = mockAiPresets

// nook-ai 后端尚未实现（仅 Application.java），AI 暂强制走 mock 流式。
// 后端接好 SSE 后把此开关改回 USE_MOCK 即可。
const AI_USE_MOCK = true

export async function* chatStream(prompt: string): AsyncGenerator<string> {
  if (AI_USE_MOCK) {
    yield* mockAiStream(prompt)
    return
  }
  // TODO: 接入后端 SSE — POST /api/ai/chat, body: { prompt }
  // 后端用 Spring AI + EventStream，前端用 fetch + ReadableStream 分块读
  const resp = await fetch('/api/ai/chat', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      Authorization: `Bearer ${localStorage.getItem('nook.token') ?? ''}`
    },
    body: JSON.stringify({ prompt })
  })
  if (!resp.body) return
  const reader = resp.body.getReader()
  const decoder = new TextDecoder()
  while (true) {
    const { value, done } = await reader.read()
    if (done) break
    yield decoder.decode(value)
  }
}
