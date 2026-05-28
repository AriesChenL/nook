<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { listConversations, type Conversation } from '@/api/im'
import { chatSocket } from '@/api/ws'

const route = useRoute()
const router = useRouter()

const conversations = ref<Conversation[]>([])
const loading = ref(true)
const keyword = ref('')

const filtered = computed(() => {
  const k = keyword.value.trim().toLowerCase()
  if (!k) return conversations.value
  return conversations.value.filter((c) => c.name.toLowerCase().includes(k))
})

const activeId = computed(() => {
  const id = route.params.id
  return typeof id === 'string' ? Number(id) : null
})

async function reload() {
  try {
    conversations.value = await listConversations()
  } finally {
    loading.value = false
  }
}

function previewOf(raw: { recalled?: number; contentType?: number; content?: string }): string {
  if (raw.recalled === 1) return '[消息已撤回]'
  if (raw.contentType === 2) return '[图片]'
  if (raw.contentType === 3) return '[文件]'
  return raw.content ?? ''
}

function nowHm(): string {
  return new Date().toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' })
}

async function onPush(frame: { data?: unknown }) {
  const raw = frame.data as
    | { conversationId?: number; recalled?: number; contentType?: number; content?: string }
    | undefined
  if (!raw?.conversationId) return
  const conv = conversations.value.find((c) => c.id === raw.conversationId)
  if (!conv) {
    // 新会话：直接整表刷新
    await reload()
    return
  }
  conv.lastMessage = previewOf(raw)
  conv.lastMessageAt = nowHm()
  if (activeId.value !== conv.id) conv.unread += 1
  // 置顶
  conversations.value = [conv, ...conversations.value.filter((c) => c.id !== conv.id)]
}

let offMessage: (() => void) | null = null

onMounted(() => {
  reload()
  offMessage = chatSocket.on('message', onPush)
})
onUnmounted(() => offMessage?.())

function open(c: Conversation) {
  c.unread = 0
  router.push({ name: 'chat-room', params: { id: c.id } })
}
</script>

<template>
  <div class="chat-shell">
    <aside class="conv-pane">
      <header class="pane-head">
        <h2>消息</h2>
        <button class="head-btn" aria-label="发起会话">
          <svg viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round">
            <line x1="12" y1="5" x2="12" y2="19" /><line x1="5" y1="12" x2="19" y2="12" />
          </svg>
        </button>
      </header>

      <div class="search">
        <svg viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round">
          <circle cx="11" cy="11" r="7" /><line x1="21" y1="21" x2="16.65" y2="16.65" />
        </svg>
        <input v-model="keyword" type="text" placeholder="搜索会话" />
      </div>

      <div class="conv-list" :aria-busy="loading || undefined">
        <el-skeleton v-if="loading" :rows="6" animated />
        <button
          v-for="c in filtered"
          v-else
          :key="c.id"
          type="button"
          :class="['conv-item', { active: activeId === c.id }]"
          @click="open(c)"
        >
          <span class="conv-avatar" :data-type="c.type">
            <svg v-if="c.type === 2" viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="currentColor" stroke-width="1.8">
              <circle cx="9" cy="8" r="3" /><circle cx="17" cy="9" r="2.5" /><path d="M3 19a6 6 0 0 1 12 0M13 19a5 5 0 0 1 8 0" stroke-linecap="round" />
            </svg>
            <span v-else>{{ c.name[0]?.toUpperCase() }}</span>
          </span>
          <div class="conv-body">
            <div class="conv-row">
              <span class="conv-name">{{ c.name }}<span v-if="c.type === 2 && c.members" class="conv-count">·{{ c.members }}</span></span>
              <span class="conv-time">{{ c.lastMessageAt }}</span>
            </div>
            <div class="conv-row">
              <span class="conv-msg">{{ c.lastMessage }}</span>
              <span v-if="c.unread > 0" class="conv-unread">{{ c.unread > 99 ? '99+' : c.unread }}</span>
            </div>
          </div>
        </button>
      </div>
    </aside>

    <section class="room-pane">
      <router-view v-if="activeId !== null" :key="activeId" />
      <div v-else class="room-empty">
        <img src="/logo.svg" alt="" width="64" height="64" />
        <p>选择左侧会话开始聊天</p>
        <p class="hint">Nook · 即时聊天 + AI</p>
      </div>
    </section>
  </div>
</template>

<style scoped>
.chat-shell {
  display: grid;
  grid-template-columns: 320px 1fr;
  height: 100vh;
  height: 100dvh;
  min-height: 0;
}
@media (max-width: 900px) {
  .chat-shell {
    grid-template-columns: 1fr;
  }
}

.conv-pane {
  display: flex;
  flex-direction: column;
  border-right: 1px solid var(--nook-surface-border);
  background: var(--nook-surface);
  backdrop-filter: blur(14px) saturate(140%);
  -webkit-backdrop-filter: blur(14px) saturate(140%);
  min-height: 0;
}

.pane-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 20px 16px 8px;
}
.pane-head h2 {
  margin: 0;
  font-family: var(--nook-font-display);
  font-size: 22px;
  font-weight: 700;
  color: var(--nook-text);
}
.head-btn {
  display: inline-flex;
  width: 32px;
  height: 32px;
  align-items: center;
  justify-content: center;
  border-radius: 10px;
  border: 1px solid var(--nook-surface-border);
  background: transparent;
  color: var(--nook-text);
  cursor: pointer;
  transition: background 180ms ease, border-color 180ms ease;
}
.head-btn:hover {
  background: rgba(20, 184, 166, 0.1);
  border-color: var(--nook-primary);
}

.search {
  position: relative;
  margin: 6px 16px 12px;
}
.search svg {
  position: absolute;
  left: 12px;
  top: 50%;
  transform: translateY(-50%);
  color: var(--nook-text-muted);
}
.search input {
  width: 100%;
  padding: 9px 12px 9px 34px;
  border-radius: 10px;
  border: 1px solid var(--nook-surface-border);
  background: rgba(255, 255, 255, 0.5);
  font: inherit;
  font-size: 13.5px;
  color: var(--nook-text);
  outline: none;
  transition: border-color 180ms ease, background 180ms ease;
}
html.dark .search input {
  background: rgba(4, 47, 46, 0.45);
}
.search input:focus {
  border-color: var(--nook-primary);
  background: rgba(255, 255, 255, 0.75);
}

.conv-list {
  flex: 1;
  overflow-y: auto;
  padding: 4px 8px 12px;
}
.conv-list::-webkit-scrollbar { width: 6px; }
.conv-list::-webkit-scrollbar-thumb { background: rgba(20, 184, 166, 0.25); border-radius: 3px; }

.conv-item {
  display: flex;
  width: 100%;
  align-items: center;
  gap: 12px;
  padding: 10px 10px;
  border: none;
  border-radius: 12px;
  background: transparent;
  cursor: pointer;
  text-align: left;
  font: inherit;
  transition: background 150ms ease;
}
.conv-item + .conv-item { margin-top: 2px; }
.conv-item:hover {
  background: rgba(20, 184, 166, 0.08);
}
.conv-item.active {
  background: linear-gradient(135deg, rgba(20, 184, 166, 0.18), rgba(251, 146, 60, 0.1));
}

.conv-avatar {
  flex-shrink: 0;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 42px;
  height: 42px;
  border-radius: 12px;
  background: linear-gradient(135deg, #14b8a6, #fb923c);
  color: #fff;
  font-weight: 700;
  font-family: var(--nook-font-display);
}
.conv-avatar[data-type='2'] {
  background: linear-gradient(135deg, #5eead4, #fbbf24);
}

.conv-body {
  flex: 1;
  min-width: 0;
}
.conv-row {
  display: flex;
  align-items: center;
  gap: 8px;
}
.conv-row + .conv-row {
  margin-top: 2px;
}
.conv-name {
  flex: 1;
  font-weight: 600;
  font-size: 14px;
  color: var(--nook-text);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.conv-count {
  margin-left: 4px;
  font-weight: 400;
  font-size: 12px;
  color: var(--nook-text-muted);
}
.conv-time {
  font-size: 11.5px;
  color: var(--nook-text-muted);
  flex-shrink: 0;
}
.conv-msg {
  flex: 1;
  font-size: 12.5px;
  color: var(--nook-text-muted);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.conv-unread {
  flex-shrink: 0;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-width: 18px;
  height: 18px;
  padding: 0 5px;
  border-radius: 999px;
  background: var(--nook-accent-deep);
  color: #fff;
  font-size: 11px;
  font-weight: 600;
}

.room-pane {
  display: flex;
  flex-direction: column;
  min-height: 0;
  min-width: 0;
}
.room-empty {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 10px;
  color: var(--nook-text-muted);
}
.room-empty img {
  border-radius: 16px;
  box-shadow: 0 20px 40px -16px rgba(15, 118, 110, 0.4);
  margin-bottom: 6px;
}
.room-empty p {
  margin: 0;
  font-size: 14px;
}
.room-empty .hint {
  font-family: var(--nook-font-display);
  font-size: 12px;
  letter-spacing: 0.05em;
  opacity: 0.7;
}
</style>
