<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { useAuthStore } from '@/stores/auth'
import { getMe, listFriends, updateProfile } from '@/api/user'
import { listConversations } from '@/api/im'

const auth = useAuthStore()

const form = reactive({
  nickname: auth.user?.nickname ?? auth.user?.username ?? '',
  email: '',
  phone: ''
})
const saving = ref(false)

// 概览统计：好友数 / 群组数 / 会话数
const stats = reactive({ friends: 0, groups: 0, conversations: 0 })

onMounted(async () => {
  try {
    const me = await getMe()
    form.nickname = me.nickname || me.username
    form.email = me.email ?? ''
    form.phone = me.phone ?? ''
    if (auth.user) auth.setAuth(auth.token, { ...auth.user, nickname: form.nickname })
  } catch {
    /* 拉取失败保留本地缓存 */
  }

  // 统计数据独立加载，失败不影响资料展示
  try {
    const [friends, conversations] = await Promise.all([listFriends(), listConversations()])
    stats.friends = friends.length
    stats.groups = conversations.filter((c) => c.type === 2).length
    stats.conversations = conversations.length
  } catch {
    /* 统计拉取失败保持 0 */
  }
})

async function onSave() {
  saving.value = true
  try {
    await updateProfile({
      nickname: form.nickname,
      email: form.email || undefined,
      phone: form.phone || undefined
    })
    if (auth.user) auth.setAuth(auth.token, { ...auth.user, nickname: form.nickname })
    ElMessage.success('已保存')
  } catch (e: any) {
    ElMessage.error(e?.message ?? '保存失败')
  } finally {
    saving.value = false
  }
}
</script>

<template>
  <div class="profile">
    <header class="head">
      <h2>个人资料</h2>
    </header>

    <div class="body">
      <section class="card hero">
        <div class="avatar-big">
          {{ (auth.displayName?.[0] ?? '?').toUpperCase() }}
        </div>
        <div class="meta">
          <div class="name">{{ auth.displayName }}</div>
          <div class="id">@{{ auth.user?.username }}</div>
          <div class="stats">
            <span><strong>{{ stats.friends }}</strong> 好友</span>
            <span><strong>{{ stats.groups }}</strong> 群组</span>
            <span><strong>{{ stats.conversations }}</strong> 会话</span>
          </div>
        </div>
      </section>

      <div class="cards-grid">
        <section class="card">
          <h3>基本信息</h3>
          <div class="field">
            <label for="pf-nickname">昵称</label>
            <input id="pf-nickname" v-model="form.nickname" maxlength="32" />
          </div>
          <div class="field">
            <label for="pf-email">邮箱</label>
            <input id="pf-email" v-model="form.email" type="email" maxlength="128" placeholder="可选" />
          </div>
          <div class="field">
            <label for="pf-phone">手机号</label>
            <input id="pf-phone" v-model="form.phone" maxlength="32" placeholder="可选" />
          </div>
          <div class="actions">
            <button class="btn primary" :disabled="saving" @click="onSave">
              {{ saving ? '保存中…' : '保存' }}
            </button>
          </div>
        </section>

        <section class="card">
          <h3>账号</h3>
          <div class="kv">
            <span class="k">用户名</span><span class="v">{{ auth.user?.username }}</span>
          </div>
          <div class="kv">
            <span class="k">密码</span><span class="v">已设置 · <a class="link">修改</a></span>
          </div>
        </section>
      </div>
    </div>
  </div>
</template>

<style scoped>
.profile {
  display: flex;
  flex-direction: column;
  height: 100vh;
  height: 100dvh;
  min-height: 0;
}
.head {
  padding: 20px 28px 14px;
  border-bottom: 1px solid var(--nook-surface-border);
  background: var(--nook-surface);
  backdrop-filter: blur(12px);
}
.head h2 {
  margin: 0;
  font-family: var(--nook-font-display);
  font-size: 22px;
  font-weight: 700;
  color: var(--nook-text);
}

.body {
  flex: 1;
  overflow-y: auto;
  padding: var(--space-5) var(--space-7) var(--space-8);
  display: flex;
  flex-direction: column;
  gap: var(--space-4);
  max-width: 960px;
  width: 100%;
  margin: 0 auto;
}
/* 下方两张卡片宽屏并排，窄屏堆叠 */
.cards-grid {
  display: grid;
  grid-template-columns: 1.4fr 1fr;
  gap: var(--space-4);
  align-items: start;
}
@media (max-width: 720px) {
  .cards-grid { grid-template-columns: 1fr; }
}

.card {
  padding: var(--space-5) var(--space-5);
  border-radius: var(--r-md);
  border: 1px solid var(--nook-surface-border);
  background: var(--nook-surface);
  box-shadow: var(--shadow-sm);
}
.card h3 {
  margin: 0 0 14px;
  font-family: var(--nook-font-display);
  font-size: 14px;
  font-weight: 600;
  color: var(--nook-text);
}

.hero {
  display: flex;
  align-items: center;
  gap: var(--space-5);
  padding: var(--space-6);
  background:
    var(--nook-gradient-wash),
    var(--nook-surface);
}
.avatar-big {
  width: 76px;
  height: 76px;
  border-radius: 20px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  background: var(--nook-gradient-brand);
  color: #fff;
  font-family: var(--nook-font-display);
  font-weight: 700;
  font-size: 32px;
  box-shadow: 0 14px 30px -10px rgba(15, 118, 110, 0.45);
}
.meta { min-width: 0; }
.name {
  font-family: var(--nook-font-display);
  font-size: 20px;
  font-weight: 700;
  color: var(--nook-text);
}
.id {
  font-size: 12.5px;
  color: var(--nook-text-muted);
  margin: 2px 0 10px;
}
.stats {
  display: flex;
  gap: 18px;
  font-size: 13px;
  color: var(--nook-text-muted);
}
.stats strong {
  font-family: var(--nook-font-display);
  font-size: 16px;
  color: var(--nook-text);
  margin-right: 4px;
}

.field {
  display: flex;
  flex-direction: column;
  gap: 6px;
  margin-bottom: 14px;
}
.field label {
  font-size: 12.5px;
  font-weight: 500;
  color: var(--nook-text-muted);
}
.field input,
.field textarea {
  padding: 10px 12px;
  border-radius: 10px;
  border: 1px solid var(--nook-surface-border);
  background: rgba(255, 255, 255, 0.55);
  font: inherit;
  font-size: 14px;
  color: var(--nook-text);
  outline: none;
  resize: vertical;
  transition: border-color 180ms ease;
}
html.dark .field input,
html.dark .field textarea {
  background: rgba(4, 47, 46, 0.45);
}
.field input:focus,
.field textarea:focus {
  border-color: var(--nook-primary);
}

.actions {
  display: flex;
  justify-content: flex-end;
}
.btn {
  box-sizing: border-box;
  height: 38px;
  padding: 0 22px;
  border-radius: var(--r-sm);
  border: 1px solid transparent;
  font: inherit;
  font-size: 13.5px;
  font-weight: 600;
  letter-spacing: 0.01em;
  cursor: pointer;
  transition: transform var(--dur-fast) var(--ease-out), box-shadow var(--dur) var(--ease-out),
    background var(--dur) var(--ease-out), border-color var(--dur) var(--ease-out);
}
/* 对齐侧边栏「选中菜单项」的视觉语言：品牌微渐变 + 深青绿文字 + 细描边 + 柔光 */
.btn.primary {
  background: var(--nook-gradient-wash);
  color: var(--nook-primary-deep);
  box-shadow: inset 0 0 0 1px var(--nook-surface-border),
    0 8px 22px -14px rgba(20, 184, 166, 0.7);
}
.btn.primary:hover:not(:disabled) {
  transform: translateY(-1px);
  box-shadow: inset 0 0 0 1px var(--nook-primary),
    0 12px 26px -14px rgba(20, 184, 166, 0.8);
}
.btn.primary:active:not(:disabled) {
  transform: translateY(0) scale(0.985);
  box-shadow: inset 0 0 0 1px var(--nook-primary),
    0 5px 14px -12px rgba(20, 184, 166, 0.7);
}
html.dark .btn.primary {
  color: var(--nook-primary-soft);
}
.btn:disabled { opacity: 0.5; cursor: not-allowed; }

.kv {
  display: flex;
  justify-content: space-between;
  padding: 10px 0;
  border-bottom: 1px solid var(--nook-surface-border);
  font-size: 14px;
}
.kv:last-child { border-bottom: none; }
.k { color: var(--nook-text-muted); }
.v { color: var(--nook-text); }
.link {
  color: var(--nook-primary-deep);
  cursor: pointer;
  text-decoration: none;
}
html.dark .link { color: var(--nook-primary-soft); }
.link:hover { text-decoration: underline; }
</style>
