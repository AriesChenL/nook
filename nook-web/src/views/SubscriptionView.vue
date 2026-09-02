<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { toast } from '@/composables/useToast'
import {
  getSubscription,
  isPro,
  openBillingPortal,
  startSubscriptionCheckout,
  type Subscription
} from '@/api/pay'

const route = useRoute()
const router = useRouter()

const sub = ref<Subscription | null>(null)
const loading = ref(true)
const acting = ref(false)

const pro = computed(() => isPro(sub.value))

const STATUS_TEXT: Record<string, string> = {
  active: '生效中',
  trialing: '试用中',
  past_due: '扣款失败',
  canceled: '已取消',
  incomplete: '待完成',
  unpaid: '欠费',
  paused: '已暂停'
}

function fmtDate(iso: string | null): string {
  if (!iso) return '—'
  return new Date(iso).toLocaleDateString('zh-CN', { year: 'numeric', month: 'long', day: 'numeric' })
}

async function load() {
  loading.value = true
  try {
    sub.value = await getSubscription()
  } catch (e: any) {
    toast.error(e?.message ?? '加载订阅信息失败')
  } finally {
    loading.value = false
  }
}

async function onUpgrade() {
  acting.value = true
  try {
    await startSubscriptionCheckout()
    // 成功则已跳转到 Stripe；不会执行到这里
  } catch (e: any) {
    toast.error(e?.message ?? '发起支付失败')
    acting.value = false
  }
}

async function onManage() {
  acting.value = true
  try {
    await openBillingPortal()
  } catch (e: any) {
    toast.error(e?.message ?? '打开管理页失败')
    acting.value = false
  }
}

onMounted(async () => {
  // Stripe 回跳：?checkout=success / cancel（success/cancel URL 由后端配置指向本页）
  const flag = route.query.checkout
  if (flag === 'success') {
    toast.success('支付完成，订阅将在数秒内生效')
  } else if (flag === 'cancel') {
    toast.info('已取消支付')
  }
  if (flag) {
    router.replace({ path: route.path }) // 清掉 query，避免刷新重复提示
  }
  await load()
})
</script>

<template>
  <div class="sub">
    <header class="head">
      <h2>会员与订阅</h2>
    </header>

    <div class="body">
      <section class="card plan" :class="{ 'is-pro': pro }">
        <div class="plan-top">
          <div>
            <div class="plan-name">{{ pro ? 'Pro 会员' : '免费版' }}</div>
            <div class="plan-desc">
              {{ pro ? 'AI 智能体数量与每日对话不限' : '免费版：最多 3 个 AI 智能体，每日 20 轮对话' }}
            </div>
          </div>
          <span class="badge" :class="{ pro }">{{ pro ? 'PRO' : 'FREE' }}</span>
        </div>

        <div v-if="loading" class="muted">加载中…</div>

        <div v-else-if="sub" class="kvs">
          <div class="kv"><span class="k">状态</span><span class="v">{{ STATUS_TEXT[sub.status] ?? sub.status }}</span></div>
          <div class="kv">
            <span class="k">{{ sub.cancelAtPeriodEnd ? '到期后停止' : '下次续费' }}</span>
            <span class="v">{{ fmtDate(sub.currentPeriodEnd) }}</span>
          </div>
          <div v-if="sub.cancelAtPeriodEnd" class="kv">
            <span class="k">自动续费</span><span class="v">已关闭</span>
          </div>
        </div>

        <div class="actions">
          <button v-if="!pro" class="btn primary" :disabled="acting" @click="onUpgrade">
            {{ acting ? '跳转中…' : '升级到 Pro' }}
          </button>
          <button v-else class="btn" :disabled="acting" @click="onManage">
            {{ acting ? '跳转中…' : '管理订阅' }}
          </button>
        </div>
      </section>

      <p class="note">支付由 Stripe 处理，Nook 不存储你的银行卡信息。</p>
    </div>
  </div>
</template>

<style scoped>
.sub {
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
  max-width: 640px;
  width: 100%;
  margin: 0 auto;
}
.card {
  padding: var(--space-5);
  border-radius: var(--r-md);
  border: 1px solid var(--nook-surface-border);
  background: var(--nook-surface);
  box-shadow: var(--shadow-sm);
}
.plan.is-pro {
  border-color: var(--primary);
  background: var(--primary-soft);
}
.plan-top {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: var(--space-3);
}
.plan-name {
  font-family: var(--font-display);
  font-size: var(--t-lg);
  font-weight: 700;
  color: var(--ink);
}
.plan-desc {
  margin-top: 4px;
  font-size: 13px;
  color: var(--ink-2);
}
.badge {
  flex-shrink: 0;
  padding: 3px 10px;
  border-radius: 999px;
  font-size: 11px;
  font-weight: 700;
  letter-spacing: 0.06em;
  background: var(--surface-2);
  color: var(--ink-2);
}
.badge.pro {
  background: var(--grad-primary);
  color: var(--on-primary);
}
.kvs {
  margin-top: 16px;
  padding-top: 12px;
  border-top: 1px solid var(--line);
}
.kv {
  display: flex;
  justify-content: space-between;
  padding: 8px 0;
  font-size: 14px;
}
.kv .k { color: var(--ink-2); }
.kv .v { color: var(--ink); }
.muted {
  margin-top: 14px;
  font-size: 13px;
  color: var(--ink-3);
}
.actions {
  margin-top: 18px;
  display: flex;
  justify-content: flex-end;
}
.btn {
  height: 38px;
  padding: 0 22px;
  border-radius: var(--r-sm);
  border: 1px solid var(--line-strong);
  background: var(--surface);
  font: inherit;
  font-size: 13.5px;
  font-weight: 600;
  color: var(--ink);
  cursor: pointer;
  transition: transform var(--dur-fast) var(--ease-out), box-shadow var(--dur) var(--ease-out);
}
.btn:hover:not(:disabled) { transform: translateY(-1px); }
.btn.primary {
  border-color: transparent;
  background: var(--grad-primary);
  color: var(--on-primary);
  box-shadow: var(--elev-1), inset 0 1px 0 rgba(255, 255, 255, 0.28);
}
.btn:disabled { opacity: 0.5; cursor: not-allowed; }
.note {
  margin: 0;
  font-size: 12px;
  color: var(--ink-3);
}
</style>
