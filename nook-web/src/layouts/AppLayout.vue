<script setup lang="ts">
import { computed, onMounted, onUnmounted } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useAuthStore } from '@/stores/auth'
import { usePresenceStore } from '@/stores/presence'
import { logout } from '@/api/auth'
import { chatSocket } from '@/api/ws'
import ThemeToggle from '@/components/ThemeToggle.vue'

const router = useRouter()
const route = useRoute()
const auth = useAuthStore()
const presence = usePresenceStore()

interface NavItem {
  key: string
  label: string
  to: string
  match: (path: string) => boolean
  icon: 'chat' | 'contacts' | 'ai' | 'profile'
  badge?: () => number
}

const nav: NavItem[] = [
  { key: 'chat',     label: '消息',    to: '/chat',     match: (p) => p.startsWith('/chat'),     icon: 'chat' },
  { key: 'contacts', label: '联系人',  to: '/contacts', match: (p) => p.startsWith('/contacts'), icon: 'contacts' },
  { key: 'ai',       label: 'AI 助手', to: '/ai',       match: (p) => p.startsWith('/ai'),       icon: 'ai' },
  { key: 'profile',  label: '我的',    to: '/profile',  match: (p) => p.startsWith('/profile'),  icon: 'profile' }
]

const currentKey = computed(() => nav.find((n) => n.match(route.path))?.key ?? 'chat')

let offPresence: (() => void) | null = null

onMounted(() => {
  if (auth.token) chatSocket.connect(auth.token)
  offPresence = chatSocket.on('presence', (frame) => {
    const d = frame.data as { userId?: number; online?: boolean } | undefined
    if (d?.userId != null) presence.setOnline(d.userId, !!d.online)
  })
})

onUnmounted(() => offPresence?.())

async function onLogout() {
  try {
    await ElMessageBox.confirm('确定退出登录吗？', '提示', {
      type: 'warning',
      confirmButtonText: '退出',
      cancelButtonText: '取消'
    })
  } catch {
    return
  }
  try {
    await logout()
  } catch {
    /* 即使失败也清本地 */
  }
  chatSocket.disconnect()
  presence.reset()
  auth.clear()
  ElMessage.success('已退出')
  router.replace({ name: 'login' })
}
</script>

<template>
  <div class="app">
    <aside class="sidebar">
      <div class="brand" @click="router.push('/chat')">
        <img src="/logo.svg" alt="Nook" width="32" height="32" />
        <span class="brand-name">Nook</span>
      </div>

      <nav class="nav">
        <router-link
          v-for="item in nav"
          :key="item.key"
          :to="item.to"
          :class="['nav-item', { active: currentKey === item.key }]"
        >
          <span class="nav-icon" aria-hidden="true">
            <!-- chat -->
            <svg v-if="item.icon === 'chat'" viewBox="0 0 24 24" width="20" height="20" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round">
              <path d="M21 11.5a8.38 8.38 0 0 1-9 8.5 8.5 8.5 0 0 1-3.8-.9L3 21l1.9-5.2A8.5 8.5 0 1 1 21 11.5Z" />
            </svg>
            <!-- contacts -->
            <svg v-else-if="item.icon === 'contacts'" viewBox="0 0 24 24" width="20" height="20" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round">
              <path d="M16 21v-2a4 4 0 0 0-4-4H6a4 4 0 0 0-4 4v2" />
              <circle cx="9" cy="7" r="4" />
              <path d="M22 21v-2a4 4 0 0 0-3-3.87M16 3.13a4 4 0 0 1 0 7.75" />
            </svg>
            <!-- ai -->
            <svg v-else-if="item.icon === 'ai'" viewBox="0 0 24 24" width="20" height="20" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round">
              <path d="M12 3 L14 9 L20 11 L14 13 L12 19 L10 13 L4 11 L10 9 Z" />
            </svg>
            <!-- profile -->
            <svg v-else viewBox="0 0 24 24" width="20" height="20" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round">
              <circle cx="12" cy="8" r="4" />
              <path d="M4 21a8 8 0 0 1 16 0" />
            </svg>
          </span>
          <span class="nav-label">{{ item.label }}</span>
          <span v-if="item.badge && item.badge() > 0" class="nav-badge">{{ item.badge()! > 99 ? '99+' : item.badge() }}</span>
        </router-link>
      </nav>

      <div class="sidebar-foot">
        <div class="user-chip">
          <span class="avatar">{{ (auth.displayName?.[0] ?? '?').toUpperCase() }}</span>
          <div class="who">
            <span class="who-name">{{ auth.displayName }}</span>
            <span class="who-id">@{{ auth.user?.username }}</span>
          </div>
        </div>
        <div class="actions">
          <ThemeToggle />
          <button class="icon-btn" type="button" aria-label="退出登录" title="退出登录" @click="onLogout">
            <svg viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round">
              <path d="M9 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h4" />
              <polyline points="16 17 21 12 16 7" />
              <line x1="21" y1="12" x2="9" y2="12" />
            </svg>
          </button>
        </div>
      </div>
    </aside>

    <main class="content">
      <router-view v-slot="{ Component }">
        <transition name="fade" mode="out-in">
          <component :is="Component" />
        </transition>
      </router-view>
    </main>
  </div>
</template>

<style scoped>
.app {
  display: grid;
  grid-template-columns: 240px 1fr;
  min-height: 100vh;
  min-height: 100dvh;
  background:
    radial-gradient(70% 50% at 0% 0%, rgba(20, 184, 166, 0.12), transparent 70%),
    radial-gradient(60% 50% at 100% 100%, rgba(251, 146, 60, 0.1), transparent 70%),
    var(--nook-bg, #f0fdfa);
}
html.dark .app {
  --nook-bg: #042f2e;
}

.sidebar {
  display: flex;
  flex-direction: column;
  padding: 20px 14px;
  border-right: 1px solid var(--nook-surface-border);
  background: var(--nook-surface);
  backdrop-filter: blur(18px) saturate(150%);
  -webkit-backdrop-filter: blur(18px) saturate(150%);
}

.brand {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 6px 8px 14px;
  cursor: pointer;
}
.brand img {
  border-radius: 8px;
  box-shadow: 0 6px 14px -4px rgba(15, 118, 110, 0.4);
}
.brand-name {
  font-family: var(--nook-font-display);
  font-weight: 700;
  font-size: 18px;
  background: linear-gradient(135deg, #0f766e, #fb923c);
  -webkit-background-clip: text;
  background-clip: text;
  color: transparent;
}

.nav {
  display: flex;
  flex-direction: column;
  gap: 4px;
  margin-top: 8px;
  flex: 1;
}
.nav-item {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 10px 12px;
  border-radius: 12px;
  font-size: 14px;
  font-weight: 500;
  color: var(--nook-text-muted);
  text-decoration: none;
  transition: background 180ms ease, color 180ms ease;
  position: relative;
}
.nav-item:hover {
  background: rgba(20, 184, 166, 0.08);
  color: var(--nook-text);
}
.nav-item.active {
  background: linear-gradient(135deg, rgba(20, 184, 166, 0.18), rgba(251, 146, 60, 0.12));
  color: var(--nook-primary-deep);
}
html.dark .nav-item.active {
  color: var(--nook-primary-soft);
}
.nav-icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 24px;
  height: 24px;
}
.nav-label {
  flex: 1;
}
.nav-badge {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-width: 22px;
  height: 18px;
  padding: 0 6px;
  border-radius: 999px;
  background: var(--nook-accent-deep);
  color: #fff;
  font-size: 11px;
  font-weight: 600;
}

.sidebar-foot {
  display: flex;
  flex-direction: column;
  gap: 10px;
  padding: 10px;
  border-radius: 14px;
  background: rgba(20, 184, 166, 0.06);
}
.user-chip {
  display: flex;
  align-items: center;
  gap: 10px;
}
.avatar {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 36px;
  height: 36px;
  border-radius: 10px;
  background: linear-gradient(135deg, #14b8a6, #fb923c);
  color: #fff;
  font-family: var(--nook-font-display);
  font-weight: 700;
  font-size: 15px;
}
.who {
  display: flex;
  flex-direction: column;
  line-height: 1.2;
  min-width: 0;
}
.who-name {
  font-weight: 600;
  font-size: 13.5px;
  color: var(--nook-text);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.who-id {
  font-size: 11.5px;
  color: var(--nook-text-muted);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.actions {
  display: flex;
  gap: 8px;
  align-items: center;
}
.icon-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 36px;
  height: 36px;
  border-radius: 12px;
  border: 1px solid var(--nook-surface-border);
  background: var(--nook-surface);
  color: var(--nook-text);
  cursor: pointer;
  transition: border-color 180ms ease, color 180ms ease, background 180ms ease;
}
.icon-btn:hover {
  border-color: #ef4444;
  color: #ef4444;
}

.content {
  min-width: 0;
  min-height: 100vh;
  min-height: 100dvh;
  display: flex;
  flex-direction: column;
}

.fade-enter-active,
.fade-leave-active {
  transition: opacity 180ms ease;
}
.fade-enter-from,
.fade-leave-to {
  opacity: 0;
}

@media (max-width: 768px) {
  .app {
    grid-template-columns: 72px 1fr;
  }
  .brand-name,
  .nav-label,
  .who {
    display: none;
  }
  .nav-item {
    justify-content: center;
  }
  .sidebar-foot .user-chip {
    justify-content: center;
  }
  .sidebar-foot .actions {
    justify-content: center;
  }
}
</style>
