// IM 实时通道：连接网关 /im/ws，握手用 ?access_token= 让网关注入 X-User-Id。
// 服务端推送帧：{type:'ready'|'pong'|'message'|'recall', data?, ...}
// 客户端只发心跳 {type:'ping'}；业务消息仍走 HTTP POST /im/messages。

type Frame = { type: string; data?: unknown; [k: string]: unknown }
type Handler = (frame: Frame) => void

const PING_INTERVAL = 25_000
const RECONNECT_BASE = 1_000
const RECONNECT_MAX = 15_000

function wsBase(): string {
  const proto = location.protocol === 'https:' ? 'wss' : 'ws'
  return `${proto}://${location.host}`
}

class ChatSocket {
  private ws: WebSocket | null = null
  private token = ''
  private manualClose = false
  private retries = 0
  private pingTimer: ReturnType<typeof setInterval> | null = null
  private reconnectTimer: ReturnType<typeof setTimeout> | null = null
  private handlers = new Map<string, Set<Handler>>()

  connect(token: string) {
    if (!token) return
    // 同 token 且已就绪/连接中则复用
    if (this.ws && this.token === token &&
        (this.ws.readyState === WebSocket.OPEN || this.ws.readyState === WebSocket.CONNECTING)) {
      return
    }
    this.disconnect()
    this.token = token
    this.manualClose = false
    this.open()
  }

  private open() {
    const url = `${wsBase()}/im/ws?access_token=${encodeURIComponent(this.token)}`
    const ws = new WebSocket(url)
    this.ws = ws

    ws.onopen = () => {
      this.retries = 0
      this.startPing()
    }
    ws.onmessage = (e) => {
      let frame: Frame
      try {
        frame = JSON.parse(e.data)
      } catch {
        return
      }
      this.dispatch(frame)
    }
    ws.onclose = () => {
      this.stopPing()
      if (!this.manualClose) this.scheduleReconnect()
    }
    ws.onerror = () => {
      // 错误后通常紧跟 onclose，由其处理重连
    }
  }

  private scheduleReconnect() {
    if (this.reconnectTimer) return
    const delay = Math.min(RECONNECT_BASE * 2 ** this.retries, RECONNECT_MAX)
    this.retries++
    this.reconnectTimer = setTimeout(() => {
      this.reconnectTimer = null
      if (!this.manualClose && this.token) this.open()
    }, delay)
  }

  private startPing() {
    this.stopPing()
    this.pingTimer = setInterval(() => {
      if (this.ws?.readyState === WebSocket.OPEN) {
        this.ws.send(JSON.stringify({ type: 'ping' }))
      }
    }, PING_INTERVAL)
  }

  private stopPing() {
    if (this.pingTimer) {
      clearInterval(this.pingTimer)
      this.pingTimer = null
    }
  }

  private dispatch(frame: Frame) {
    this.handlers.get(frame.type)?.forEach((h) => {
      try {
        h(frame)
      } catch {
        /* 单个订阅者异常不影响其他 */
      }
    })
  }

  on(type: string, handler: Handler): () => void {
    let set = this.handlers.get(type)
    if (!set) {
      set = new Set()
      this.handlers.set(type, set)
    }
    set.add(handler)
    return () => set!.delete(handler)
  }

  disconnect() {
    this.manualClose = true
    this.stopPing()
    if (this.reconnectTimer) {
      clearTimeout(this.reconnectTimer)
      this.reconnectTimer = null
    }
    if (this.ws) {
      this.ws.onclose = null
      this.ws.onerror = null
      this.ws.onmessage = null
      this.ws.onopen = null
      try {
        this.ws.close()
      } catch {
        /* ignore */
      }
      this.ws = null
    }
    this.token = ''
  }
}

export const chatSocket = new ChatSocket()
