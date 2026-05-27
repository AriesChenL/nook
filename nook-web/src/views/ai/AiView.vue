<script setup lang="ts">
import { nextTick, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { aiPresets, chatStream } from '@/api/ai'

interface ChatTurn {
  id: number
  role: 'user' | 'assistant'
  content: string
}

const turns = ref<ChatTurn[]>([])
const draft = ref('')
const streaming = ref(false)
const scroller = ref<HTMLDivElement | null>(null)

async function scrollBottom() {
  await nextTick()
  if (scroller.value) scroller.value.scrollTop = scroller.value.scrollHeight
}

async function ask(prompt: string) {
  if (streaming.value || !prompt.trim()) return
  const userTurn: ChatTurn = { id: Date.now(), role: 'user', content: prompt }
  const aiTurn: ChatTurn = { id: Date.now() + 1, role: 'assistant', content: '' }
  turns.value.push(userTurn, aiTurn)
  draft.value = ''
  streaming.value = true
  scrollBottom()
  try {
    for await (const chunk of chatStream(prompt)) {
      aiTurn.content += chunk
      scrollBottom()
    }
  } catch (e: any) {
    aiTurn.content += `\n\n⚠ 出错了：${e?.message ?? '未知错误'}`
    ElMessage.error('AI 请求失败')
  } finally {
    streaming.value = false
  }
}

function onSubmit() {
  ask(draft.value.trim())
}

function onKeydown(e: KeyboardEvent) {
  if (e.key === 'Enter' && !e.shiftKey) {
    e.preventDefault()
    onSubmit()
  }
}

function clearChat() {
  if (streaming.value) return
  turns.value = []
}
</script>

<template>
  <div class="ai">
    <header class="ai-head">
      <div class="title">
        <span class="logo">
          <img src="/logo.svg" alt="" width="28" height="28" />
        </span>
        <div>
          <h2>AI 助手</h2>
          <p>问点什么都行 — 写代码、整理思路、润色文案</p>
        </div>
      </div>
      <button class="ghost-btn" :disabled="streaming || !turns.length" @click="clearChat">
        <svg viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"><polyline points="3 6 5 6 21 6" /><path d="M19 6 l-1 14 a2 2 0 0 1 -2 2 H8 a2 2 0 0 1 -2 -2 L5 6" /><path d="M10 11v6M14 11v6" /></svg>
        清空会话
      </button>
    </header>

    <div ref="scroller" class="ai-body">
      <!-- 空态：欢迎 + 建议 prompt -->
      <div v-if="!turns.length" class="welcome">
        <div class="hero">
          <img src="/logo.svg" alt="" width="56" height="56" />
          <h3>嗨，我是 Nook AI</h3>
          <p>挑一个开始，或者直接告诉我你想做什么</p>
        </div>
        <div class="presets">
          <button v-for="p in aiPresets" :key="p" class="preset" @click="ask(p)">
            {{ p }}
          </button>
        </div>
      </div>

      <!-- 对话流 -->
      <div v-else class="turns">
        <div v-for="t in turns" :key="t.id" :class="['turn', t.role]">
          <span class="role-tag">{{ t.role === 'user' ? '我' : 'AI' }}</span>
          <div class="bubble">
            <pre v-if="t.content">{{ t.content }}</pre>
            <span v-else class="typing"><i /><i /><i /></span>
          </div>
        </div>
      </div>
    </div>

    <footer class="composer">
      <textarea
        v-model="draft"
        rows="1"
        placeholder="给 AI 提一个问题，Enter 发送 · Shift+Enter 换行"
        :disabled="streaming"
        @keydown="onKeydown"
      />
      <button class="send-btn" :disabled="!draft.trim() || streaming" @click="onSubmit">
        <svg v-if="!streaming" viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round">
          <line x1="22" y1="2" x2="11" y2="13" /><polygon points="22 2 15 22 11 13 2 9 22 2" />
        </svg>
        <span v-else class="typing"><i /><i /><i /></span>
        <span>{{ streaming ? '思考中…' : '发送' }}</span>
      </button>
    </footer>
  </div>
</template>

<style scoped>
.ai {
  display: flex;
  flex-direction: column;
  height: 100vh;
  height: 100dvh;
  min-height: 0;
}

.ai-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 16px 28px;
  border-bottom: 1px solid var(--nook-surface-border);
  background: var(--nook-surface);
  backdrop-filter: blur(12px);
}
.title {
  display: flex;
  align-items: center;
  gap: 14px;
}
.title .logo img {
  border-radius: 10px;
  box-shadow: 0 6px 14px -4px rgba(15, 118, 110, 0.4);
}
.title h2 {
  margin: 0;
  font-family: var(--nook-font-display);
  font-size: 18px;
  font-weight: 700;
  color: var(--nook-text);
}
.title p {
  margin: 2px 0 0;
  font-size: 12.5px;
  color: var(--nook-text-muted);
}
.ghost-btn {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  height: 32px;
  padding: 0 12px;
  border-radius: 10px;
  border: 1px solid var(--nook-surface-border);
  background: transparent;
  color: var(--nook-text);
  font: inherit;
  font-size: 12.5px;
  cursor: pointer;
  transition: border-color 180ms ease, color 180ms ease, background 180ms ease;
}
.ghost-btn:hover:not(:disabled) {
  border-color: #ef4444;
  color: #ef4444;
}
.ghost-btn:disabled { opacity: 0.5; cursor: not-allowed; }

.ai-body {
  flex: 1;
  overflow-y: auto;
  padding: 20px 28px 28px;
}
.ai-body::-webkit-scrollbar { width: 6px; }
.ai-body::-webkit-scrollbar-thumb { background: rgba(20, 184, 166, 0.25); border-radius: 3px; }

.welcome {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 26px;
  padding: 56px 16px 24px;
  text-align: center;
}
.hero img {
  border-radius: 16px;
  box-shadow: 0 20px 40px -16px rgba(15, 118, 110, 0.45);
  margin-bottom: 12px;
}
.hero h3 {
  margin: 0;
  font-family: var(--nook-font-display);
  font-size: 22px;
  font-weight: 700;
  background: linear-gradient(135deg, #0f766e, #fb923c);
  -webkit-background-clip: text;
  background-clip: text;
  color: transparent;
}
.hero p {
  margin: 6px 0 0;
  color: var(--nook-text-muted);
  font-size: 13.5px;
}
.presets {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 280px));
  gap: 10px;
}
@media (max-width: 640px) {
  .presets { grid-template-columns: 1fr; }
}
.preset {
  padding: 14px 16px;
  border-radius: 14px;
  border: 1px solid var(--nook-surface-border);
  background: var(--nook-surface);
  color: var(--nook-text);
  font: inherit;
  font-size: 13.5px;
  text-align: left;
  cursor: pointer;
  transition: border-color 180ms ease, transform 180ms ease, background 180ms ease;
}
.preset:hover {
  border-color: var(--nook-primary);
  transform: translateY(-1px);
}

.turns {
  display: flex;
  flex-direction: column;
  gap: 18px;
  max-width: 820px;
  margin: 0 auto;
}
.turn {
  display: flex;
  flex-direction: column;
  gap: 6px;
}
.turn.user { align-items: flex-end; }
.role-tag {
  font-family: var(--nook-font-display);
  font-size: 11.5px;
  letter-spacing: 0.05em;
  color: var(--nook-text-muted);
  padding: 0 4px;
}
.bubble {
  padding: 12px 16px;
  border-radius: 16px;
  max-width: 85%;
  border: 1px solid var(--nook-surface-border);
  background: var(--nook-surface);
}
.turn.user .bubble {
  background: linear-gradient(135deg, #14b8a6, #0f766e);
  border-color: transparent;
  color: #fff;
}
.bubble pre {
  margin: 0;
  font-family: var(--nook-font-sans);
  font-size: 14px;
  line-height: 1.6;
  color: inherit;
  white-space: pre-wrap;
  word-break: break-word;
}

.typing {
  display: inline-flex;
  gap: 4px;
  align-items: center;
}
.typing i {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: currentColor;
  animation: blink 1s infinite ease-in-out;
}
.typing i:nth-child(2) { animation-delay: 0.15s; }
.typing i:nth-child(3) { animation-delay: 0.3s; }
@keyframes blink {
  0%, 80%, 100% { opacity: 0.3; transform: scale(0.6); }
  40% { opacity: 1; transform: scale(1); }
}

.composer {
  display: flex;
  align-items: flex-end;
  gap: 10px;
  padding: 12px 28px 18px;
  border-top: 1px solid var(--nook-surface-border);
  background: var(--nook-surface);
  backdrop-filter: blur(12px);
}
.composer textarea {
  flex: 1;
  resize: none;
  min-height: 44px;
  max-height: 200px;
  padding: 11px 14px;
  border-radius: 14px;
  border: 1px solid var(--nook-surface-border);
  background: rgba(255, 255, 255, 0.6);
  font: inherit;
  font-size: 14px;
  line-height: 1.5;
  color: var(--nook-text);
  outline: none;
}
html.dark .composer textarea { background: rgba(4, 47, 46, 0.5); }
.composer textarea:focus { border-color: var(--nook-primary); }

.send-btn {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  height: 44px;
  padding: 0 16px;
  border-radius: 14px;
  border: none;
  background: linear-gradient(135deg, #14b8a6, #fb923c);
  color: #fff;
  font-family: var(--nook-font-display);
  font-weight: 600;
  font-size: 14px;
  cursor: pointer;
  transition: filter 180ms ease;
}
.send-btn:hover:not(:disabled) { filter: brightness(1.06); }
.send-btn:disabled { opacity: 0.6; cursor: not-allowed; }
</style>
