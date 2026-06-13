<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { toast } from '@/composables/useToast'
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

// 扁平按展示名排序：用一张密集卡片网格铺满宽屏，比每字母一行更好用
const sortedFriends = computed(() =>
  [...friends.value].sort((a, b) => displayName(a).localeCompare(displayName(b), 'zh-Hans-CN'))
)

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
    toast.success(`已接受 ${r.fromNickname} 的好友申请`)
  } catch (e: any) {
    toast.error(e?.message ?? '操作失败')
  }
}
async function onReject(r: FriendRequest) {
  try {
    await rejectFriendRequest(r.id)
    r.status = 2
    friendStore.pendingCount = pendingRequests.value.length // 角标即时 -1
  } catch (e: any) {
    toast.error(e?.message ?? '操作失败')
  }
}

async function onAdd(u: Friend) {
  try {
    await sendFriendRequest(u.userId)
    toast.success(`已向 ${u.nickname} 发送好友申请`)
  } catch (e: any) {
    toast.error(e?.message ?? '申请发送失败')
  }
}

async function onMessage(f: Friend) {
  try {
    const conv = await getOrCreateDirect(f.userId)
    router.push({ name: 'chat-room', params: { id: conv.id } })
  } catch (e: any) {
    toast.error(e?.message ?? '无法发起会话')
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
    toast.success(remark ? '备注已更新' : '已清除备注')
  } catch (e: any) {
    toast.error(e?.message ?? '保存失败')
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
        <div v-else class="group-grid">
            <div v-for="f in sortedFriends" :key="f.userId" class="friend-card">
              <span class="avatar">{{ (displayName(f)[0] ?? '?').toUpperCase() }}<span v-if="presence.isOnline(f.userId)" class="av-dot" title="在线" /></span>
              <div class="info">
                <span class="name">{{ displayName(f) }}</span>
                <div class="sig">
                  <template v-if="f.remark">昵称：{{ f.nickname }}</template>
                  <template v-else>{{ f.signature || `@${f.username}` }}</template>
                </div>
              </div>
              <div class="card-actions">
                <button class="icon-act" type="button" title="设置备注" @click.stop="openRemark(f)">
                  <svg viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"><path d="M11 4H4a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2v-7" /><path d="M18.5 2.5a2.12 2.12 0 0 1 3 3L12 15l-4 1 1-4Z" /></svg>
                </button>
                <button class="icon-act accent" type="button" title="发消息" @click.stop="onMessage(f)">
                  <svg viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"><path d="M21 11.5a8.38 8.38 0 0 1-9 8.5 8.5 8.5 0 0 1-3.8-.9L3 21l1.9-5.2A8.5 8.5 0 1 1 21 11.5Z" /></svg>
                </button>
              </div>
            </div>
        </div>
      </div>

      <!-- 好友申请 -->
      <div v-else-if="tab === 'requests'" class="requests">
        <div v-if="!requests.length" class="empty">暂无好友申请</div>
        <div class="req-grid">
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
        <div class="result-grid">
          <div v-for="u in searchResults" :key="u.userId" class="result-card">
            <span class="avatar">{{ (u.nickname[0] ?? '?').toUpperCase() }}</span>
            <div class="info">
              <div class="name">{{ u.nickname }}</div>
              <div class="sig">@{{ u.username }} · {{ u.signature || '暂无签名' }}</div>
            </div>
            <button class="btn primary" @click="onAdd(u)">添加</button>
          </div>
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
  border-bottom: 1px solid var(--line);
  background: var(--surface);
}
.head h2 {
  margin: 0 0 14px;
  font-family: var(--font-display);
  font-size: var(--t-2xl);
  font-weight: 700;
  letter-spacing: -0.02em;
  color: var(--ink);
}
/* 分段控件（设计稿 .segmented）*/
.tabs {
  display: inline-flex;
  gap: 2px;
  padding: 3px;
  border-radius: var(--r-sm);
  background: var(--surface-2);
  border: 1px solid var(--line);
  box-shadow: inset 0 1px 2px hsl(var(--sh-color) / 0.08);
}
.tab {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 7px 16px;
  border: none;
  border-radius: var(--r-xs);
  background: transparent;
  color: var(--ink-2);
  font-family: var(--font-sans);
  font-size: var(--t-sm);
  font-weight: 600;
  cursor: pointer;
  transition: background var(--dur) var(--ease), color var(--dur) var(--ease), box-shadow var(--dur) var(--ease);
}
.tab:hover { color: var(--ink); }
.tab.active {
  background: var(--surface);
  color: var(--primary-strong);
  box-shadow: var(--elev-1), var(--inset-top);
}
.tab-count {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-width: 18px;
  height: 18px;
  padding: 0 6px;
  border-radius: 999px;
  background: var(--grad-accent);
  color: #fff;
  font-size: 11px;
  font-weight: 700;
  font-variant-numeric: tabular-nums;
  box-shadow: var(--elev-1);
}

.body {
  flex: 1;
  overflow-y: auto;
  padding: var(--space-5) var(--space-7) var(--space-8);
}
.body::-webkit-scrollbar { width: 6px; }
.body::-webkit-scrollbar-thumb { background: hsl(var(--sh-color) / 0.2); border-radius: 3px; }
/* 内容居中，宽屏用卡片网格铺开，不再左钉 720 列留大片右白 */
.friends,
.requests,
.add {
  max-width: 1180px;
  margin: 0 auto;
}

.empty {
  padding: 40px 0;
  text-align: center;
  color: var(--nook-text-muted);
  font-size: 14px;
}

.group + .group { margin-top: var(--space-5); }
.group-key {
  font-family: var(--nook-font-display);
  font-size: var(--text-sm);
  font-weight: 600;
  letter-spacing: var(--tracking-wide);
  color: var(--nook-primary-deep);
  padding: var(--space-2) var(--space-1) var(--space-2);
}
html.dark .group-key { color: var(--nook-primary-soft); }

/* 好友卡片网格：宽屏自动多列铺开 */
.group-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(300px, 1fr));
  gap: var(--space-3);
}
.friend-card {
  display: flex;
  align-items: center;
  gap: var(--space-3);
  padding: var(--space-3) var(--space-4);
  border-radius: var(--r-md);
  border: 1px solid var(--line);
  background: var(--surface);
  box-shadow: var(--elev-1), var(--inset-top);
  transition: transform var(--dur) var(--ease), box-shadow var(--dur) var(--ease), border-color var(--dur) var(--ease);
}
.friend-card:hover {
  transform: translateY(-2px);
  box-shadow: var(--elev-2), var(--inset-top);
  border-color: var(--primary);
}
.avatar {
  position: relative;
  flex-shrink: 0;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 44px;
  height: 44px;
  border-radius: 38%;
  background: var(--grad-primary);
  color: var(--on-primary);
  font-weight: 700;
  font-family: var(--font-display);
  box-shadow: var(--elev-1), inset 0 1px 0 rgba(255, 255, 255, 0.2);
}
.av-dot {
  position: absolute;
  right: -2px;
  bottom: -2px;
  width: 12px;
  height: 12px;
  border-radius: 50%;
  background: var(--success);
  border: 2px solid var(--surface);
}
.info {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 2px;
}
.name {
  font-weight: 600;
  font-size: var(--text-md);
  color: var(--nook-text);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.sig, .msg {
  font-size: var(--text-sm);
  color: var(--nook-text-muted);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.card-actions {
  display: flex;
  gap: var(--space-2);
  flex-shrink: 0;
}
.icon-act {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 34px;
  height: 34px;
  border-radius: var(--r-sm);
  border: 1px solid var(--nook-surface-border);
  background: transparent;
  color: var(--nook-text-muted);
  cursor: pointer;
  transition: background var(--dur) var(--ease-out), color var(--dur) var(--ease-out),
    border-color var(--dur) var(--ease-out);
}
.icon-act:hover {
  background: color-mix(in srgb, var(--primary) 10%, transparent);
  border-color: var(--nook-primary);
  color: var(--nook-primary-deep);
}
.icon-act.accent:hover {
  background: var(--nook-gradient-teal);
  border-color: transparent;
  color: #fff;
}
html.dark .icon-act:hover { color: var(--nook-primary-soft); }

/* 申请 / 搜索结果卡片网格 */
.req-grid,
.result-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(340px, 1fr));
  gap: var(--space-3);
}
.req-card {
  display: flex;
  align-items: center;
  gap: var(--space-3);
  padding: var(--space-4);
  border-radius: var(--r-md);
  border: 1px solid var(--line);
  background: var(--surface);
  box-shadow: var(--elev-1), var(--inset-top);
  transition: transform var(--dur) var(--ease), box-shadow var(--dur) var(--ease);
}
.req-card:hover { transform: translateY(-2px); box-shadow: var(--elev-2), var(--inset-top); }
.req-card .info {
  display: flex;
  flex-direction: column;
  gap: 3px;
}
.req-card .ts {
  font-size: 11.5px;
  color: var(--ink-3);
}
.st-1 { color: var(--success); }
.st-2 { color: var(--danger); }
.st-3 { color: var(--ink-3); }
.req-actions {
  display: flex;
  gap: 8px;
}

/* 按钮（设计稿 .btn）：主 = 渐变拟物，次 = 暖白描边 */
.btn {
  height: 36px;
  padding: 0 16px;
  border-radius: var(--r-sm);
  border: 1px solid transparent;
  font-family: var(--font-sans);
  font-size: var(--t-sm);
  font-weight: 600;
  cursor: pointer;
  transition: filter var(--dur) var(--ease), transform var(--dur-fast) var(--ease), box-shadow var(--dur) var(--ease), background var(--dur) var(--ease), border-color var(--dur) var(--ease), color var(--dur) var(--ease);
}
.btn.primary {
  background: var(--grad-primary);
  color: var(--on-primary);
  box-shadow: var(--elev-1), inset 0 1px 0 rgba(255, 255, 255, 0.28);
}
.btn.primary:hover:not(:disabled) { filter: brightness(1.04); transform: translateY(-1px); box-shadow: var(--elev-2), inset 0 1px 0 rgba(255, 255, 255, 0.32); }
.btn.ghost {
  background: var(--surface);
  border-color: var(--line-strong);
  color: var(--ink);
  box-shadow: var(--elev-1), var(--inset-top);
}
.btn.ghost:hover {
  border-color: var(--danger);
  color: var(--danger);
  background: var(--danger-soft);
}
.btn:disabled { opacity: 0.6; cursor: not-allowed; }

.search-bar {
  display: flex;
  gap: 10px;
  margin-bottom: 16px;
}
.search-bar input {
  flex: 1;
  height: 42px;
  padding: 0 14px;
  border-radius: var(--r-sm);
  border: 1px solid var(--line-strong);
  background: var(--surface);
  font: inherit;
  font-size: var(--t-base);
  color: var(--ink);
  outline: none;
  box-shadow: inset 0 1px 3px hsl(var(--sh-color) / 0.07);
  transition: border-color var(--dur) var(--ease), box-shadow var(--dur) var(--ease);
}
.search-bar input::placeholder { color: var(--ink-3); }
.search-bar input:focus { border-color: var(--primary); box-shadow: var(--ring), inset 0 1px 3px hsl(var(--sh-color) / 0.05); }

.result-card {
  display: flex;
  align-items: center;
  gap: var(--space-3);
  padding: var(--space-3) var(--space-4);
  border-radius: var(--r-md);
  border: 1px solid var(--nook-surface-border);
  background: var(--nook-surface);
  box-shadow: var(--shadow-sm);
  transition: transform var(--dur) var(--ease-out), box-shadow var(--dur) var(--ease-out);
}
.result-card:hover { transform: translateY(-2px); box-shadow: var(--shadow-md); }

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
