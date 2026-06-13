<script setup lang="ts">
import { toastState, dismiss, TOAST_TTL, type ToastKind } from '@/composables/useToast'

/** kind → 图标名（与设计稿 TOAST_ICON 一致） */
const ICON_OF: Record<ToastKind, keyof typeof ICON_PATHS> = {
  success: 'check',
  error: 'alert',
  warning: 'alert',
  info: 'bell',
  welcome: 'spark'
}

/** Lucide 风格图标路径（viewBox 0 0 24 24，描边），取自设计稿 data.jsx */
const ICON_PATHS = {
  check: ['M20 6 9 17l-5-5'],
  alert: ['M12 9v4', 'M12 17h.01', 'M10.3 3.9 1.8 18a2 2 0 0 0 1.7 3h17a2 2 0 0 0 1.7-3L13.7 3.9a2 2 0 0 0-3.4 0Z'],
  bell: ['M18 8a6 6 0 0 0-12 0c0 7-3 9-3 9h18s-3-2-3-9', 'M13.7 21a2 2 0 0 1-3.4 0'],
  spark: ['M12 3 14 9 20 11 14 13 12 19 10 13 4 11 10 9Z']
} as const

const ttl = TOAST_TTL + 'ms'
</script>

<template>
  <div class="toast-host">
    <TransitionGroup name="toast">
      <div
        v-for="t in toastState.items"
        :key="t.id"
        class="toast"
        :class="t.kind"
        role="status"
        aria-live="polite"
        @click="dismiss(t.id)"
      >
        <span class="t-chip">
          <svg
            viewBox="0 0 24 24"
            width="17"
            height="17"
            fill="none"
            stroke="currentColor"
            stroke-width="1.8"
            stroke-linecap="round"
            stroke-linejoin="round"
            aria-hidden="true"
          >
            <path v-for="(d, i) in ICON_PATHS[ICON_OF[t.kind]]" :key="i" :d="d" />
          </svg>
        </span>
        <div class="t-text">
          <b v-if="t.title" class="t-title">{{ t.title }}</b>
          <span class="t-msg">{{ t.message }}</span>
        </div>
        <span class="t-bar" :style="{ animationDuration: ttl }" />
      </div>
    </TransitionGroup>
  </div>
</template>

<style scoped>
.toast-host {
  position: fixed;
  top: 22px;
  left: 50%;
  transform: translateX(-50%);
  z-index: 2200;
  display: flex;
  flex-direction: column;
  gap: 10px;
  align-items: center;
  pointer-events: none;
  width: max-content;
  max-width: 92vw;
}

.toast {
  --tc: var(--primary);
  --tc-soft: var(--primary-soft);
  position: relative;
  overflow: hidden;
  display: flex;
  align-items: center;
  gap: 11px;
  padding: 10px 18px 10px 10px;
  border-radius: var(--r-md);
  background: var(--surface);
  border: 1px solid var(--line);
  box-shadow: var(--elev-float), var(--inset-top);
  pointer-events: auto;
  cursor: pointer;
}

.toast.success {
  --tc: var(--success);
  --tc-soft: color-mix(in oklch, var(--success) 14%, transparent);
}
.toast.error {
  --tc: var(--danger);
  --tc-soft: var(--danger-soft);
}
.toast.warning {
  --tc: var(--accent);
  --tc-soft: var(--accent-soft);
}
.toast.info {
  --tc: var(--primary);
  --tc-soft: var(--primary-soft);
}
.toast.welcome {
  --tc: var(--primary);
  --tc-soft: var(--primary-soft);
}
.toast.welcome .t-chip {
  background: var(--grad-primary);
  color: var(--on-primary);
  box-shadow: var(--elev-1), inset 0 1px 0 rgba(255, 255, 255, 0.25);
}

.t-chip {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 32px;
  height: 32px;
  flex-shrink: 0;
  border-radius: var(--r-sm);
  background: var(--tc-soft);
  color: var(--tc);
  box-shadow: var(--inset-top);
}

.t-text {
  display: flex;
  flex-direction: column;
  gap: 1px;
  line-height: 1.35;
  padding-right: 2px;
}
.t-title {
  font-size: var(--t-sm);
  font-weight: 700;
  color: var(--ink);
}
.t-msg {
  font-size: var(--t-sm);
  font-weight: 500;
  color: var(--ink-2);
  white-space: nowrap;
}
.toast:not(:has(.t-title)) .t-msg {
  color: var(--ink);
  font-weight: 600;
}

.t-bar {
  position: absolute;
  left: 0;
  bottom: 0;
  height: 3px;
  width: 100%;
  transform-origin: left;
  background: var(--tc);
  opacity: 0.85;
  animation: toast-bar linear forwards;
}

@keyframes toast-bar {
  from {
    transform: scaleX(1);
  }
  to {
    transform: scaleX(0);
  }
}

/* 入场：上方落下 + 轻微放大回弹（设计稿 toast-in / ease-spring）；出场：上移淡出 */
.toast-enter-active {
  animation: toast-in var(--dur-slow) var(--ease-spring) both;
}
.toast-leave-active {
  transition: opacity 0.2s ease, transform 0.2s ease;
  position: absolute;
}
.toast-leave-to {
  opacity: 0;
  transform: translateY(-10px) scale(0.96);
}
.toast-move {
  transition: transform var(--dur-slow) var(--ease-spring);
}

@keyframes toast-in {
  from {
    opacity: 0;
    transform: translateY(-14px) scale(0.95);
  }
  to {
    opacity: 1;
    transform: none;
  }
}

@media (prefers-reduced-motion: reduce) {
  .toast-enter-active,
  .toast-leave-active,
  .toast-move,
  .t-bar {
    animation: none;
    transition: none;
  }
}
</style>
