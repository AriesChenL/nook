# Stripe 订阅上线清单

Nook 当前只售卖 `pro_monthly` 订阅，不支持一次性积分或余额充值。

## Dashboard 配置

1. 为每个环境创建独立 Product / recurring Price，并把 Price id 写入 `STRIPE_PRICE_PRO_MONTHLY`。
2. 使用最小权限 `rk_` restricted key；不要把服务端 key 放入前端、日志或 Git。
3. 配置 Billing Portal，至少开放支付方式更新、取消订阅和账单历史。
4. 在 Dashboard 管理可用支付方式。代码刻意不传 `payment_method_types`。
5. 如已完成 Stripe Tax 注册和经营地配置，再设置 `STRIPE_AUTOMATIC_TAX_ENABLED=true`。
6. 开启 Smart Retries、失败付款邮件和即将续费提醒。
7. 开启 Radar 与争议通知。当前没有后台管理员权限体系，退款和争议先在 Stripe Dashboard 处理，不向普通用户暴露退款 API。

## Webhook

Endpoint：`POST https://<api-domain>/pay/webhook`

只订阅以下事件：

- `checkout.session.completed`
- `checkout.session.expired`
- `checkout.session.async_payment_failed`
- `customer.subscription.created`
- `customer.subscription.updated`
- `customer.subscription.deleted`
- `customer.subscription.paused`
- `customer.subscription.resumed`
- `invoice.created`
- `invoice.finalized`
- `invoice.finalization_failed`
- `invoice.paid`
- `invoice.payment_failed`
- `invoice.payment_action_required`
- `invoice.voided`
- `invoice.marked_uncollectible`

将 endpoint 的签名密钥写入 `STRIPE_WEBHOOK_SECRET`。Nook 使用原始请求体验签、5 分钟时间容差、数据库唯一约束去重，并用 Stripe event `created` 防止旧事件覆盖新状态。

## 发布后验证

1. 使用 Stripe sandbox 完成一笔真实订阅 Checkout，回跳页应显示 Pro。
2. 重放同一 Webhook event，`stripe_events` 只应有一行，权益不得重复变化。
3. 用 Test Clock 推进续费，确认 `invoice.paid` 更新账单与订阅周期。
4. 使用失败测试支付方式，确认页面出现“扣款失败”，并能进入 Billing Portal 更新支付方式。
5. 在 Portal 取消订阅，确认周期结束前仍有权益，结束后变为 Free。
6. 检查 `/actuator/health/stripe` 及 `nook.pay.webhook.events`、`nook.pay.checkout.created` 指标。

## 运维边界

- 前端 `checkout=success` 只触发主动对账，不能直接授予权益。
- Stripe Webhook 是订阅与账单状态的长期事实来源；已知事件处理失败必须返回 5xx 让 Stripe 重试。
- `/pay/internal/**` 已被公网网关拒绝；部署时还必须用安全组/服务网格保证 `nook-pay:8085` 仅对内部服务可达。
- `payment_orders` 是早期一次性付款遗留表，仅保留历史数据兼容，新代码不再写入。
- Webhook endpoint 的 API 版本应与当前 `stripe-java` 主版本匹配；升级 SDK 时先阅读官方迁移指南并在 sandbox 重放事件。
