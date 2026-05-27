<script setup lang="ts">
import { computed, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { register } from '@/api/auth'
import AuthShell from '@/components/AuthShell.vue'
import '@/styles/auth-form.css'

const router = useRouter()

const form = reactive({
  username: '',
  nickname: '',
  password: '',
  confirm: ''
})
const loading = ref(false)
const submitError = ref('')

const strength = computed(() => {
  const p = form.password
  if (!p) return { level: 0, label: '', color: '' }
  let score = 0
  if (p.length >= 8) score++
  if (/[A-Z]/.test(p)) score++
  if (/[0-9]/.test(p)) score++
  if (/[^A-Za-z0-9]/.test(p)) score++
  const map = [
    { level: 1, label: '太弱', color: '#ef4444' },
    { level: 2, label: '一般', color: '#f59e0b' },
    { level: 3, label: '不错', color: '#10b981' },
    { level: 4, label: '很强', color: '#059669' }
  ]
  return map[Math.min(score, 4) - 1] ?? { level: 1, label: '太弱', color: '#ef4444' }
})

function validate(): string | null {
  if (!form.username || form.username.length < 3) return '用户名至少 3 个字符'
  if (!/^[A-Za-z0-9_]+$/.test(form.username)) return '用户名仅支持字母、数字和下划线'
  if (!form.password || form.password.length < 6) return '密码至少 6 位'
  if (form.password !== form.confirm) return '两次输入的密码不一致'
  return null
}

async function onSubmit() {
  submitError.value = ''
  const err = validate()
  if (err) {
    submitError.value = err
    return
  }
  loading.value = true
  try {
    await register({
      username: form.username,
      password: form.password,
      nickname: form.nickname || undefined
    })
    ElMessage.success('注册成功，请登录')
    router.replace({ name: 'login', query: { username: form.username } })
  } catch (e: any) {
    submitError.value = e?.message ?? '注册失败，请稍后再试'
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <AuthShell title="创建你的 Nook 账号" subtitle="一个账号，畅聊与 AI 协作">
    <form class="auth-form" @submit.prevent="onSubmit" novalidate>
      <div class="field">
        <label class="field-label" for="reg-username">用户名</label>
        <el-input
          id="reg-username"
          v-model="form.username"
          placeholder="3-32 位字母 / 数字 / 下划线"
          autocomplete="username"
          size="large"
          maxlength="32"
        />
      </div>

      <div class="field">
        <label class="field-label" for="reg-nickname">昵称 <span class="optional">（可选）</span></label>
        <el-input
          id="reg-nickname"
          v-model="form.nickname"
          placeholder="显示给好友的名字"
          size="large"
          maxlength="32"
        />
      </div>

      <div class="field">
        <label class="field-label" for="reg-password">密码</label>
        <el-input
          id="reg-password"
          v-model="form.password"
          type="password"
          placeholder="至少 6 位，建议含字母与数字"
          autocomplete="new-password"
          show-password
          size="large"
        />
        <div v-if="form.password" class="strength" :aria-label="`密码强度：${strength.label}`">
          <div class="strength-bar">
            <span
              v-for="i in 4"
              :key="i"
              :style="{
                background: i <= strength.level ? strength.color : 'rgba(99,102,241,0.15)'
              }"
            />
          </div>
          <span class="strength-label" :style="{ color: strength.color }">{{ strength.label }}</span>
        </div>
      </div>

      <div class="field">
        <label class="field-label" for="reg-confirm">确认密码</label>
        <el-input
          id="reg-confirm"
          v-model="form.confirm"
          type="password"
          placeholder="再次输入密码"
          autocomplete="new-password"
          show-password
          size="large"
          @keyup.enter="onSubmit"
        />
      </div>

      <p v-if="submitError" class="form-error" role="alert">{{ submitError }}</p>

      <button
        type="submit"
        class="submit-btn"
        :disabled="loading"
        :aria-busy="loading || undefined"
      >
        <span v-if="!loading">注 册</span>
        <span v-else class="loading-dot" aria-hidden="true">
          <i /><i /><i />
        </span>
      </button>

      <p class="tos">
        点击注册即表示同意
        <a class="link" tabindex="0">服务条款</a> 与
        <a class="link" tabindex="0">隐私政策</a>。
      </p>
    </form>

    <template #footer>
      已有账号？
      <router-link class="link" :to="{ name: 'login' }">直接登录</router-link>
    </template>
  </AuthShell>
</template>

<style scoped>
.optional {
  color: var(--nook-text-muted);
  font-weight: 400;
  font-size: 12px;
}

.strength {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-top: 8px;
}
.strength-bar {
  flex: 1;
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 4px;
}
.strength-bar span {
  height: 4px;
  border-radius: 2px;
  transition: background 200ms ease;
}
.strength-label {
  font-size: 12px;
  font-weight: 500;
  min-width: 32px;
  text-align: right;
}

.tos {
  margin: 14px 0 0;
  font-size: 12px;
  color: var(--nook-text-muted);
  line-height: 1.5;
  text-align: center;
}

.form-error {
  margin: 0 0 12px;
  padding: 10px 12px;
  border-radius: 10px;
  background: rgba(239, 68, 68, 0.08);
  color: #b91c1c;
  font-size: 13px;
  line-height: 1.4;
}
html.dark .form-error {
  background: rgba(239, 68, 68, 0.18);
  color: #fecaca;
}

.loading-dot {
  display: inline-flex;
  gap: 6px;
  align-items: center;
}
.loading-dot i {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: currentColor;
  animation: bounce 1s infinite ease-in-out;
}
.loading-dot i:nth-child(2) { animation-delay: 0.15s; }
.loading-dot i:nth-child(3) { animation-delay: 0.3s; }
@keyframes bounce {
  0%, 80%, 100% { transform: scale(0.6); opacity: 0.4; }
  40% { transform: scale(1); opacity: 1; }
}
</style>
