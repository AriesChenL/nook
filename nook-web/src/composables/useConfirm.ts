import { reactive } from 'vue'

export interface ConfirmOptions {
  message: string
  title?: string
  confirmText?: string
  cancelText?: string
  /** 危险操作：确认按钮用红色 */
  danger?: boolean
}

// 单例状态：同一时刻一个确认框（够用）
export const confirmState = reactive({
  visible: false,
  title: '确认',
  message: '',
  confirmText: '确定',
  cancelText: '取消',
  danger: false,
  resolve: null as ((v: boolean) => void) | null
})

/** 弹出确认框，返回 Promise<boolean>（确认 true / 取消 false） */
export function confirm(opts: ConfirmOptions): Promise<boolean> {
  return new Promise<boolean>((resolve) => {
    confirmState.title = opts.title ?? '确认'
    confirmState.message = opts.message
    confirmState.confirmText = opts.confirmText ?? '确定'
    confirmState.cancelText = opts.cancelText ?? '取消'
    confirmState.danger = !!opts.danger
    confirmState.resolve = resolve
    confirmState.visible = true
  })
}

/** 由宿主组件调用，结束当前确认 */
export function settleConfirm(value: boolean) {
  if (!confirmState.visible) return
  confirmState.visible = false
  const r = confirmState.resolve
  confirmState.resolve = null
  r?.(value)
}
