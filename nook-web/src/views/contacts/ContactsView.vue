<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import {
  acceptFriendRequest,
  listFriendRequests,
  listFriends,
  rejectFriendRequest,
  searchUsers,
  type Friend,
  type FriendRequest
} from '@/api/user'

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
    const key = (f.nickname[0] ?? '#').toUpperCase()
    ;(groups[key] ??= []).push(f)
  }
  return Object.keys(groups)
    .sort()
    .map((k) => ({ key: k, items: groups[k] }))
})

const pendingRequests = computed(() => requests.value.filter((r) => r.status === 0))

onMounted(async () => {
  try {
    const [fs, rs] = await Promise.all([listFriends(), listFriendRequests()])
    friends.value = fs
    requests.value = rs
  } finally {
    loading.value = false
  }
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
    ElMessage.success(`已接受 ${r.fromNickname} 的好友申请`)
  } catch (e: any) {
    ElMessage.error(e?.message ?? '操作失败')
  }
}
async function onReject(r: FriendRequest) {
  try {
    await rejectFriendRequest(r.id)
    r.status = 2
  } catch (e: any) {
    ElMessage.error(e?.message ?? '操作失败')
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
            { k: 'friends',  label: '好友', count: friends.length },
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
          <button v-for="f in g.items" :key="f.userId" class="friend-row">
            <span class="avatar">{{ (f.nickname[0] ?? '?').toUpperCase() }}</span>
            <div class="info">
              <div class="row">
                <span class="name">{{ f.nickname }}</span>
                <span v-if="f.online" class="dot" title="在线" />
              </div>
              <div class="sig">{{ f.signature || `@${f.username}` }}</div>
            </div>
            <button class="row-btn" type="button">
              <svg viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"><path d="M21 11.5a8.38 8.38 0 0 1-9 8.5 8.5 8.5 0 0 1-3.8-.9L3 21l1.9-5.2A8.5 8.5 0 1 1 21 11.5Z" /></svg>
              发消息
            </button>
          </button>
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
          <button class="btn primary">添加</button>
        </div>
      </div>
    </div>
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
  background: linear-gradient(135deg, #14b8a6, #fb923c);
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
  background: linear-gradient(135deg, #14b8a6, #0f766e);
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
</style>
