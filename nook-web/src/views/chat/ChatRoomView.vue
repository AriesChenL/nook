<script setup lang="ts">
import { computed, nextTick, onMounted, onUnmounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { toast } from '@/composables/useToast'
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
import { updateFriendRemark } from '@/api/user'
import { chatSocket } from '@/api/ws'
import { useAuthStore } from '@/stores/auth'
import GroupPanel from '@/components/GroupPanel.vue'
import MessageContent from '@/components/MessageContent.vue'
import NookModal from '@/components/NookModal.vue'
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

// 待发图片（粘贴进来的）：先在输入区预览，点发送才上传，不直接发出
interface PendingImage {
  file: File
  url: string // objectURL，用于缩略图预览，发送/移除后释放
}
const pendingImages = ref<PendingImage[]>([])

function addPending(file: File) {
  if (file.size > MAX_FILE_SIZE) {
    toast.error('图片超过 200MB 上限')
    return
  }
  pendingImages.value.push({ file, url: URL.createObjectURL(file) })
}
function removePending(i: number) {
  const [removed] = pendingImages.value.splice(i, 1)
  if (removed) URL.revokeObjectURL(removed.url)
}
function clearPending() {
  pendingImages.value.forEach((p) => URL.revokeObjectURL(p.url))
  pendingImages.value = []
}
// 有文字或有待发图片即可发送
const canSend = computed(() => (!!draft.value.trim() || pendingImages.value.length > 0) && !sending.value && !uploading.value)

// ───── 输入框自动撑高：随内容增高，到上限后转为内部滚动（不无限拉高）─────
const COMPOSER_MAX_H = 176 // ≈ 7 行，超出则出现滚动条
function autoGrow() {
  const el = composerEl.value
  if (!el) return
  el.style.height = 'auto' // 先归零量出真实内容高度，才能正确收缩
  el.style.height = Math.min(el.scrollHeight, COMPOSER_MAX_H) + 'px'
}
// 文本变化（输入 / 发送后清空 / 粘贴文本）后重算高度；flush:post 确保 DOM 已更新
watch(draft, autoGrow, { flush: 'post' })

const presence = usePresenceStore()
const friendStore = useFriendStore()
const isGroup = computed(() => conv.value?.type === 2)
// 单聊（type===1）才有好友备注可编辑；群聊点标题打开群面板，互不干扰
const isDirect = computed(() => conv.value?.type === 1)
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

// ───── 单聊：从聊天头编辑好友备注 ─────
const showFriendPanel = ref(false)
const remarkDraft = ref('')
const savingRemark = ref(false)

function openFriendPanel() {
  if (!isDirect.value) return // 群聊无好友备注，点标题不开此面板
  remarkDraft.value = peerRemark.value ?? ''
  showFriendPanel.value = true
}

async function saveRemark() {
  const peerId = conv.value?.peerId
  if (!peerId || savingRemark.value) return
  const remark = remarkDraft.value.trim()
  savingRemark.value = true
  try {
    await updateFriendRemark(peerId, remark)
    // 写回全局备注表：标题别名 chip 与会话列表均响应式联动，无需重拉
    friendStore.setRemark(peerId, remark)
    showFriendPanel.value = false
    toast.success(remark ? '备注已更新' : '已清除备注')
  } catch (e: any) {
    toast.error(e?.message ?? '保存失败')
  } finally {
    savingRemark.value = false
  }
}

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
  if (sending.value || uploading.value) return
  const text = draft.value.trim()
  const imgs = pendingImages.value.map((p) => p.file)
  if (!text && !imgs.length) return
  sending.value = true
  try {
    // 先逐张发待发图片，再发文字
    for (const file of imgs) {
      await uploadAndSend(file)
    }
    clearPending()
    if (text) {
      const msg = await sendMessage(conversationId.value, text)
      if (!messages.value.some((m) => m.id === msg.id)) messages.value.push(msg)
      draft.value = ''
      await scrollBottom()
    }
  } catch (e: any) {
    toast.error(e?.message ?? '发送失败')
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

// 统一的「上传并作为文件消息发出」：文件选择与粘贴图片共用
async function uploadAndSend(file: File) {
  if (uploading.value) return
  if (file.size > MAX_FILE_SIZE) {
    toast.error('文件超过 200MB 上限')
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
    toast.error(e?.message ?? '发送失败')
  } finally {
    uploading.value = false
  }
}

async function onFileChange(e: Event) {
  const input = e.target as HTMLInputElement
  const file = input.files?.[0]
  input.value = '' // 清空以便再次选择同一文件
  if (file) await uploadAndSend(file)
}

// 输入框里 Ctrl/Cmd+V 粘贴图片 → 进入待发预览（不直接发出），点发送才上传
function onPaste(e: ClipboardEvent) {
  const items = e.clipboardData?.items
  if (!items) return
  let hit = false
  for (const item of items) {
    if (item.kind === 'file' && item.type.startsWith('image/')) {
      const blob = item.getAsFile()
      if (!blob) continue
      hit = true
      // 粘贴的图片往往没有文件名，补一个带时间戳的名字
      const ext = item.type.split('/')[1] || 'png'
      const named = blob.name ? blob : new File([blob], `pasted-${Date.now()}.${ext}`, { type: item.type })
      addPending(named)
    }
  }
  if (hit) e.preventDefault() // 命中图片才拦截，纯文本粘贴照常
}

// 撤回时限：与后端 nook-im RECALL_WINDOW 保持一致（5 分钟）。
// 前端仅用于不展示无效的撤回入口；真正的强校验在后端。
const RECALL_WINDOW_MS = 5 * 60 * 1000
// 用 now 心跳让超时的撤回按钮能在停留页面时自动消失（每 30s 走一次）
const now = ref(Date.now())
let recallTimer: ReturnType<typeof setInterval> | null = null

function canRecall(m: Message): boolean {
  return !!m.mine && !m.recalled && now.value - m.createdAtMs < RECALL_WINDOW_MS
}

async function onRecall(m: Message) {
  const ok = await confirm({ message: '撤回这条消息？', confirmText: '撤回', danger: true })
  if (!ok) return
  try {
    await recallMessage(m.id)
    m.recalled = true
    m.content = '[消息已撤回]'
  } catch (e: any) {
    toast.error(e?.message ?? '撤回失败')
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
    toast.error(e?.message ?? '获取已读状态失败')
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
  recallTimer = setInterval(() => (now.value = Date.now()), 30000) // 心跳：让超时撤回入口自动隐藏
  nextTick(autoGrow) // 初始化输入框高度
})
onUnmounted(() => {
  offMessage?.()
  offRecall?.()
  if (recallTimer) clearInterval(recallTimer)
  clearPending() // 释放预览 objectURL
})
</script>

<template>
  <div class="room">
    <header class="room-head">
      <div class="room-left">
        <span class="head-avatar">
          <img v-if="conv?.avatarUrl" :src="conv.avatarUrl" alt="" />
          <template v-else>{{ (title?.[0] ?? '?').toUpperCase() }}</template>
        </span>
        <div
          class="room-title"
          :class="{ 'room-title--clickable': isDirect }"
          :role="isDirect ? 'button' : undefined"
          :tabindex="isDirect ? 0 : undefined"
          :title="isDirect ? '设置好友备注' : undefined"
          @click="openFriendPanel"
          @keydown.enter.prevent="openFriendPanel"
          @keydown.space.prevent="openFriendPanel"
        >
          <div class="title-line">
            <h3>{{ title }}</h3>
            <span v-if="peerNickname" class="nk-alias nk-alias--lg" :title="`昵称：${peerNickname}`">
              <svg viewBox="0 0 24 24" width="12" height="12" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M20.59 13.41 11 3.82A2 2 0 0 0 9.59 3.24H4a1 1 0 0 0-1 1v5.59a2 2 0 0 0 .59 1.41l9.59 9.59a2 2 0 0 0 2.82 0l4.59-4.59a2 2 0 0 0 0-2.82Z" /><circle cx="7.5" cy="7.5" r="1.4" fill="currentColor" stroke="none" /></svg>
              <span class="nk-alias__name">{{ peerNickname }}</span>
            </span>
            <svg v-if="isDirect" class="title-edit-hint" viewBox="0 0 24 24" width="13" height="13" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"><path d="M11 4H4a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2v-7" /><path d="M18.5 2.5a2.12 2.12 0 0 1 3 3L12 15l-4 1 1-4Z" /></svg>
          </div>
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
      <div class="msg-flow">
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
              <img v-if="m.mine ? auth.user?.avatarUrl : m.senderAvatar" :src="(m.mine ? auth.user?.avatarUrl : m.senderAvatar) || ''" alt="" />
              <template v-else>{{ m.mine ? myInitial : (m.senderName?.[0] ?? '?').toUpperCase() }}</template>
            </span>
            <span v-else class="msg-avatar-spacer" />

            <div class="msg-body">
              <div v-if="m.isFirst" class="msg-meta">
                <span class="msg-name">{{ m.mine ? auth.displayName : m.senderName }}</span>
              </div>
              <div class="bubble-wrap">
                <div :class="['bubble', { recalled: m.recalled }]"><MessageContent :message="m" /></div>
                <button
                  v-if="canRecall(m)"
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
    </div>

    <footer class="composer">
      <div class="composer-inner">
      <!-- 粘贴的待发图片预览：卡片式，点发送才上传，可逐个移除 -->
      <div v-if="pendingImages.length" class="paste-tray">
        <div class="paste-tray__head">
          <svg viewBox="0 0 24 24" width="14" height="14" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"><rect x="3" y="3" width="18" height="18" rx="2" /><circle cx="8.5" cy="8.5" r="1.5" /><path d="m21 15-5-5L5 21" /></svg>
          <span>待发送图片</span>
          <span class="paste-tray__count">{{ pendingImages.length }}</span>
        </div>
        <div class="paste-tray__grid">
          <div v-for="(p, i) in pendingImages" :key="p.url" class="paste-card">
            <img :src="p.url" alt="待发图片" />
            <button class="paste-card__remove" type="button" aria-label="移除图片" @click="removePending(i)">
              <svg viewBox="0 0 24 24" width="13" height="13" fill="none" stroke="currentColor" stroke-width="2.4" stroke-linecap="round"><line x1="18" y1="6" x2="6" y2="18" /><line x1="6" y1="6" x2="18" y2="18" /></svg>
            </button>
          </div>
        </div>
      </div>

      <!-- 一体式输入条：附件、输入区、发送按钮包在同一个圆角框里 -->
      <div class="composer-box">
        <input
          ref="fileInput"
          type="file"
          class="file-hidden"
          accept="image/*,video/*,audio/*,.pdf,.doc,.docx,.xls,.xlsx,.ppt,.pptx,.txt,.zip,.rar,.7z"
          @change="onFileChange"
        />
        <button
          class="composer-tool"
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
          placeholder="输入消息，Enter 发送 · Shift+Enter 换行 · 可直接粘贴图片"
          :disabled="sending"
          @keydown="onKeydown"
          @paste="onPaste"
        />
        <button
          class="composer-send"
          :class="{ loading: sending }"
          :disabled="!canSend"
          :aria-busy="sending || undefined"
          aria-label="发送"
          @click="onSend"
        >
          <svg viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round">
            <line x1="22" y1="2" x2="11" y2="13" /><polygon points="22 2 15 22 11 13 2 9 22 2" />
          </svg>
        </button>
      </div>
      </div>
    </footer>

    <GroupPanel
      v-if="conv && conv.type === 2"
      v-model="showGroupPanel"
      :conv="conv"
      @refresh="reloadConv"
      @left="onGroupLeft"
    />

    <!-- 单聊：好友资料 + 备注编辑（复用项目自有 NookModal） -->
    <NookModal v-model="showFriendPanel" title="好友资料" :width="400">
      <div v-if="isDirect" class="friend-panel">
        <div class="friend-id">
          <span class="friend-avatar">{{ (conv?.name?.[0] ?? '?').toUpperCase() }}</span>
          <div class="friend-meta">
            <div class="friend-nickname">{{ conv?.name }}</div>
            <div class="friend-sub">
              <span class="online-dot" :class="{ off: !peerOnline }" />
              {{ peerOnline ? '在线' : '离线' }}
            </div>
          </div>
        </div>
        <label class="friend-field">
          <span class="friend-label">备注名</span>
          <input
            v-model="remarkDraft"
            class="remark-input"
            maxlength="64"
            placeholder="留空则恢复显示昵称"
            @keyup.enter="saveRemark"
          />
        </label>
        <p class="remark-hint">备注只有你能看到，会在聊天标题与会话列表中替代昵称。</p>
      </div>
      <template #footer>
        <button class="nk-btn nk-btn--ghost" type="button" @click="showFriendPanel = false">取消</button>
        <button class="nk-btn nk-btn--primary" type="button" :disabled="savingRemark" @click="saveRemark">
          {{ savingRemark ? '保存中…' : '保存' }}
        </button>
      </template>
    </NookModal>
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
  flex: 1 1 auto; /* 占满除右侧操作按钮外的空间，标题随之有合理宽度 */
}
.room-title {
  min-width: 0; /* 让内部 h3 能省略号截断，而不是把整条 header 撑开 */
}
/* 单聊：标题可点击进入好友资料/备注，给出克制的悬浮反馈 */
.room-title--clickable {
  margin: -4px -8px;
  padding: 4px 8px;
  border-radius: var(--r-sm);
  cursor: pointer;
  transition: background var(--dur) var(--ease-out);
}
.room-title--clickable:hover {
  background: color-mix(in srgb, var(--primary) 8%, transparent);
}
.room-title--clickable:focus-visible {
  outline: 2px solid var(--nook-primary);
  outline-offset: 1px;
}
/* 备注编辑提示图标：默认半隐，悬浮标题时浮现 */
.title-edit-hint {
  flex-shrink: 0;
  color: var(--nook-text-muted);
  opacity: 0;
  transition: opacity var(--dur) var(--ease-out);
}
.room-title--clickable:hover .title-edit-hint,
.room-title--clickable:focus-visible .title-edit-hint {
  opacity: 0.7;
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
  overflow: hidden;
}
.head-avatar img { width: 100%; height: 100%; object-fit: cover; }
.room-title h3 {
  margin: 0;
  font-family: var(--nook-font-display);
  font-size: 16px;
  font-weight: 700;
  color: var(--nook-text);
}
.title-line {
  display: flex;
  align-items: center;
  gap: 8px;
  min-width: 0;
}
.title-line h3 {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
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
  background: var(--success);
  box-shadow: 0 0 0 2px color-mix(in srgb, var(--success) 22%, transparent);
}
.online-dot.off {
  background: #94a3b8;
  box-shadow: 0 0 0 2px rgba(148, 163, 184, 0.2);
}
.room-actions {
  display: flex;
  gap: 6px;
  flex-shrink: 0; /* 操作按钮固定，标题长时优先截断标题 */
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
  background: color-mix(in srgb, var(--primary) 10%, transparent);
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
  padding: var(--space-4) var(--space-6) var(--space-5);
}
/* 居中阅读列：宽度=面板 92%、上限 940px（设计稿 .msgs-inner） */
.msg-flow {
  width: 92%;
  max-width: 940px;
  margin: 0 auto;
  display: flex;
  flex-direction: column;
  gap: 2px;
}
.messages::-webkit-scrollbar { width: 5px; }
.messages::-webkit-scrollbar-thumb {
  background: color-mix(in srgb, var(--primary) 20%, transparent);
  border-radius: 3px;
}
.messages::-webkit-scrollbar-thumb:hover {
  background: color-mix(in srgb, var(--primary) 35%, transparent);
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
  color: var(--ink-3);
  background: var(--surface-2);
  padding: 3px 12px;
  border-radius: var(--r-pill);
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
  color: var(--ink-3);
  background: var(--surface-2);
  padding: 4px 12px;
  border-radius: var(--r-pill);
  text-align: center;
  line-height: 1.5;
}

/* ───── Message row ───── */
.msg {
  display: flex;
  gap: 10px;
  max-width: 76%;
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
  border-radius: 38%;
  background: var(--grad-primary);
  color: var(--on-primary);
  font-weight: 600;
  font-family: var(--nook-font-display);
  font-size: 14px;
  box-shadow: var(--elev-1), inset 0 1px 0 rgba(255, 255, 255, 0.2);
  user-select: none;
  overflow: hidden;
}
.msg-avatar img { width: 100%; height: 100%; object-fit: cover; }
.msg.mine .msg-avatar {
  background: var(--grad-accent);
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
  color: var(--ink);
  word-break: break-word;
  white-space: pre-wrap;
  background: var(--surface);
  border: 1px solid var(--line);
  box-shadow: var(--elev-1), var(--inset-top);
  max-width: 100%;
}

/* 气泡圆角 — 柔和拟物对话流（设计稿 6px/18px 不对称圆角）*/
/* 对方消息（左侧）：组首/独立 = 左上小角；续条 = 左上左下都收小 */
.msg:not(.mine) .bubble {
  border-radius: 6px 18px 18px 18px;
}
.msg:not(.mine).group-mid .bubble,
.msg:not(.mine).group-last .bubble {
  border-radius: 6px 18px 18px 6px;
}

/* 自己消息（右侧）：grad-primary，右上小角 */
.msg.mine .bubble {
  background: var(--grad-primary);
  border-color: transparent;
  color: var(--on-primary);
  box-shadow: var(--elev-1), inset 0 1px 0 rgba(255, 255, 255, 0.22);
  border-radius: 18px 6px 18px 18px;
}
.msg.mine.group-mid .bubble,
.msg.mine.group-last .bubble {
  border-radius: 18px 6px 6px 18px;
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
  color: var(--success);
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
  color: var(--danger);
  background: var(--danger-soft);
}
.recall-btn:focus-visible {
  opacity: 1;
  outline: 2px solid var(--danger);
  outline-offset: 2px;
}

/* ───── Composer ───── */
.composer {
  padding: 12px 24px 16px;
  border-top: 1px solid var(--nook-surface-border);
  background: var(--nook-surface);
  backdrop-filter: var(--glass-std);
  -webkit-backdrop-filter: var(--glass-std);
}
/* 与消息流同宽居中，两者对齐成一条阅读列（设计稿 940） */
.composer-inner {
  width: 92%;
  max-width: 940px;
  margin: 0 auto;
  display: flex;
  flex-direction: column;
  gap: 10px;
}
/* 一体式输入条：暖白面 + 细描边 + 内陷阴影，聚焦时主色描边 + ring */
.composer-box {
  display: flex;
  align-items: flex-end;
  gap: 6px;
  padding: 6px;
  border-radius: var(--r-md);
  border: 1px solid var(--line-strong);
  background: var(--surface);
  box-shadow: inset 0 1px 3px hsl(var(--sh-color) / 0.07);
  transition: border-color var(--dur) var(--ease), box-shadow var(--dur) var(--ease);
}
.composer-box:focus-within {
  border-color: var(--primary);
  box-shadow: var(--ring);
}
.file-hidden {
  display: none;
}

/* ───── 待发图片预览区：卡片式 ───── */
.paste-tray {
  align-self: flex-start; /* 仅占内容宽度，像一张贴着输入框的卡片 */
  max-width: 100%;
  display: flex;
  flex-direction: column;
  gap: 8px;
  padding: 10px 12px;
  border-radius: var(--r-md);
  border: 1px solid var(--nook-surface-border);
  background: var(--nook-surface-raised);
  backdrop-filter: blur(10px) saturate(140%);
  -webkit-backdrop-filter: blur(10px) saturate(140%);
  box-shadow: var(--shadow-sm);
  animation: nook-rise var(--dur) var(--ease-out) both;
}
.paste-tray__head {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-size: 11.5px;
  font-weight: 500;
  color: var(--nook-text-muted);
}
.paste-tray__head svg {
  color: var(--nook-primary);
}
.paste-tray__count {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-width: 16px;
  height: 16px;
  padding: 0 5px;
  border-radius: var(--r-pill);
  background: var(--nook-gradient-teal);
  color: #fff;
  font-size: 10.5px;
  font-weight: 600;
}
.paste-tray__grid {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
}
.paste-card {
  position: relative;
  width: 84px;
  height: 84px;
  border-radius: var(--r-md);
  overflow: hidden;
  border: 1px solid var(--nook-surface-border);
  background: var(--nook-surface);
  box-shadow: var(--shadow-sm);
  transition: transform var(--dur) var(--ease-out), box-shadow var(--dur) var(--ease-out);
}
.paste-card:hover {
  transform: translateY(-2px);
  box-shadow: var(--shadow-md);
}
.paste-card img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  display: block;
  transition: transform var(--dur-slow) var(--ease-out);
}
.paste-card:hover img {
  transform: scale(1.06);
}
.paste-card__remove {
  position: absolute;
  top: 5px;
  right: 5px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 22px;
  height: 22px;
  border: none;
  border-radius: var(--r-pill);
  background: rgba(8, 20, 18, 0.5);
  color: #fff;
  cursor: pointer;
  opacity: 0.9;
  backdrop-filter: blur(6px);
  transition: background var(--dur) var(--ease-out), transform var(--dur-fast) var(--ease-out), opacity var(--dur) var(--ease-out);
}
.paste-card__remove:hover {
  background: var(--danger);
  opacity: 1;
  transform: scale(1.1);
}
.paste-card__remove:focus-visible {
  opacity: 1;
  outline: 2px solid var(--nook-primary);
  outline-offset: 2px;
}
/* 框内工具按钮（附件）—— 与发送按钮等高，hover 主色软底 */
.composer-tool {
  flex-shrink: 0;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 38px;
  height: 38px;
  border-radius: var(--r-xs);
  border: none;
  background: transparent;
  color: var(--ink-2);
  cursor: pointer;
  transition: background var(--dur) var(--ease), color var(--dur) var(--ease), transform var(--dur-fast) var(--ease);
}
.composer-tool:hover:not(:disabled) {
  background: var(--primary-soft);
  color: var(--primary-strong);
}
.composer-tool:active:not(:disabled) { transform: scale(0.92); }
.composer-tool:disabled {
  cursor: not-allowed;
  opacity: 0.5;
}
.up-pct {
  font-size: 11px;
  font-weight: 700;
  color: var(--primary);
}
.composer-box textarea {
  flex: 1;
  min-width: 0;
  /* 高度由 JS 自动撑高（autoGrow），到 max-height 后转为内部滚动 */
  resize: none;
  min-height: 30px;
  max-height: 176px;
  padding: 9px 6px;
  border: 0;
  background: transparent;
  font: inherit;
  font-size: 14px;
  line-height: 1.5;
  color: var(--ink);
  outline: none;
  overflow-y: auto;
  transition: height 120ms var(--ease);
}
.composer-box textarea::placeholder { color: var(--ink-3); }
.composer-box textarea::-webkit-scrollbar { width: 6px; }
.composer-box textarea::-webkit-scrollbar-thumb {
  background: hsl(var(--sh-color) / 0.2);
  border-radius: 3px;
}
.composer-box textarea::-webkit-scrollbar-thumb:hover { background: hsl(var(--sh-color) / 0.32); }
/* 一体式发送按钮：贴右下角，随框增高始终对齐底部 */
.composer-send {
  position: relative;
  flex-shrink: 0;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 38px;
  height: 38px;
  border: 0;
  border-radius: var(--r-xs);
  background: var(--grad-primary);
  color: var(--on-primary);
  cursor: pointer;
  box-shadow: var(--elev-1), inset 0 1px 0 rgba(255, 255, 255, 0.25);
  transition: filter var(--dur) var(--ease), transform var(--dur-fast) var(--ease), box-shadow var(--dur) var(--ease), background var(--dur) var(--ease), color var(--dur) var(--ease);
}
.composer-send:hover:not(:disabled) {
  filter: brightness(1.05);
  transform: translateY(-1px);
  box-shadow: var(--elev-2), inset 0 1px 0 rgba(255, 255, 255, 0.28);
}
.composer-send:active:not(:disabled) { transform: translateY(0.5px) scale(0.96); }
.composer-send:disabled {
  cursor: not-allowed;
  background: var(--surface-2);
  color: var(--ink-3);
  box-shadow: inset 0 0 0 1px var(--line);
}
.composer-send.loading { color: transparent; pointer-events: none; }
.composer-send.loading::after {
  content: '';
  position: absolute;
  width: 16px;
  height: 16px;
  border-radius: 50%;
  border: 2px solid var(--on-primary);
  border-top-color: transparent;
  animation: composer-spin 0.62s linear infinite;
}
@keyframes composer-spin { to { transform: rotate(360deg); } }

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

/* ───── 好友资料 / 备注弹窗 ───── */
.friend-panel {
  display: flex;
  flex-direction: column;
  gap: 16px;
}
.friend-id {
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 12px 14px;
  border-radius: var(--r-sm);
  border: 1px solid var(--nook-surface-border);
  background: var(--nook-gradient-wash);
}
.friend-avatar {
  flex-shrink: 0;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 48px;
  height: 48px;
  border-radius: var(--r-sm);
  background: var(--nook-gradient-brand);
  color: #fff;
  font-family: var(--nook-font-display);
  font-weight: 700;
  font-size: 18px;
}
.friend-meta {
  min-width: 0;
}
.friend-nickname {
  font-family: var(--nook-font-display);
  font-size: 15px;
  font-weight: 700;
  color: var(--nook-text);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.friend-sub {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  margin-top: 2px;
  font-size: 12px;
  color: var(--nook-text-muted);
}
.friend-field {
  display: flex;
  flex-direction: column;
  gap: 6px;
}
.friend-label {
  font-size: 12.5px;
  font-weight: 600;
  color: var(--nook-text-muted);
}
.remark-input {
  width: 100%;
  height: 40px;
  padding: 0 14px;
  border-radius: var(--r-sm);
  border: 1px solid var(--nook-surface-border);
  background: rgba(255, 255, 255, 0.6);
  font: inherit;
  font-size: 14px;
  color: var(--nook-text);
  outline: none;
  transition: border-color var(--dur) var(--ease-out);
}
html.dark .remark-input { background: rgba(4, 47, 46, 0.5); }
.remark-input:focus { border-color: var(--nook-primary); }
.remark-hint {
  margin: 0;
  font-size: 12.5px;
  line-height: 1.5;
  color: var(--nook-text-muted);
}

@media (prefers-reduced-motion: reduce) {
  .msg { animation: none; }
  .icon-btn,
  .recall-btn,
  .composer-send,
  .composer-tool,
  .room-title--clickable,
  .title-edit-hint,
  .composer-box textarea {
    transition: none;
  }
}
</style>
