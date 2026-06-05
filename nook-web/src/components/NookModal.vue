<script setup lang="ts">
import { watch, onBeforeUnmount } from 'vue'

const props = withDefaults(
  defineProps<{
    modelValue: boolean
    title?: string
    /** 卡片最大宽度 px */
    width?: number
    /** 点遮罩/按 ESC 是否可关闭 */
    closable?: boolean
  }>(),
  { width: 440, closable: true }
)

const emit = defineEmits<{
  (e: 'update:modelValue', v: boolean): void
  (e: 'close'): void
}>()

function close() {
  if (!props.closable) return
  emit('update:modelValue', false)
  emit('close')
}

function onKeydown(e: KeyboardEvent) {
  if (e.key === 'Escape') close()
}

// 打开时锁 body 滚动 + 监听 ESC
watch(
  () => props.modelValue,
  (open) => {
    if (typeof document === 'undefined') return
    if (open) {
      document.addEventListener('keydown', onKeydown)
      document.body.style.overflow = 'hidden'
    } else {
      document.removeEventListener('keydown', onKeydown)
      document.body.style.overflow = ''
    }
  }
)

onBeforeUnmount(() => {
  document.removeEventListener('keydown', onKeydown)
  document.body.style.overflow = ''
})
</script>

<template>
  <Teleport to="body">
    <Transition name="nook-modal">
      <div v-if="modelValue" class="nm-overlay" @click.self="close">
        <div
          class="nm-panel"
          role="dialog"
          aria-modal="true"
          :aria-label="title"
          :style="{ maxWidth: width + 'px' }"
        >
          <header v-if="title || $slots.title || closable" class="nm-head">
            <slot name="title">
              <h3 v-if="title">{{ title }}</h3>
            </slot>
            <button v-if="closable" class="nm-x" type="button" aria-label="关闭" @click="close">
              <svg viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round"><line x1="18" y1="6" x2="6" y2="18" /><line x1="6" y1="6" x2="18" y2="18" /></svg>
            </button>
          </header>

          <div class="nm-body">
            <slot />
          </div>

          <footer v-if="$slots.footer" class="nm-foot">
            <slot name="footer" />
          </footer>
        </div>
      </div>
    </Transition>
  </Teleport>
</template>

<style scoped>
.nm-overlay {
  position: fixed;
  inset: 0;
  z-index: 2200;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 24px;
  background: rgba(8, 20, 18, 0.42);
  backdrop-filter: blur(5px);
  -webkit-backdrop-filter: blur(5px);
}
.nm-panel {
  position: relative;
  display: flex;
  flex-direction: column;
  width: 100%;
  max-height: 86vh;
  border-radius: var(--r-xl);
  border: 1px solid var(--nook-surface-border);
  background: var(--nook-surface-raised);
  backdrop-filter: blur(24px) saturate(150%);
  -webkit-backdrop-filter: blur(24px) saturate(150%);
  box-shadow: var(--shadow-lg);
  overflow: hidden;
}
/* 顶部高光线 */
.nm-panel::before {
  content: '';
  position: absolute;
  inset: 0 0 auto 0;
  height: 1px;
  background: linear-gradient(90deg, transparent, rgba(255, 255, 255, 0.6), transparent);
}
:global(html.dark) .nm-panel::before {
  background: linear-gradient(90deg, transparent, rgba(94, 234, 212, 0.4), transparent);
}

.nm-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 20px 22px 6px;
}
.nm-head :deep(h3),
.nm-head h3 {
  margin: 0;
  font-family: var(--nook-font-display);
  font-size: 18px;
  font-weight: 700;
  letter-spacing: -0.01em;
  color: var(--nook-text);
}
.nm-x {
  flex-shrink: 0;
  display: inline-flex;
  width: 32px;
  height: 32px;
  align-items: center;
  justify-content: center;
  border-radius: var(--r-xs);
  border: none;
  background: transparent;
  color: var(--nook-text-muted);
  cursor: pointer;
  transition: background var(--dur) var(--ease-out), color var(--dur) var(--ease-out);
}
.nm-x:hover {
  background: rgba(20, 184, 166, 0.1);
  color: var(--nook-text);
}

.nm-body {
  padding: 14px 22px 4px;
  overflow-y: auto;
}
.nm-foot {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 10px;
  padding: 16px 22px 20px;
}

/* 入场：遮罩淡入 + 卡片弹起 */
.nook-modal-enter-active,
.nook-modal-leave-active {
  transition: opacity var(--dur) var(--ease-out);
}
.nook-modal-enter-active .nm-panel {
  transition: transform var(--dur-slow) var(--ease-spring), opacity var(--dur-slow) var(--ease-out);
}
.nook-modal-leave-active .nm-panel {
  transition: transform 160ms ease, opacity 160ms ease;
}
.nook-modal-enter-from,
.nook-modal-leave-to {
  opacity: 0;
}
.nook-modal-enter-from .nm-panel {
  opacity: 0;
  transform: translateY(20px) scale(0.96);
}
.nook-modal-leave-to .nm-panel {
  opacity: 0;
  transform: translateY(8px) scale(0.98);
}
</style>
