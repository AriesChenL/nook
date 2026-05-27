import { USE_MOCK, mockAiStream, mockAiPresets } from './_mock'

export const aiPresets = mockAiPresets

export async function* chatStream(prompt: string): AsyncGenerator<string> {
  if (USE_MOCK) {
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
