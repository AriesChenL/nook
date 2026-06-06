<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { useAuthStore } from '@/stores/auth'
import { getMe, listFriends, updateProfile } from '@/api/user'
import { listConversations, presignUpload, uploadToStorage } from '@/api/im'
import AvatarCropper from '@/components/AvatarCropper.vue'

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
    ElMessage.success('已保存')
  } catch (e: any) {
    ElMessage.error(e?.message ?? '保存失败')
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
    ElMessage.error('请选择图片文件')
    return
  }
  if (file.size > 10 * 1024 * 1024) {
    ElMessage.error('图片不能超过 10MB')
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
    ElMessage.success('头像已更新')
  } catch (e: any) {
    ElMessage.error(e?.message ?? '头像上传失败')
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
  position: relative;
  flex-shrink: 0;
  width: 76px;
  height: 76px;
  border-radius: var(--r-lg);
  border: none;
  padding: 0;
  overflow: hidden;
  cursor: pointer;
  background: var(--nook-gradient-brand);
  color: #fff;
  box-shadow: 0 14px 30px -10px rgba(15, 118, 110, 0.45);
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
