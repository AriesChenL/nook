<script setup lang="ts">
import { computed, nextTick, onMounted, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import { listConversations, listMessages, sendMessage, type Message } from '@/api/im'

const route = useRoute()
const conversationId = computed(() => Number(route.params.id))

const messages = ref<Message[]>([])
const draft = ref('')
const loading = ref(true)
const sending = ref(false)
const title = ref('')
const subtitle = ref('')
const scroller = ref<HTMLDivElement | null>(null)

async function load() {
  loading.value = true
  try {
    const [convs, msgs] = await Promise.all([
      listConversations(),
      listMessages(conversationId.value)
    ])
    const c = convs.find((x) => x.id === conversationId.value)
    title.value = c?.name ?? `会话 #${conversationId.value}`
    subtitle.value = c?.type === 2 ? `${c.members ?? 0} 位成员` : '在线'
    messages.value = msgs
    await scrollBottom()
  } finally {
    loading.value = false
  }
}

async function scrollBottom() {
  await nextTick()
  if (scroller.value) scroller.value.scrollTop = scroller.value.scrollHeight
}

async function onSend() {
  const text = draft.value.trim()
  if (!text || sending.value) return
  sending.value = true
  try {
    const msg = await sendMessage(conversationId.value, text)
    messages.value.push(msg)
    draft.value = ''
    await scrollBottom()
  } catch (e: any) {
    ElMessage.error(e?.message ?? '发送失败')
  } finally {
    sending.value = false
  }
}

function onKeydown(e: KeyboardEvent) {
  if (e.key === 'Enter' && !e.shiftKey) {
    e.preventDefault()
    onSend()
  }
}

watch(conversationId, load)
onMounted(load)
</script>

<template>
  <div class="room">
    <header class="room-head">
      <div class="room-title">
        <h3>{{ title }}</h3>
        <span class="sub">{{ subtitle }}</span>
      </div>
      <div class="room-actions">
        <button class="icon-btn" type="button" aria-label="电话">
          <svg viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"><path d="M22 16.92v3a2 2 0 0 1-2.18 2 19.79 19.79 0 0 1-8.63-3.07 19.5 19.5 0 0 1-6-6 19.79 19.79 0 0 1-3.07-8.67A2 2 0 0 1 4.11 2h3a2 2 0 0 1 2 1.72c.13.96.34 1.9.63 2.81a2 2 0 0 1-.45 2.11L8.09 9.91a16 16 0 0 0 6 6l1.27-1.27a2 2 0 0 1 2.11-.45c.91.29 1.85.5 2.81.63A2 2 0 0 1 22 16.92Z" /></svg>
        </button>
        <button class="icon-btn" type="button" aria-label="视频">
          <svg viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"><polygon points="23 7 16 12 23 17 23 7" /><rect x="1" y="5" width="15" height="14" rx="2" /></svg>
        </button>
        <button class="icon-btn" type="button" aria-label="更多">
          <svg viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="currentColor" stroke-width="1.8"><circle cx="12" cy="5" r="1.5" /><circle cx="12" cy="12" r="1.5" /><circle cx="12" cy="19" r="1.5" /></svg>
        </button>
      </div>
    </header>

    <div ref="scroller" class="messages" :aria-busy="loading || undefined">
      <el-skeleton v-if="loading" :rows="4" animated />
      <template v-else>
        <div v-for="m in messages" :key="m.id" :class="['msg', { mine: m.mine }]">
          <span class="msg-avatar">{{ (m.senderName?.[0] ?? '?').toUpperCase() }}</span>
          <div class="msg-body">
            <div class="msg-meta">
              <span class="msg-name">{{ m.mine ? '我' : m.senderName }}</span>
              <span class="msg-time">{{ m.createdAt }}</span>
            </div>
            <div class="bubble">{{ m.content }}</div>
          </div>
        </div>
      </template>
    </div>

    <footer class="composer">
      <textarea
        v-model="draft"
        rows="1"
        placeholder="输入消息，Enter 发送 · Shift+Enter 换行"
        @keydown="onKeydown"
      />
      <button class="send-btn" :disabled="!draft.trim() || sending" @click="onSend">
        <svg viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round">
          <line x1="22" y1="2" x2="11" y2="13" /><polygon points="22 2 15 22 11 13 2 9 22 2" />
        </svg>
        <span>发送</span>
      </button>
    </footer>
  </div>
</template>

<style scoped>
.room {
  display: flex;
  flex-direction: column;
  height: 100%;
  min-height: 0;
}

.room-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 14px 20px;
  border-bottom: 1px solid var(--nook-surface-border);
  background: var(--nook-surface);
  backdrop-filter: blur(12px);
}
.room-title h3 {
  margin: 0;
  font-family: var(--nook-font-display);
  font-size: 16px;
  font-weight: 700;
  color: var(--nook-text);
}
.sub {
  font-size: 12px;
  color: var(--nook-text-muted);
}
.room-actions {
  display: flex;
  gap: 6px;
}
.icon-btn {
  display: inline-flex;
  width: 34px;
  height: 34px;
  align-items: center;
  justify-content: center;
  border-radius: 10px;
  border: 1px solid var(--nook-surface-border);
  background: transparent;
  color: var(--nook-text);
  cursor: pointer;
  transition: background 180ms ease, border-color 180ms ease, color 180ms ease;
}
.icon-btn:hover {
  background: rgba(20, 184, 166, 0.1);
  border-color: var(--nook-primary);
  color: var(--nook-primary-deep);
}
html.dark .icon-btn:hover { color: var(--nook-primary-soft); }

.messages {
  flex: 1;
  overflow-y: auto;
  padding: 18px 24px;
  display: flex;
  flex-direction: column;
  gap: 14px;
}
.messages::-webkit-scrollbar { width: 6px; }
.messages::-webkit-scrollbar-thumb { background: rgba(20, 184, 166, 0.25); border-radius: 3px; }

.msg {
  display: flex;
  gap: 10px;
  max-width: 70%;
}
.msg.mine {
  margin-left: auto;
  flex-direction: row-reverse;
}
.msg-avatar {
  flex-shrink: 0;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 34px;
  height: 34px;
  border-radius: 10px;
  background: linear-gradient(135deg, #14b8a6, #fb923c);
  color: #fff;
  font-weight: 700;
  font-family: var(--nook-font-display);
  font-size: 13px;
}
.msg-body {
  display: flex;
  flex-direction: column;
  gap: 4px;
  min-width: 0;
}
.msg.mine .msg-body {
  align-items: flex-end;
}
.msg-meta {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 11.5px;
  color: var(--nook-text-muted);
}
.msg-name { font-weight: 500; }

.bubble {
  padding: 10px 14px;
  border-radius: 16px;
  background: var(--nook-surface);
  border: 1px solid var(--nook-surface-border);
  font-size: 14px;
  line-height: 1.55;
  color: var(--nook-text);
  word-break: break-word;
  white-space: pre-wrap;
}
.msg.mine .bubble {
  background: linear-gradient(135deg, #14b8a6, #0f766e);
  border-color: transparent;
  color: #fff;
}

.composer {
  display: flex;
  align-items: flex-end;
  gap: 10px;
  padding: 12px 18px 16px;
  border-top: 1px solid var(--nook-surface-border);
  background: var(--nook-surface);
  backdrop-filter: blur(12px);
}
.composer textarea {
  flex: 1;
  resize: none;
  min-height: 44px;
  max-height: 160px;
  padding: 11px 14px;
  border-radius: 14px;
  border: 1px solid var(--nook-surface-border);
  background: rgba(255, 255, 255, 0.6);
  font: inherit;
  font-size: 14px;
  line-height: 1.5;
  color: var(--nook-text);
  outline: none;
  transition: border-color 180ms ease;
}
html.dark .composer textarea {
  background: rgba(4, 47, 46, 0.5);
}
.composer textarea:focus {
  border-color: var(--nook-primary);
}
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
  transition: filter 180ms ease, transform 180ms ease;
}
.send-btn:hover:not(:disabled) { filter: brightness(1.06); }
.send-btn:active:not(:disabled) { transform: translateY(1px); }
.send-btn:disabled {
  cursor: not-allowed;
  opacity: 0.5;
}
</style>
