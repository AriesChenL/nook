<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { toast } from '@/composables/useToast'
import { useAuthStore } from '@/stores/auth'
import { getMe, listFriends, updateProfile } from '@/api/user'
import { listConversations, presignUpload, uploadToStorage } from '@/api/im'
import AvatarCropper from '@/components/AvatarCropper.vue'
import { useTheme } from '@/composables/useTheme'
import { usePalette, PALETTES } from '@/composables/usePalette'

const auth = useAuthStore()
const { isDark, toggleDark } = useTheme()
const { palette, setPalette } = usePalette()

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
    if (auth.user) auth.setAuth(auth.token, { ...auth.user, nickname: form.nickname, avatarUrl: me.avatarUrl })
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
    toast.success('已保存')
  } catch (e: any) {
    toast.error(e?.message ?? '保存失败')
  } finally {
    saving.value = false
  }
}

// ───── 头像：选图 → 裁剪 → 上传 → 保存 ─────
const avatarInput = ref<HTMLInputElement | null>(null)
const cropperSrc = ref('')
const cropperVisible = ref(false)
const uploadingAvatar = ref(false)

function pickAvatar() {
  if (uploadingAvatar.value) return
  avatarInput.value?.click()
}

function onAvatarFile(e: Event) {
  const input = e.target as HTMLInputElement
  const file = input.files?.[0]
  input.value = '' // 允许再次选同一文件
  if (!file) return
  if (!file.type.startsWith('image/')) {
    toast.error('请选择图片文件')
    return
  }
  if (file.size > 10 * 1024 * 1024) {
    toast.error('图片不能超过 10MB')
    return
  }
  if (cropperSrc.value) URL.revokeObjectURL(cropperSrc.value)
  cropperSrc.value = URL.createObjectURL(file)
  cropperVisible.value = true
}

async function onCropped(blob: Blob) {
  cropperVisible.value = false
  uploadingAvatar.value = true
  try {
    const file = new File([blob], `avatar-${Date.now()}.jpg`, { type: 'image/jpeg' })
    const presigned = await presignUpload(file)
    await uploadToStorage(presigned.uploadUrl, file)
    await updateProfile({ avatarUrl: presigned.downloadUrl })
    auth.setAvatar(presigned.downloadUrl) // 侧栏/资料页即时更新 + 落本地缓存
    toast.success('头像已更新')
  } catch (e: any) {
    toast.error(e?.message ?? '头像上传失败')
  } finally {
    uploadingAvatar.value = false
    if (cropperSrc.value) {
      URL.revokeObjectURL(cropperSrc.value)
      cropperSrc.value = ''
    }
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
        <button
          class="avatar-big"
          type="button"
          :aria-busy="uploadingAvatar || undefined"
          title="点击更换头像"
          @click="pickAvatar"
        >
          <img v-if="auth.user?.avatarUrl" :src="auth.user.avatarUrl" alt="头像" class="avatar-img" />
          <span v-else class="avatar-initial">{{ (auth.displayName?.[0] ?? '?').toUpperCase() }}</span>
          <span class="avatar-edit">
            <svg v-if="!uploadingAvatar" viewBox="0 0 24 24" width="20" height="20" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"><path d="M23 19a2 2 0 0 1-2 2H3a2 2 0 0 1-2-2V8a2 2 0 0 1 2-2h4l2-3h6l2 3h4a2 2 0 0 1 2 2z" /><circle cx="12" cy="13" r="4" /></svg>
            <span v-else class="up-dots"><i /><i /><i /></span>
          </span>
        </button>
        <input ref="avatarInput" type="file" accept="image/*" class="hidden-file" @change="onAvatarFile" />
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
          <div class="kv">
            <span class="k">会员</span>
            <span class="v"><RouterLink class="link" to="/subscription">会员与订阅</RouterLink></span>
          </div>
        </section>

        <!-- 外观：深色模式 + 11 套配色（柔和拟物） -->
        <section class="card appearance">
          <h3>外观</h3>
          <div class="set-row">
            <div class="s-tt">
              <b>深色模式</b>
              <span>跟随你的心情切换明暗</span>
            </div>
            <button
              class="switch"
              type="button"
              role="switch"
              :aria-checked="isDark"
              aria-label="深色模式"
              @click="toggleDark"
            >
              <i />
            </button>
          </div>
          <div class="ap-block">
            <span class="ap-label">配色</span>
            <div class="ap-grid">
              <button
                v-for="p in PALETTES"
                :key="p.value"
                type="button"
                :class="['ap-swatch', { active: palette === p.value }]"
                :title="p.label"
                :aria-pressed="palette === p.value"
                @click="setPalette(p.value)"
              >
                <span class="ap-chip" :style="{ background: p.swatch }" aria-hidden="true" />
                <span class="ap-name">{{ p.label }}</span>
              </button>
            </div>
          </div>
        </section>
      </div>
    </div>

    <AvatarCropper v-model="cropperVisible" :src="cropperSrc" @confirm="onCropped" />
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
  border-bottom: 1px solid var(--line);
  background: var(--surface);
}
.head h2 {
  margin: 0;
  font-family: var(--font-display);
  font-size: var(--t-2xl);
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

/* ───── 外观卡片：深色模式开关 + 配色色板 ───── */
.appearance {
  grid-column: 1 / -1;
}
.set-row {
  display: flex;
  align-items: center;
  gap: var(--space-3);
}
.set-row .s-tt {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  line-height: 1.3;
}
.set-row .s-tt b {
  font-size: 14px;
  font-weight: 600;
  color: var(--ink);
}
.set-row .s-tt span {
  font-size: 12px;
  color: var(--ink-3);
}
/* 开关 —— 设计稿 .switch */
.switch {
  position: relative;
  display: inline-flex;
  flex-shrink: 0;
  width: 44px;
  height: 26px;
  padding: 0;
  border-radius: 999px;
  background: var(--surface-2);
  border: 1px solid var(--line-strong);
  box-shadow: inset 0 1px 3px hsl(var(--sh-color) / 0.12);
  cursor: pointer;
  transition: background var(--dur) var(--ease), border-color var(--dur) var(--ease);
}
.switch i {
  position: absolute;
  top: 2px;
  left: 2px;
  width: 20px;
  height: 20px;
  border-radius: 50%;
  background: var(--surface);
  box-shadow: var(--elev-1), inset 0 1px 0 var(--hi-strong);
  transition: transform var(--dur) var(--ease-spring);
}
.switch[aria-checked='true'] {
  background: var(--grad-primary);
  border-color: transparent;
}
.switch[aria-checked='true'] i {
  transform: translateX(18px);
}
.switch:focus-visible {
  box-shadow: var(--ring);
}

.ap-block {
  margin-top: 18px;
  padding-top: 16px;
  border-top: 1px solid var(--line);
}
.ap-label {
  display: block;
  margin-bottom: 10px;
  font-size: 13px;
  font-weight: 600;
  color: var(--ink);
}
.ap-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 8px;
}
.ap-swatch {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 6px;
  padding: 9px 4px 7px;
  border: 1px solid transparent;
  border-radius: var(--r-sm);
  background: transparent;
  color: var(--ink-2);
  cursor: pointer;
  transition: background var(--dur) var(--ease), border-color var(--dur) var(--ease), color var(--dur) var(--ease);
}
.ap-swatch:hover {
  background: var(--surface-2);
  color: var(--ink);
}
.ap-swatch.active {
  border-color: var(--primary);
  background: var(--primary-soft);
  color: var(--primary-strong);
}
.ap-chip {
  width: 28px;
  height: 28px;
  border-radius: 50%;
  box-shadow: var(--elev-1), inset 0 1px 0 rgba(255, 255, 255, 0.35);
}
.ap-name {
  font-size: 11px;
  font-weight: 500;
  line-height: 1;
}
@media (max-width: 720px) {
  .ap-grid { grid-template-columns: repeat(3, 1fr); }
}

/* 个人 hero（设计稿 .profile-hero）：暖白面 + 浮起阴影 + 右上柔光晕 */
.hero {
  position: relative;
  display: flex;
  align-items: center;
  gap: var(--space-5);
  padding: 28px;
  border-radius: var(--r-lg);
  background: var(--surface);
  box-shadow: var(--elev-2), var(--inset-top);
  overflow: hidden;
}
.hero::after {
  content: '';
  position: absolute;
  right: -40px;
  top: -40px;
  width: 200px;
  height: 200px;
  border-radius: 50%;
  background: var(--primary-soft);
  filter: blur(20px);
  pointer-events: none;
  z-index: 0;
}
.hero > * { position: relative; z-index: 1; }
.avatar-big {
  position: relative;
  flex-shrink: 0;
  width: 76px;
  height: 76px;
  border-radius: 32%;
  border: none;
  padding: 0;
  overflow: hidden;
  cursor: pointer;
  background: var(--grad-primary);
  color: var(--on-primary);
  box-shadow: var(--elev-2), inset 0 1px 0 rgba(255, 255, 255, 0.2);
}
.avatar-img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  display: block;
}
.avatar-initial {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 100%;
  height: 100%;
  font-family: var(--nook-font-display);
  font-weight: 700;
  font-size: 32px;
}
/* hover / 上传中浮出相机层 */
.avatar-edit {
  position: absolute;
  inset: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  background: rgba(8, 20, 18, 0.45);
  color: #fff;
  opacity: 0;
  transition: opacity var(--dur) var(--ease-out);
}
.avatar-big:hover .avatar-edit,
.avatar-big[aria-busy='true'] .avatar-edit { opacity: 1; }
.avatar-big:focus-visible {
  outline: 2px solid var(--nook-primary);
  outline-offset: 2px;
}
.hidden-file { display: none; }
.up-dots { display: inline-flex; gap: 4px; align-items: center; }
.up-dots i {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: currentColor;
  animation: av-blink 1s infinite ease-in-out;
}
.up-dots i:nth-child(2) { animation-delay: 0.15s; }
.up-dots i:nth-child(3) { animation-delay: 0.3s; }
@keyframes av-blink {
  0%, 80%, 100% { opacity: 0.3; transform: scale(0.6); }
  40% { opacity: 1; transform: scale(1); }
}
.meta { min-width: 0; }
.name {
  font-family: var(--font-display);
  font-size: var(--t-xl);
  font-weight: 700;
  letter-spacing: -0.02em;
  color: var(--ink);
}
.id {
  font-family: var(--font-mono);
  font-size: var(--t-sm);
  color: var(--ink-2);
  margin: 2px 0 10px;
}
.stats {
  display: flex;
  gap: 18px;
  font-size: 13px;
  color: var(--ink-2);
}
.stats strong {
  font-family: var(--font-display);
  font-size: var(--t-lg);
  color: var(--ink);
  margin-right: 4px;
}

.field {
  display: flex;
  flex-direction: column;
  gap: 6px;
  margin-bottom: 14px;
}
.field label {
  font-size: var(--t-sm);
  font-weight: 600;
  color: var(--ink);
}
.field input,
.field textarea {
  padding: 10px 14px;
  border-radius: var(--r-sm);
  border: 1px solid var(--line-strong);
  background: var(--surface);
  font: inherit;
  font-size: var(--t-base);
  color: var(--ink);
  outline: none;
  resize: vertical;
  box-shadow: inset 0 1px 3px hsl(var(--sh-color) / 0.07);
  transition: border-color var(--dur) var(--ease), box-shadow var(--dur) var(--ease);
}
.field input::placeholder,
.field textarea::placeholder { color: var(--ink-3); }
.field input:focus,
.field textarea:focus {
  border-color: var(--primary);
  box-shadow: var(--ring), inset 0 1px 3px hsl(var(--sh-color) / 0.05);
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
/* 主按钮（设计稿 .btn.primary）：渐变填充 + 顶部高光 + 落地阴影 */
.btn.primary {
  background: var(--grad-primary);
  color: var(--on-primary);
  box-shadow: var(--elev-1), inset 0 1px 0 rgba(255, 255, 255, 0.28);
}
.btn.primary:hover:not(:disabled) {
  transform: translateY(-1px);
  box-shadow: var(--elev-2), inset 0 1px 0 rgba(255, 255, 255, 0.32);
}
.btn.primary:active:not(:disabled) {
  transform: translateY(0.5px);
  filter: brightness(0.98);
  box-shadow: var(--elev-1), inset 0 2px 5px rgba(0, 0, 0, 0.16);
}
.btn:disabled { opacity: 0.5; cursor: not-allowed; }

.kv {
  display: flex;
  justify-content: space-between;
  padding: 10px 0;
  border-bottom: 1px solid var(--line);
  font-size: 14px;
}
.kv:last-child { border-bottom: none; }
.k { color: var(--ink-2); }
.v { color: var(--ink); }
.link {
  color: var(--primary-strong);
  font-weight: 600;
  cursor: pointer;
  text-decoration: none;
}
.link:hover { text-decoration: underline; }
</style>
