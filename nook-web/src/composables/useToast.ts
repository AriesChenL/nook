import { reactive } from 'vue'

/**
 * 全局 Toast —— 对齐 Claude Design「ai aganet chat」重设计稿
 * 主题色图标芯片 + 标题/正文两级文字 + 主题色倒计时进度条，跟随 data-palette / 深色切换。
 * 宿主组件见 components/NookToast.vue。
 */
export type ToastKind = 'success' | 'error' | 'warning' | 'info' | 'welcome'

export interface ToastItem {
  id: number
  kind: ToastKind
  message: string
  title?: string
}

/** 自动消失时长（ms）—— 与进度条动画同步，见设计稿 TOAST_TTL */
export const TOAST_TTL = 2800

export const toastState = reactive<{ items: ToastItem[] }>({ items: [] })

let _seq = 0
const _timers = new Map<number, ReturnType<typeof setTimeout>>()

/** 主入口：push 一条 toast。kind 默认 success（与设计稿一致）。 */
function push(message: string, kind: ToastKind = 'success', title?: string): number {
  const id = ++_seq
  toastState.items.push({ id, kind, message, title })
  _timers.set(
    id,
    setTimeout(() => dismiss(id), TOAST_TTL)
  )
  return id
}

/** 移除一条 toast（点击关闭或到时）。 */
export function dismiss(id: number) {
  const t = _timers.get(id)
  if (t) {
    clearTimeout(t)
    _timers.delete(id)
  }
  const i = toastState.items.findIndex((x) => x.id === id)
  if (i !== -1) toastState.items.splice(i, 1)
}

type ToastFn = ((message: string, kind?: ToastKind, title?: string) => number) & {
  success: (message: string, title?: string) => number
  error: (message: string, title?: string) => number
  warning: (message: string, title?: string) => number
  info: (message: string, title?: string) => number
  welcome: (message: string, title?: string) => number
}

/**
 * toast('已发送')                         → success
 * toast.error('保存失败')                  → error（红）
 * toast.welcome('继续你的对话', '欢迎回来') → 珊瑚渐变芯片 + 标题
 */
export const toast = push as ToastFn
toast.success = (message, title) => push(message, 'success', title)
toast.error = (message, title) => push(message, 'error', title)
toast.warning = (message, title) => push(message, 'warning', title)
toast.info = (message, title) => push(message, 'info', title)
toast.welcome = (message, title) => push(message, 'welcome', title)
