<script setup lang="ts">
import { computed, nextTick, onMounted, onUnmounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { confirm } from '@/composables/useConfirm'
import {
  getConversation,
  listMessages,
  sendMessage,
  presignUpload,
  uploadToStorage,
  sendFileMessage,
  markRead,
  recallMessage,
  getReadStatus,
  decodeIncoming,
  type Conversation,
  type Message
} from '@/api/im'
import { chatSocket } from '@/api/ws'
import { useAuthStore } from '@/stores/auth'
import GroupPanel from '@/components/GroupPanel.vue'
import MessageContent from '@/components/MessageContent.vue'
import { usePresenceStore } from '@/stores/presence'
import { useFriendStore } from '@/stores/friends'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()
const conversationId = computed(() => String(route.params.id))

const messages = ref<Message[]>([])
const draft = ref('')
const composerEl = ref<HTMLTextAreaElement | null>(null)
const loading = ref(true)
const sending = ref(false)
const uploading = ref(false)
const uploadPct = ref(0)
const fileInput = ref<HTMLInputElement | null>(null)
const MAX_FILE_SIZE = 200 * 1024 * 1024 // 200MB，与后端 nook.storage.max-file-size 对齐
const conv = ref<Conversation | null>(null)
const showGroupPanel = ref(false)
const scroller = ref<HTMLDivElement | null>(null)

const presence = usePresenceStore()
const friendStore = useFriendStore()
const isGroup = computed(() => conv.value?.type === 2)
const peerOnline = computed(() => presence.isOnline(conv.value?.peerId))
// 单聊设的好友备注（无则 undefined）
const peerRemark = computed(() =>
  conv.value?.type === 1 ? friendStore.remarkFor(conv.value?.peerId) : undefined
)
// 单聊标题优先用好友备注（微信式），其余回退到会话名
const title = computed(() => peerRemark.value || conv.value?.name || `会话 #${conversationId.value}`)
// 设了备注时，标题旁附带原昵称（conv.name 即对端真实昵称）
const peerNickname = computed(() => (peerRemark.value ? conv.value?.name : ''))
const subtitle = computed(() => {
  if (conv.value?.type === 2) return `${conv.value.members ?? 0} 位成员`
  return peerOnline.value ? '在线' : '离线'
})

const myInitial = computed(() => {
  const name = auth.user?.nickname || auth.user?.username || '?'
  return name[0].toUpperCase()
})

// 消息分组：连续同发送者 + 同分钟 → 合并为一组
interface GroupedMessage extends Message {
  isFirst: boolean
  isLast: boolean
  showTime: boolean
}

const grouped = computed<GroupedMessage[]>(() => {
  return messages.value.map((m, i) => {
    const prev = i > 0 ? messages.value[i - 1] : null
    const next = i < messages.value.length - 1 ? messages.value[i + 1] : null
    const sameSenderAsPrev = !m.system && !prev?.system && prev?.senderId === m.senderId && prev?.createdAt === m.createdAt
    const sameSenderAsNext = !m.system && !next?.system && next?.senderId === m.senderId && next?.createdAt === m.createdAt
    const isFirst = !sameSenderAsPrev
    const isLast = !sameSenderAsNext
    // 同组只有首条显示时间；不同组间如果时间变了也显示
    const showTime = isFirst && (!prev || prev.createdAt !== m.createdAt || prev.senderId !== m.senderId)
    return { ...m, isFirst, isLast, showTime }
  })
})

async function load() {
  loading.value = true
  try {
    const [c, msgs] = await Promise.all([
      getConversation(conversationId.value),
      listMessages(conversationId.value)
    ])
    conv.value = c
    messages.value = msgs
    await scrollBottom()
    syncRead()
  } finally {
    loading.value = false
  }
}

async function reloadConv() {
  try {
    conv.value = await getConversation(conversationId.value)
  } catch {
    /* ignore */
  }
}

function onGroupLeft() {
  showGroupPanel.value = false
  router.push({ name: 'chat' })
}

function syncRead() {
  const last = messages.value[messages.value.length - 1]
  if (last) markRead(conversationId.value, last.id).catch(() => {})
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
    if (!messages.value.some((m) => m.id === msg.id)) messages.value.push(msg)
    draft.value = ''
    await scrollBottom()
  } catch (e: any) {
    ElMessage.error(e?.message ?? '发送失败')
  } finally {
    sending.value = false
    // 发送完成（textarea 因 disabled 失焦）后重新聚焦，方便连续输入
    await nextTick()
    composerEl.value?.focus()
  }
}

function pickFile() {
  if (uploading.value) return
  fileInput.value?.click()
}

async function onFileChange(e: Event) {
  const input = e.target as HTMLInputElement
  const file = input.files?.[0]
  input.value = '' // 清空以便再次选择同一文件
  if (!file) return
  if (file.size > MAX_FILE_SIZE) {
    ElMessage.error('文件超过 200MB 上限')
    return
  }
  uploading.value = true
  uploadPct.value = 0
  try {
    const presigned = await presignUpload(file)
    await uploadToStorage(presigned.uploadUrl, file, (p) => (uploadPct.value = p))
    const msg = await sendFileMessage(conversationId.value, {
      fileUrl: presigned.downloadUrl,
      fileName: file.name,
      fileSize: file.size,
      mediaType: presigned.mediaType
    })
    if (!messages.value.some((m) => m.id === msg.id)) messages.value.push(msg)
    await scrollBottom()
  } catch (e: any) {
    ElMessage.error(e?.message ?? '发送失败')
  } finally {
    uploading.value = false
  }
}

async function onRecall(m: Message) {
  const ok = await confirm({ message: '撤回这条消息？', confirmText: '撤回', danger: true })
  if (!ok) return
  try {
    await recallMessage(m.id)
    m.recalled = true
    m.content = '[消息已撤回]'
  } catch (e: any) {
    ElMessage.error(e?.message ?? '撤回失败')
  }
}

// ───── 群已读人数（按需拉取）─────
interface ReadInfo {
  loading: boolean
  readCount: number
  total: number
}
const readMap = ref<Record<string, ReadInfo>>({})

async function loadRead(m: Message) {
  if (readMap.value[m.id]?.loading) return
  readMap.value = { ...readMap.value, [m.id]: { loading: true, readCount: 0, total: 0 } }
  try {
    const s = await getReadStatus(m.id)
    readMap.value = { ...readMap.value, [m.id]: { loading: false, readCount: s.readCount, total: s.totalRecipients } }
  } catch (e: any) {
    const next = { ...readMap.value }
    delete next[m.id]
    readMap.value = next
    ElMessage.error(e?.message ?? '获取已读状态失败')
  }
}

function readLabel(info: ReadInfo): string {
  if (info.total === 0) return '暂无其他成员'
  if (info.readCount >= info.total) return '全部已读'
  return `${info.readCount}/${info.total} 已读`
}

function readIsAll(info: ReadInfo): boolean {
  return info.total > 0 && info.readCount >= info.total
}

function onKeydown(e: KeyboardEvent) {
  if (e.key === 'Enter' && !e.shiftKey) {
    e.preventDefault()
    onSend()
  }
}

// ───── WebSocket 实时 ─────
async function onPush(frame: { data?: unknown }) {
  const raw = frame.data as { conversationId?: string } | undefined
  if (!raw || raw.conversationId !== conversationId.value) return
  const msg = await decodeIncoming(raw)
  if (messages.value.some((m) => m.id === msg.id)) return
  messages.value.push(msg)
  await scrollBottom()
  syncRead()
}

function onRecallPush(frame: { data?: unknown }) {
  const d = frame.data as { conversationId?: string; messageId?: string } | undefined
  if (!d || d.conversationId !== conversationId.value) return
  const m = messages.value.find((x) => x.id === d.messageId)
  if (m) {
    m.recalled = true
    m.content = '[消息已撤回]'
  }
}

let offMessage: (() => void) | null = null
let offRecall: (() => void) | null = null

watch(conversationId, load)
onMounted(() => {
  load()
  friendStore.loadRemarks() // 备注表用于单聊标题展示
  offMessage = chatSocket.on('message', onPush)
  offRecall = chatSocket.on('recall', onRecallPush)
})
onUnmounted(() => {
  offMessage?.()
  offRecall?.()
})
</script>

<template>
  <div class="room">
    <header class="room-head">
      <div class="room-left">
        <span class="head-avatar">{{ (title?.[0] ?? '?').toUpperCase() }}</span>
        <div class="room-title">
          <h3>{{ title }}<span v-if="peerNickname" class="nick-suffix">（{{ peerNickname }}）</span></h3>
          <span class="sub">
            <span v-if="!isGroup" class="online-dot" :class="{ off: !peerOnline }" />
            {{ subtitle }}
          </span>
        </div>
      </div>
      <div class="room-actions">
        <button class="icon-btn" type="button" aria-label="电话">
          <svg viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"><path d="M22 16.92v3a2 2 0 0 1-2.18 2 19.79 19.79 0 0 1-8.63-3.07 19.5 19.5 0 0 1-6-6 19.79 19.79 0 0 1-3.07-8.67A2 2 0 0 1 4.11 2h3a2 2 0 0 1 2 1.72c.13.96.34 1.9.63 2.81a2 2 0 0 1-.45 2.11L8.09 9.91a16 16 0 0 0 6 6l1.27-1.27a2 2 0 0 1 2.11-.45c.91.29 1.85.5 2.81.63A2 2 0 0 1 22 16.92Z" /></svg>
        </button>
        <button class="icon-btn" type="button" aria-label="视频">
          <svg viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"><polygon points="23 7 16 12 23 17 23 7" /><rect x="1" y="5" width="15" height="14" rx="2" /></svg>
        </button>
        <button v-if="isGroup" class="icon-btn" type="button" aria-label="群设置" @click="showGroupPanel = true">
          <svg viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="currentColor" stroke-width="1.8"><circle cx="12" cy="5" r="1.5" /><circle cx="12" cy="12" r="1.5" /><circle cx="12" cy="19" r="1.5" /></svg>
        </button>
      </div>
    </header>

    <div ref="scroller" class="messages" :aria-busy="loading || undefined" role="log" aria-live="polite">
      <el-skeleton v-if="loading" :rows="4" animated />
      <template v-else>
        <template v-for="m in grouped" :key="m.id">
          <div v-if="m.system" class="sys-msg"><span>{{ m.content }}</span></div>
          <template v-else>
          <div v-if="m.showTime && m.isFirst" class="time-divider">
            <span>{{ m.createdAt }}</span>
          </div>
          <div :class="['msg', {
            mine: m.mine,
            'group-first': m.isFirst,
            'group-mid': !m.isFirst && !m.isLast,
            'group-last': !m.isFirst && m.isLast,
            'group-solo': m.isFirst && m.isLast
          }]">
            <!-- 头像：只在组首条显示，否则占位 -->
            <span v-if="m.isFirst" class="msg-avatar">
              {{ m.mine ? myInitial : (m.senderName?.[0] ?? '?').toUpperCase() }}
            </span>
            <span v-else class="msg-avatar-spacer" />

            <div class="msg-body">
              <div v-if="m.isFirst" class="msg-meta">
                <span class="msg-name">{{ m.mine ? auth.displayName : m.senderName }}</span>
              </div>
              <div class="bubble-wrap">
                <div :class="['bubble', { recalled: m.recalled }]"><MessageContent :message="m" /></div>
                <button
                  v-if="m.mine && !m.recalled"
                  class="recall-btn"
                  type="button"
                  aria-label="撤回消息"
                  @click="onRecall(m)"
                >
                  <svg viewBox="0 0 24 24" width="14" height="14" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                    <polyline points="1 4 1 10 7 10" /><path d="M3.51 15a9 9 0 1 0 2.13-9.36L1 10" />
                  </svg>
                </button>
              </div>
              <div v-if="m.mine && isGroup && !m.recalled" class="read-meta">
                <button
                  class="read-trigger"
                  :class="{ 'read-all': readMap[m.id] && !readMap[m.id].loading && readIsAll(readMap[m.id]) }"
                  type="button"
                  :disabled="readMap[m.id]?.loading"
                  @click="loadRead(m)"
                >
                  <template v-if="!readMap[m.id]">查看已读</template>
                  <template v-else-if="readMap[m.id].loading">查看中…</template>
                  <template v-else>{{ readLabel(readMap[m.id]) }}</template>
                </button>
              </div>
            </div>
          </div>
          </template>
        </template>
      </template>
    </div>

    <footer class="composer">
      <input
        ref="fileInput"
        type="file"
        class="file-hidden"
        accept="image/*,video/*,audio/*,.pdf,.doc,.docx,.xls,.xlsx,.ppt,.pptx,.txt,.zip,.rar,.7z"
        @change="onFileChange"
      />
      <button
        class="attach-btn"
        type="button"
        :disabled="uploading || sending"
        :aria-busy="uploading || undefined"
        title="发送图片 / 视频 / 文件"
        @click="pickFile"
      >
        <svg v-if="!uploading" viewBox="0 0 24 24" width="20" height="20" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round">
          <line x1="12" y1="5" x2="12" y2="19" /><line x1="5" y1="12" x2="19" y2="12" />
        </svg>
        <span v-else class="up-pct">{{ uploadPct }}%</span>
      </button>
      <textarea
        ref="composerEl"
        v-model="draft"
        rows="1"
        placeholder="输入消息，Enter 发送 · Shift+Enter 换行"
        :disabled="sending"
        @keydown="onKeydown"
      />
      <button
        class="send-btn"
        :disabled="!draft.trim() || sending"
        :aria-busy="sending || undefined"
        @click="onSend"
      >
        <svg v-if="!sending" viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round">
          <line x1="22" y1="2" x2="11" y2="13" /><polygon points="22 2 15 22 11 13 2 9 22 2" />
        </svg>
        <span v-if="!sending">发送</span>
        <span v-else class="sending-dot"><i /><i /><i /></span>
      </button>
    </footer>

    <GroupPanel
      v-if="conv && conv.type === 2"
      v-model="showGroupPanel"
      :conv="conv"
      @refresh="reloadConv"
      @left="onGroupLeft"
    />
  </div>
</template>

<style scoped>
.room {
  display: flex;
  flex-direction: column;
  height: 100%;
  min-height: 0;
}

/* ───── Header ───── */
.room-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 14px 24px;
  border-bottom: 1px solid var(--nook-surface-border);
  background: var(--nook-surface);
  backdrop-filter: blur(16px) saturate(140%);
  -webkit-backdrop-filter: blur(16px) saturate(140%);
}
.room-left {
  display: flex;
  align-items: center;
  gap: 12px;
  min-width: 0;
}
.head-avatar {
  flex-shrink: 0;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 38px;
  height: 38px;
  border-radius: var(--r-sm);
  background: var(--nook-gradient-brand);
  color: #fff;
  font-family: var(--nook-font-display);
  font-weight: 700;
  font-size: 15px;
  box-shadow: var(--shadow-sm);
}
.room-title h3 {
  margin: 0;
  font-family: var(--nook-font-display);
  font-size: 16px;
  font-weight: 700;
  color: var(--nook-text);
}
.nick-suffix {
  margin-left: 6px;
  font-family: var(--nook-font-sans);
  font-size: 12.5px;
  font-weight: 400;
  color: var(--nook-text-muted);
}
.sub {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-size: 12px;
  color: var(--nook-text-muted);
}
.online-dot {
  width: 7px;
  height: 7px;
  border-radius: 50%;
  background: #10b981;
  box-shadow: 0 0 0 2px rgba(16, 185, 129, 0.2);
}
.online-dot.off {
  background: #94a3b8;
  box-shadow: 0 0 0 2px rgba(148, 163, 184, 0.2);
}
.room-actions {
  display: flex;
  gap: 6px;
}
.icon-btn {
  display: inline-flex;
  width: 36px;
  height: 36px;
  align-items: center;
  justify-content: center;
  border-radius: 10px;
  border: 1px solid var(--nook-surface-border);
  background: transparent;
  color: var(--nook-text);
  cursor: pointer;
  transition: background 200ms ease-out, border-color 200ms ease-out, color 200ms ease-out;
}
.icon-btn:hover {
  background: rgba(20, 184, 166, 0.1);
  border-color: var(--nook-primary);
  color: var(--nook-primary-deep);
}
.icon-btn:focus-visible {
  outline: 2px solid var(--nook-primary);
  outline-offset: 2px;
}
html.dark .icon-btn:hover { color: var(--nook-primary-soft); }

/* ───── Messages area ───── */
.messages {
  flex: 1;
  overflow-y: auto;
  padding: 16px 24px 20px;
  display: flex;
  flex-direction: column;
  gap: 2px;
}
.messages::-webkit-scrollbar { width: 5px; }
.messages::-webkit-scrollbar-thumb {
  background: rgba(20, 184, 166, 0.2);
  border-radius: 3px;
}
.messages::-webkit-scrollbar-thumb:hover {
  background: rgba(20, 184, 166, 0.35);
}

/* ───── Time divider ───── */
.time-divider {
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 12px 0 6px;
}
.time-divider span {
  font-size: 11px;
  font-weight: 500;
  color: var(--nook-text-muted);
  background: rgba(20, 184, 166, 0.06);
  padding: 3px 12px;
  border-radius: 10px;
  letter-spacing: 0.02em;
}

/* ───── 系统消息（群成员变更）───── */
.sys-msg {
  display: flex;
  justify-content: center;
  padding: 8px 0;
}
.sys-msg span {
  max-width: 80%;
  font-size: 11.5px;
  color: var(--nook-text-muted);
  background: rgba(20, 184, 166, 0.06);
  padding: 4px 12px;
  border-radius: 10px;
  text-align: center;
  line-height: 1.5;
}

/* ───── Message row ───── */
.msg {
  display: flex;
  gap: 10px;
  max-width: 72%;
  padding: 1px 0;
  animation: msgIn 200ms ease-out;
}
.msg.mine {
  margin-left: auto;
  flex-direction: row-reverse;
}

/* 组首条增加上间距与其他组分开 */
.msg.group-first {
  margin-top: 10px;
}
.msg.group-first:first-child,
.msg.group-first:has(+ .time-divider) {
  margin-top: 0;
}
/* 时间分隔线后的首条不需要额外 margin */
.time-divider + .msg.group-first {
  margin-top: 4px;
}

/* ───── Avatar ───── */
.msg-avatar {
  flex-shrink: 0;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 34px;
  height: 34px;
  border-radius: 50%;
  background: linear-gradient(135deg, #14b8a6, #0d9488);
  color: #fff;
  font-weight: 600;
  font-family: var(--nook-font-display);
  font-size: 14px;
  box-shadow: 0 2px 8px -2px rgba(13, 148, 136, 0.3);
  user-select: none;
}
.msg.mine .msg-avatar {
  background: linear-gradient(135deg, #0f766e, #134e4a);
}
.msg-avatar-spacer {
  flex-shrink: 0;
  width: 34px;
}

/* ───── Body ───── */
.msg-body {
  display: flex;
  flex-direction: column;
  gap: 3px;
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
  padding: 0 2px;
}
.msg-name {
  font-weight: 500;
}

/* ───── Bubble ───── */
.bubble-wrap {
  display: flex;
  align-items: center;
  gap: 6px;
}
.msg.mine .bubble-wrap {
  flex-direction: row-reverse;
}

.bubble {
  padding: 10px 14px;
  font-size: 14px;
  line-height: 1.55;
  color: var(--nook-text);
  word-break: break-word;
  white-space: pre-wrap;
  background: var(--nook-surface);
  border: 1px solid var(--nook-surface-border);
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.04);
  max-width: 100%;
}

/* 气泡圆角 — 根据分组位置动态调整，形成对话流 */
/* 对方消息（左侧）*/
.msg:not(.mine) .bubble {
  border-radius: 4px 18px 18px 18px;
}
.msg:not(.mine).group-first .bubble {
  border-radius: 18px 18px 18px 4px;
}
.msg:not(.mine).group-mid .bubble {
  border-radius: 4px 18px 18px 4px;
}
.msg:not(.mine).group-last .bubble {
  border-radius: 4px 18px 18px 18px;
}
.msg:not(.mine).group-solo .bubble {
  border-radius: 18px 18px 18px 4px;
}

/* 自己消息（右侧）*/
.msg.mine .bubble {
  background: var(--nook-bubble-mine-bg);
  border-color: var(--nook-bubble-mine-border);
  color: var(--nook-bubble-mine-fg);
  box-shadow: var(--nook-bubble-mine-shadow);
  border-radius: 18px 4px 4px 18px;
}
.msg.mine.group-first .bubble {
  border-radius: 18px 18px 4px 18px;
}
.msg.mine.group-mid .bubble {
  border-radius: 18px 4px 4px 18px;
}
.msg.mine.group-last .bubble {
  border-radius: 18px 4px 18px 18px;
}
.msg.mine.group-solo .bubble {
  border-radius: 18px 18px 4px 18px;
}

.bubble.recalled {
  font-style: italic;
  opacity: 0.55;
  font-size: 13px;
}

/* ───── 群已读指示 ───── */
.read-meta {
  margin-top: 3px;
  padding: 0 2px;
}
.read-trigger {
  border: none;
  background: transparent;
  color: var(--nook-text-muted);
  font: inherit;
  font-size: 10.5px;
  cursor: pointer;
  padding: 0;
  transition: color 150ms ease;
}
.read-trigger:hover:not(:disabled) {
  color: var(--nook-primary-deep);
}
.read-trigger.read-all {
  color: #10b981;
}
.read-trigger:disabled {
  cursor: default;
}
html.dark .read-trigger:hover:not(:disabled) {
  color: var(--nook-primary-soft);
}

/* ───── Recall button ───── */
.recall-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 28px;
  height: 28px;
  border: none;
  border-radius: 50%;
  background: transparent;
  color: var(--nook-text-muted);
  cursor: pointer;
  opacity: 0;
  transition: opacity 150ms ease-out, color 150ms ease-out, background 150ms ease-out;
  flex-shrink: 0;
}
.msg:hover .recall-btn {
  opacity: 0.6;
}
.msg:hover .recall-btn:hover {
  opacity: 1;
  color: #ef4444;
  background: rgba(239, 68, 68, 0.08);
}
.recall-btn:focus-visible {
  opacity: 1;
  outline: 2px solid #ef4444;
  outline-offset: 2px;
}

/* ───── Composer ───── */
.composer {
  display: flex;
  align-items: flex-end;
  gap: 10px;
  padding: 12px 24px 16px;
  border-top: 1px solid var(--nook-surface-border);
  background: var(--nook-surface);
  backdrop-filter: blur(16px) saturate(140%);
  -webkit-backdrop-filter: blur(16px) saturate(140%);
}
.file-hidden {
  display: none;
}
.attach-btn {
  flex-shrink: 0;
  width: 44px;
  height: 44px;
  border-radius: 14px;
  border: 1px solid var(--nook-surface-border);
  background: transparent;
  color: var(--nook-text-muted);
  display: inline-flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  transition: color 150ms ease, border-color 150ms ease;
}
.attach-btn:hover:not(:disabled) {
  color: var(--nook-primary);
  border-color: var(--nook-primary);
}
.attach-btn:disabled {
  cursor: not-allowed;
  opacity: 0.55;
}
.up-pct {
  font-size: 11px;
  font-weight: 700;
  color: var(--nook-primary);
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
  transition: border-color 200ms ease-out, box-shadow 200ms ease-out;
}
html.dark .composer textarea {
  background: rgba(4, 47, 46, 0.5);
}
.composer textarea:focus {
  border-color: var(--nook-primary);
  box-shadow: 0 0 0 3px rgba(20, 184, 166, 0.1);
}
.send-btn {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  height: 44px;
  padding: 0 18px;
  border-radius: 14px;
  border: none;
  background: linear-gradient(135deg, #14b8a6, #0d9488);
  color: #fff;
  font-family: var(--nook-font-display);
  font-weight: 600;
  font-size: 14px;
  cursor: pointer;
  transition: filter 200ms ease-out, transform 200ms ease-out, box-shadow 200ms ease-out;
  box-shadow: 0 2px 10px -3px rgba(20, 184, 166, 0.4);
}
.send-btn:hover:not(:disabled) {
  filter: brightness(1.06);
  box-shadow: 0 4px 14px -3px rgba(20, 184, 166, 0.5);
}
.send-btn:active:not(:disabled) {
  transform: translateY(1px);
}
.send-btn:focus-visible {
  outline: 2px solid var(--nook-primary);
  outline-offset: 2px;
}
.send-btn:disabled {
  cursor: not-allowed;
  opacity: 0.45;
  box-shadow: none;
}

/* ───── Sending indicator ───── */
.sending-dot {
  display: inline-flex;
  gap: 4px;
  align-items: center;
}
.sending-dot i {
  width: 5px;
  height: 5px;
  border-radius: 50%;
  background: currentColor;
  animation: bounce 1s infinite ease-in-out;
}
.sending-dot i:nth-child(2) { animation-delay: 0.15s; }
.sending-dot i:nth-child(3) { animation-delay: 0.3s; }

/* ───── Animations ───── */
@keyframes msgIn {
  from {
    opacity: 0;
    transform: translateY(8px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}
@keyframes bounce {
  0%, 80%, 100% { transform: scale(0.6); opacity: 0.4; }
  40% { transform: scale(1); opacity: 1; }
}

@media (prefers-reduced-motion: reduce) {
  .msg { animation: none; }
  .icon-btn,
  .recall-btn,
  .send-btn,
  .composer textarea {
    transition: none;
  }
}
</style>
