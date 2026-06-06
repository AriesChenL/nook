<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import {
  acceptFriendRequest,
  listFriendRequests,
  listFriends,
  rejectFriendRequest,
  searchUsers,
  sendFriendRequest,
  updateFriendRemark,
  type Friend,
  type FriendRequest
} from '@/api/user'
import { getOrCreateDirect } from '@/api/im'
import { usePresenceStore } from '@/stores/presence'
import { useFriendStore } from '@/stores/friends'
import NookModal from '@/components/NookModal.vue'

// 好友展示名：有备注用备注，否则用昵称（微信式）
function displayName(f: Friend): string {
  return f.remark || f.nickname
}

const router = useRouter()
const presence = usePresenceStore()
const friendStore = useFriendStore()

type Tab = 'friends' | 'requests' | 'add'

const tab = ref<Tab>('friends')
const friends = ref<Friend[]>([])
const requests = ref<FriendRequest[]>([])
const loading = ref(true)
const keyword = ref('')
const searchResults = ref<Friend[]>([])
const searching = ref(false)

const grouped = computed(() => {
  const groups: Record<string, Friend[]> = {}
  for (const f of friends.value) {
    const key = (displayName(f)[0] ?? '#').toUpperCase()
    ;(groups[key] ??= []).push(f)
  }
  return Object.keys(groups)
    .sort()
    .map((k) => ({ key: k, items: groups[k] }))
})

const pendingRequests = computed(() => requests.value.filter((r) => r.status === 0))

// 拉取好友 + 申请；silent=true 用于轮询，不显示骨架屏、失败不打扰
async function refresh(silent = false) {
  if (!silent) loading.value = true
  try {
    const [fs, rs] = await Promise.all([listFriends(), listFriendRequests()])
    friends.value = fs
    requests.value = rs
    friendStore.syncRemarks(fs) // 同步全局备注表，供聊天列表/聊天室复用
    friendStore.pendingCount = rs.filter((r) => r.status === 0).length // 同步侧栏角标
  } catch {
    /* 轮询失败静默处理 */
  } finally {
    if (!silent) loading.value = false
  }
}

// 轮询：好友申请被对方通过 / 收到新申请时，本端自动同步（无 WS 推送时的兜底）
const POLL_MS = 15000
let pollTimer: ReturnType<typeof setInterval> | null = null

onMounted(() => {
  refresh()
  pollTimer = setInterval(() => {
    // 页面不可见时不拉，省掉无效请求
    if (!document.hidden) refresh(true)
  }, POLL_MS)
})

onUnmounted(() => {
  if (pollTimer) clearInterval(pollTimer)
})

async function onSearch() {
  const k = keyword.value.trim()
  if (!k) {
    searchResults.value = []
    return
  }
  searching.value = true
  try {
    searchResults.value = await searchUsers(k)
  } finally {
    searching.value = false
  }
}

async function onAccept(r: FriendRequest) {
  try {
    await acceptFriendRequest(r.id)
    r.status = 1
    friendStore.pendingCount = pendingRequests.value.length // 角标即时 -1
    ElMessage.success(`已接受 ${r.fromNickname} 的好友申请`)
  } catch (e: any) {
    ElMessage.error(e?.message ?? '操作失败')
  }
}
async function onReject(r: FriendRequest) {
  try {
    await rejectFriendRequest(r.id)
    r.status = 2
    friendStore.pendingCount = pendingRequests.value.length // 角标即时 -1
  } catch (e: any) {
    ElMessage.error(e?.message ?? '操作失败')
  }
}

async function onAdd(u: Friend) {
  try {
    await sendFriendRequest(u.userId)
    ElMessage.success(`已向 ${u.nickname} 发送好友申请`)
  } catch (e: any) {
    ElMessage.error(e?.message ?? '申请发送失败')
  }
}

async function onMessage(f: Friend) {
  try {
    const conv = await getOrCreateDirect(f.userId)
    router.push({ name: 'chat-room', params: { id: conv.id } })
  } catch (e: any) {
    ElMessage.error(e?.message ?? '无法发起会话')
  }
}

// ───── 好友备注编辑 ─────
const remarkTarget = ref<Friend | null>(null)
const remarkDraft = ref('')
const savingRemark = ref(false)

function openRemark(f: Friend) {
  remarkTarget.value = f
  remarkDraft.value = f.remark ?? ''
}

async function saveRemark() {
  const target = remarkTarget.value
  if (!target || savingRemark.value) return
  const remark = remarkDraft.value.trim()
  savingRemark.value = true
  try {
    await updateFriendRemark(target.userId, remark)
    target.remark = remark || undefined // 即时反映到列表
    friendStore.setRemark(target.userId, remark) // 同步全局表（聊天页生效）
    remarkTarget.value = null
    ElMessage.success(remark ? '备注已更新' : '已清除备注')
  } catch (e: any) {
    ElMessage.error(e?.message ?? '保存失败')
  } finally {
    savingRemark.value = false
  }
}

const statusLabel: Record<number, string> = {
  0: '待处理',
  1: '已接受',
  2: '已拒绝',
  3: '已撤回'
}
</script>

<template>
  <div class="contacts">
    <header class="head">
      <h2>联系人</h2>
      <div class="tabs" role="tablist">
        <button
          v-for="t in [
            { k: 'friends',  label: '好友' },
            { k: 'requests', label: '申请', count: pendingRequests.length },
            { k: 'add',      label: '添加' }
          ]"
          :key="t.k"
          :class="['tab', { active: tab === t.k }]"
          @click="tab = t.k as Tab"
        >
          {{ t.label }}
          <span v-if="'count' in t && t.count" class="tab-count">{{ t.count }}</span>
        </button>
      </div>
    </header>

    <div class="body">
      <el-skeleton v-if="loading" :rows="6" animated />

      <!-- 好友列表 -->
      <div v-else-if="tab === 'friends'" class="friends">
        <div v-if="!friends.length" class="empty">还没有好友，去「添加」找找看</div>
        <div v-for="g in grouped" :key="g.key" class="group">
          <div class="group-key">{{ g.key }}</div>
          <div v-for="f in g.items" :key="f.userId" class="friend-row">
            <span class="avatar">{{ (displayName(f)[0] ?? '?').toUpperCase() }}</span>
            <div class="info">
              <div class="row">
                <span class="name">{{ displayName(f) }}</span>
                <span v-if="presence.isOnline(f.userId)" class="dot" title="在线" />
              </div>
              <div class="sig">
                <template v-if="f.remark">昵称：{{ f.nickname }}</template>
                <template v-else>{{ f.signature || `@${f.username}` }}</template>
              </div>
            </div>
            <button class="row-btn" type="button" title="设置备注" @click.stop="openRemark(f)">
              <svg viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"><path d="M11 4H4a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2v-7" /><path d="M18.5 2.5a2.12 2.12 0 0 1 3 3L12 15l-4 1 1-4Z" /></svg>
              备注
            </button>
            <button class="row-btn" type="button" @click.stop="onMessage(f)">
              <svg viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"><path d="M21 11.5a8.38 8.38 0 0 1-9 8.5 8.5 8.5 0 0 1-3.8-.9L3 21l1.9-5.2A8.5 8.5 0 1 1 21 11.5Z" /></svg>
              发消息
            </button>
          </div>
        </div>
      </div>

      <!-- 好友申请 -->
      <div v-else-if="tab === 'requests'" class="requests">
        <div v-if="!requests.length" class="empty">暂无好友申请</div>
        <div v-for="r in requests" :key="r.id" class="req-card">
          <span class="avatar">{{ (r.fromNickname[0] ?? '?').toUpperCase() }}</span>
          <div class="info">
            <div class="name">{{ r.fromNickname }}</div>
            <div class="msg">{{ r.message }}</div>
            <div class="ts">{{ r.createdAt }} · <span :class="`st-${r.status}`">{{ statusLabel[r.status] }}</span></div>
          </div>
          <div v-if="r.status === 0" class="req-actions">
            <button class="btn primary" @click="onAccept(r)">接受</button>
            <button class="btn ghost" @click="onReject(r)">拒绝</button>
          </div>
        </div>
      </div>

      <!-- 添加好友 -->
      <div v-else class="add">
        <div class="search-bar">
          <input v-model="keyword" placeholder="输入用户名或昵称搜索" @keyup.enter="onSearch" />
          <button class="btn primary" :disabled="searching" @click="onSearch">搜索</button>
        </div>
        <div v-if="!searchResults.length && keyword" class="empty">
          {{ searching ? '搜索中…' : '没有匹配结果' }}
        </div>
        <div v-for="u in searchResults" :key="u.userId" class="result-row">
          <span class="avatar">{{ (u.nickname[0] ?? '?').toUpperCase() }}</span>
          <div class="info">
            <div class="name">{{ u.nickname }}</div>
            <div class="sig">@{{ u.username }} · {{ u.signature || '暂无签名' }}</div>
          </div>
          <button class="btn primary" @click="onAdd(u)">添加</button>
        </div>
      </div>
    </div>

    <!-- 备注编辑弹窗（复用项目自有 NookModal，不用 Element 弹窗） -->
    <NookModal
      :model-value="!!remarkTarget"
      title="设置备注名"
      :width="400"
      @update:model-value="(v) => { if (!v) remarkTarget = null }"
    >
      <div v-if="remarkTarget" class="remark-form">
        <p class="remark-hint">为「{{ remarkTarget.nickname }}」设置一个只有你能看到的备注名</p>
        <input
          v-model="remarkDraft"
          class="remark-input"
          maxlength="64"
          placeholder="留空则恢复显示昵称"
          @keyup.enter="saveRemark"
        />
      </div>
      <template #footer>
        <button class="btn ghost" type="button" @click="remarkTarget = null">取消</button>
        <button class="btn primary" type="button" :disabled="savingRemark" @click="saveRemark">
          {{ savingRemark ? '保存中…' : '保存' }}
        </button>
      </template>
    </NookModal>
  </div>
</template>

<style scoped>
.contacts {
  display: flex;
  flex-direction: column;
  height: 100vh;
  height: 100dvh;
  min-height: 0;
}
.head {
  padding: 20px 28px 12px;
  border-bottom: 1px solid var(--nook-surface-border);
  background: var(--nook-surface);
  backdrop-filter: blur(12px);
}
.head h2 {
  margin: 0 0 14px;
  font-family: var(--nook-font-display);
  font-size: 22px;
  font-weight: 700;
  color: var(--nook-text);
}
.tabs {
  display: inline-flex;
  gap: 4px;
  padding: 4px;
  border-radius: 12px;
  background: rgba(20, 184, 166, 0.06);
}
.tab {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 7px 14px;
  border: none;
  border-radius: 9px;
  background: transparent;
  color: var(--nook-text-muted);
  font: inherit;
  font-size: 13.5px;
  font-weight: 500;
  cursor: pointer;
  transition: background 180ms ease, color 180ms ease;
}
.tab:hover { color: var(--nook-text); }
.tab.active {
  background: var(--nook-surface);
  color: var(--nook-primary-deep);
  box-shadow: 0 2px 6px -2px rgba(20, 184, 166, 0.3);
}
html.dark .tab.active { color: var(--nook-primary-soft); }
.tab-count {
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

.body {
  flex: 1;
  overflow-y: auto;
  padding: 18px 28px 32px;
}
.body::-webkit-scrollbar { width: 6px; }
.body::-webkit-scrollbar-thumb { background: rgba(20, 184, 166, 0.25); border-radius: 3px; }
/* 宽屏下把列表收进阅读列，左对齐与标题对齐，避免整行铺满、按钮被甩到最右 */
.friends,
.requests,
.add {
  max-width: 720px;
}

.empty {
  padding: 40px 0;
  text-align: center;
  color: var(--nook-text-muted);
  font-size: 14px;
}

.group + .group { margin-top: 16px; }
.group-key {
  font-family: var(--nook-font-display);
  font-size: 12px;
  font-weight: 600;
  color: var(--nook-text-muted);
  padding: 8px 12px 6px;
}
.friend-row {
  display: flex;
  align-items: center;
  width: 100%;
  gap: 14px;
  padding: 10px 12px;
  border-radius: 12px;
  border: none;
  background: transparent;
  cursor: pointer;
  text-align: left;
  font: inherit;
  transition: background 150ms ease;
}
.friend-row:hover {
  background: rgba(20, 184, 166, 0.08);
}
.avatar {
  flex-shrink: 0;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 42px;
  height: 42px;
  border-radius: 12px;
  background: var(--nook-gradient-brand);
  color: #fff;
  font-weight: 700;
  font-family: var(--nook-font-display);
}
.info {
  flex: 1;
  min-width: 0;
}
.row {
  display: flex;
  align-items: center;
  gap: 8px;
}
.name {
  font-weight: 600;
  font-size: 14.5px;
  color: var(--nook-text);
}
.dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: #10b981;
  box-shadow: 0 0 0 2px rgba(16, 185, 129, 0.2);
}
.sig, .msg {
  font-size: 12.5px;
  color: var(--nook-text-muted);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.row-btn {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 6px 10px;
  border-radius: 8px;
  border: 1px solid var(--nook-surface-border);
  background: transparent;
  color: var(--nook-text);
  font: inherit;
  font-size: 12.5px;
  cursor: pointer;
  transition: background 180ms ease, border-color 180ms ease, color 180ms ease;
}
.row-btn:hover {
  background: rgba(20, 184, 166, 0.1);
  border-color: var(--nook-primary);
  color: var(--nook-primary-deep);
}

.req-card {
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 14px;
  margin-bottom: 10px;
  border-radius: 14px;
  border: 1px solid var(--nook-surface-border);
  background: var(--nook-surface);
}
.req-card .info {
  display: flex;
  flex-direction: column;
  gap: 3px;
}
.req-card .ts {
  font-size: 11.5px;
  color: var(--nook-text-muted);
}
.st-1 { color: #10b981; }
.st-2 { color: #ef4444; }
.st-3 { color: var(--nook-text-muted); }
.req-actions {
  display: flex;
  gap: 8px;
}

.btn {
  height: 32px;
  padding: 0 14px;
  border-radius: 10px;
  border: none;
  font: inherit;
  font-size: 13px;
  font-weight: 500;
  cursor: pointer;
  transition: filter 180ms ease, background 180ms ease, border-color 180ms ease;
}
.btn.primary {
  background: var(--nook-gradient-teal);
  color: #fff;
}
.btn.primary:hover:not(:disabled) { filter: brightness(1.06); }
.btn.ghost {
  background: transparent;
  border: 1px solid var(--nook-surface-border);
  color: var(--nook-text);
}
.btn.ghost:hover {
  border-color: #ef4444;
  color: #ef4444;
}
.btn:disabled { opacity: 0.6; cursor: not-allowed; }

.search-bar {
  display: flex;
  gap: 10px;
  margin-bottom: 16px;
}
.search-bar input {
  flex: 1;
  height: 38px;
  padding: 0 14px;
  border-radius: 10px;
  border: 1px solid var(--nook-surface-border);
  background: rgba(255, 255, 255, 0.6);
  font: inherit;
  font-size: 14px;
  color: var(--nook-text);
  outline: none;
}
html.dark .search-bar input { background: rgba(4, 47, 46, 0.5); }
.search-bar input:focus { border-color: var(--nook-primary); }

.result-row {
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 10px 12px;
  border-radius: 12px;
  transition: background 150ms ease;
}
.result-row:hover { background: rgba(20, 184, 166, 0.08); }

/* ───── 备注弹窗 ───── */
.remark-form {
  display: flex;
  flex-direction: column;
  gap: 12px;
}
.remark-hint {
  margin: 0;
  font-size: 13px;
  color: var(--nook-text-muted);
  line-height: 1.5;
}
.remark-input {
  width: 100%;
  height: 40px;
  padding: 0 14px;
  border-radius: 10px;
  border: 1px solid var(--nook-surface-border);
  background: rgba(255, 255, 255, 0.6);
  font: inherit;
  font-size: 14px;
  color: var(--nook-text);
  outline: none;
  transition: border-color 180ms ease;
}
html.dark .remark-input { background: rgba(4, 47, 46, 0.5); }
.remark-input:focus { border-color: var(--nook-primary); }
</style>
