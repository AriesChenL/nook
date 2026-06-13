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
/* 遮罩（设计稿 .scrim）：阴影色半透明 + 轻模糊 */
.nm-overlay {
  position: fixed;
  inset: 0;
  z-index: 2200;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 24px;
  background: hsl(var(--sh-color) / 0.32);
  backdrop-filter: blur(6px);
  -webkit-backdrop-filter: blur(6px);
}
/* 弹窗（设计稿 .modal）：不透明暖白面 + 浮起阴影 + 顶部高光 */
.nm-panel {
  position: relative;
  display: flex;
  flex-direction: column;
  width: 100%;
  max-height: calc(100vh - 48px);
  border-radius: var(--r-lg);
  border: 1px solid var(--line);
  background: var(--surface);
  box-shadow: var(--elev-float), var(--inset-top);
  overflow: hidden;
}

.nm-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
  padding: 20px 22px 14px;
}
.nm-head :deep(h3),
.nm-head h3 {
  margin: 0;
  font-family: var(--font-display);
  font-size: var(--t-lg);
  font-weight: 600;
  letter-spacing: -0.01em;
  color: var(--ink);
}
.nm-x {
  flex-shrink: 0;
  display: inline-flex;
  width: 32px;
  height: 32px;
  margin: -4px -4px 0 0;
  align-items: center;
  justify-content: center;
  border-radius: var(--r-xs);
  border: none;
  background: transparent;
  color: var(--ink-2);
  cursor: pointer;
  transition: background var(--dur) var(--ease), color var(--dur) var(--ease);
}
.nm-x:hover {
  background: var(--primary-soft);
  color: var(--primary-strong);
}

.nm-body {
  padding: 4px 22px 8px;
  overflow-y: auto;
}
.nm-foot {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 10px;
  padding: 16px 22px 20px;
  margin-top: 8px;
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
