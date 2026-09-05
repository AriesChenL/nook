import http from './http'
import { USE_MOCK, delay } from './_mock'

/**
 * 订阅套餐编码。必须与后端 `nook.stripe.prices` 的 key 一致，服务端据此解析成 Stripe Price。
 * 多套餐时在此扩展并在 SubscriptionView 里列出。
 */
export const PLAN_PRO = 'pro_monthly'

export interface Subscription {
  productCode: string | null
  priceId: string | null
  /** active | trialing | past_due | canceled | incomplete | unpaid | paused */
  status: string
  /** 当前计费周期结束时间（ISO），null 表示无 */
  currentPeriodEnd: string | null
  /** 周期末是否自动取消 */
  cancelAtPeriodEnd: boolean | null
  trialEnd: string | null
  canceledAt: string | null
}

export interface Invoice {
  stripeInvoiceId: string
  invoiceNumber: string | null
  status: string
  currency: string | null
  amountDue: number | null
  amountPaid: number | null
  hostedInvoiceUrl: string | null
  invoicePdf: string | null
  periodStart: string | null
  periodEnd: string | null
  paidAt: string | null
  lastEventType: string | null
}

interface CheckoutResponse {
  checkoutUrl: string
  sessionId: string
}

const ENTITLED = new Set(['active', 'trialing'])

/** 与后端 EntitlementService 判定一致：active/trialing 且未过期才算 Pro。 */
export function isPro(sub: Subscription | null): boolean {
  if (!sub || sub.productCode !== PLAN_PRO || !ENTITLED.has(sub.status)) return false
  if (sub.currentPeriodEnd && new Date(sub.currentPeriodEnd).getTime() < Date.now()) return false
  return true
}

/** 当前订阅；无订阅时后端返回 null。 */
export async function getSubscription(): Promise<Subscription | null> {
  if (USE_MOCK) return delay(null)
  return http.get<unknown, Subscription | null>('/pay/subscription')
}

/** 发起订阅 Checkout 并跳转到 Stripe 托管收银台。success/cancel 回跳地址由后端配置。 */
export async function startSubscriptionCheckout(productCode: string = PLAN_PRO): Promise<void> {
  if (USE_MOCK) throw new Error('演示模式不支持支付')
  const res = await http.post<unknown, CheckoutResponse>(
    '/pay/checkout/subscription',
    { productCode },
    { headers: { 'Idempotency-Key': crypto.randomUUID() } }
  )
  window.location.href = res.checkoutUrl
}

/** 打开 Stripe Billing Portal 自助管理订阅（改套餐 / 取消 / 换卡）。 */
export async function openBillingPortal(): Promise<void> {
  if (USE_MOCK) throw new Error('演示模式不支持支付')
  const res = await http.post<unknown, { url: string }>('/pay/portal', {})
  window.location.href = res.url
}

/** 成功回跳后从 Stripe 主动读取一次权威状态，Webhook 仍是长期同步主路径。 */
export async function syncSubscription(sessionId: string): Promise<Subscription | null> {
  if (USE_MOCK) return delay(null)
  return http.post<unknown, Subscription | null>('/pay/subscription/sync', { sessionId })
}

export async function listInvoices(): Promise<Invoice[]> {
  if (USE_MOCK) return delay([])
  return http.get<unknown, Invoice[]>('/pay/invoices')
}
