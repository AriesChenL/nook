import axios, { AxiosError } from 'axios'
import { toast } from '@/composables/useToast'
import { useAuthStore } from '@/stores/auth'
import router from '@/router'

export interface ApiError extends Error {
  code: number
  message: string
}

function makeApiError(code: number, message: string): ApiError {
  const err = new Error(message) as ApiError
  err.code = code
  return err
}

const http = axios.create({
  baseURL: '/api',
  timeout: 15000
})

http.interceptors.request.use((config) => {
  const auth = useAuthStore()
  if (auth.token) {
    config.headers.Authorization = `Bearer ${auth.token}`
  }
  return config
})

http.interceptors.response.use(
  (resp) => {
    const body = resp.data
    if (body && typeof body === 'object' && 'code' in body) {
      if (body.code === 200 || body.code === 0) {
        return body.data
      }
      // 业务错误：抛 ApiError，由组件 catch 决定如何展示（不在这里 toast）
      return Promise.reject(makeApiError(body.code, body.message ?? '请求失败'))
    }
    return body
  },
  (err: AxiosError<{ code?: number; message?: string }>) => {
    const status = err.response?.status
    const data = err.response?.data
    const code = data?.code ?? status ?? 0
    const message = data?.message ?? err.message ?? '网络错误'

    if (status === 401) {
      const auth = useAuthStore()
      auth.clear()
      const route = router.currentRoute.value
      if (route.name !== 'login' && route.name !== 'register') {
        router.push({ name: 'login', query: { redirect: route.fullPath } })
        toast.warning('登录已过期，请重新登录')
      }
      return Promise.reject(makeApiError(code, message))
    }

    if (!err.response) {
      // 真正的网络错误（断网、超时、CORS）— 走 toast
      toast.error(message)
    }

    return Promise.reject(makeApiError(code, message))
  }
)

export default http
