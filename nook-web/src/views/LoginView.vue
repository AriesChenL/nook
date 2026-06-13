<script setup lang="ts">
import { reactive, ref, onMounted } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { toast } from '@/composables/useToast'
import { login } from '@/api/auth'
import { useAuthStore } from '@/stores/auth'
import AuthShell from '@/components/AuthShell.vue'
import '@/styles/auth-form.css'

const router = useRouter()
const route = useRoute()
const auth = useAuthStore()

const form = reactive({ username: '', password: '', remember: true })
const loading = ref(false)
const submitError = ref('')

onMounted(() => {
  const prefill = route.query.username
  if (typeof prefill === 'string') form.username = prefill
})

async function onSubmit() {
  submitError.value = ''
  if (!form.username || !form.password) {
    submitError.value = '请输入用户名和密码'
    return
  }
  loading.value = true
  try {
    const data = await login({ username: form.username, password: form.password })
    auth.setAuth(data.token, {
      userId: data.userId,
      username: data.username,
      nickname: data.nickname
    })
    toast.welcome('继续你的对话与 AI 工作流', `欢迎回来，${data.nickname || data.username}`)
    const redirect = (route.query.redirect as string) || '/home'
    router.replace(redirect)
  } catch (e: any) {
    submitError.value = e?.message ?? '登录失败，请稍后再试'
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <AuthShell title="欢迎回到 Nook" subtitle="登录后继续你的对话与 AI 工作流">
    <form class="auth-form" @submit.prevent="onSubmit" novalidate>
      <div class="field">
        <label class="field-label" for="login-username">用户名</label>
        <el-input
          id="login-username"
          v-model="form.username"
          placeholder="请输入用户名"
          autocomplete="username"
          :prefix-icon="undefined"
          size="large"
        />
      </div>

      <div class="field">
        <label class="field-label" for="login-password">密码</label>
        <el-input
          id="login-password"
          v-model="form.password"
          type="password"
          placeholder="请输入密码"
          autocomplete="current-password"
          show-password
          size="large"
        />
      </div>

      <div class="row-between">
        <el-checkbox v-model="form.remember">7 天内自动登录</el-checkbox>
        <a class="link" tabindex="0" @click="toast.info('请联系管理员重置密码')">忘记密码？</a>
      </div>

      <p v-if="submitError" class="form-error" role="alert">{{ submitError }}</p>

      <button
        type="submit"
        class="submit-btn"
        :disabled="loading"
        :aria-busy="loading || undefined"
      >
        <span v-if="!loading">登 录</span>
        <span v-else class="loading-dot" aria-hidden="true">
          <i /><i /><i />
        </span>
      </button>

    </form>

    <template #footer>
      还没有账号？
      <router-link class="link" :to="{ name: 'register' }">立即注册</router-link>
    </template>
  </AuthShell>
</template>

<style scoped>
.form-error {
  margin: 0 0 12px;
  padding: 10px 12px;
  border-radius: var(--r-sm);
  background: var(--danger-soft);
  color: var(--danger);
  font-size: 13px;
  line-height: 1.4;
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
