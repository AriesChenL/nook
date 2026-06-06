<script setup lang="ts">
import { computed } from 'vue'
import { useTheme, type ThemeMode } from '@/composables/useTheme'

const { mode, setMode } = useTheme()

interface Option {
  value: ThemeMode
  label: string
}

const options: Option[] = [
  { value: 'light', label: '浅色' },
  { value: 'auto', label: '跟随系统' },
  { value: 'dark', label: '深色' }
]

const activeIndex = computed(() => options.findIndex((o) => o.value === mode.value))
</script>

<template>
  <div class="theme-switch" role="radiogroup" aria-label="主题">
    <span
      class="switch-thumb"
      :style="{ transform: `translateX(${activeIndex * 100}%)` }"
      aria-hidden="true"
    />
    <button
      v-for="o in options"
      :key="o.value"
      type="button"
      role="radio"
      :aria-checked="mode === o.value"
      :aria-label="o.label"
      :title="o.label"
      :class="['switch-btn', { active: mode === o.value }]"
      @click="setMode(o.value)"
    >
      <!-- light -->
      <svg v-if="o.value === 'light'" viewBox="0 0 24 24" width="17" height="17" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
        <circle cx="12" cy="12" r="4" />
        <path d="M12 2v2M12 20v2M4.93 4.93l1.41 1.41M17.66 17.66l1.41 1.41M2 12h2M20 12h2M4.93 19.07l1.41-1.41M17.66 6.34l1.41-1.41" />
      </svg>
      <!-- auto (half-filled circle) -->
      <svg v-else-if="o.value === 'auto'" viewBox="0 0 24 24" width="17" height="17" aria-hidden="true">
        <circle cx="12" cy="12" r="9" fill="none" stroke="currentColor" stroke-width="1.8" />
        <path d="M12 3 a9 9 0 0 1 0 18 Z" fill="currentColor" />
      </svg>
      <!-- dark -->
      <svg v-else viewBox="0 0 24 24" width="17" height="17" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
        <path d="M21 12.8A9 9 0 1 1 11.2 3a7 7 0 0 0 9.8 9.8Z" />
      </svg>
    </button>
  </div>
</template>

<style scoped>
/* 内联分段开关 —— 三个图标常驻，点击即切换，滑块指示当前模式，无弹层 */
.theme-switch {
  position: relative;
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  height: 36px;
  padding: 3px;
  border-radius: var(--r-sm);
  border: 1px solid var(--nook-surface-border);
  background: var(--nook-surface-sunken);
  box-shadow: inset 0 1px 2px rgba(15, 58, 54, 0.06);
}
html.dark .theme-switch {
  box-shadow: inset 0 1px 2px rgba(0, 0, 0, 0.3);
}

/* 滑动高亮块 —— 柔和玻璃药丸在三个图标间滑动 */
.switch-thumb {
  position: absolute;
  top: 3px;
  left: 3px;
  width: calc((100% - 6px) / 3);
  height: calc(100% - 6px);
  border-radius: 9px;
  background: var(--nook-surface-raised);
  box-shadow: 0 4px 10px -6px rgba(15, 118, 110, 0.45);
  transition: transform var(--dur) var(--ease-spring);
  pointer-events: none;
}
html.dark .switch-thumb {
  background: linear-gradient(135deg, rgba(45, 212, 191, 0.24), rgba(15, 118, 110, 0.3));
  box-shadow: 0 4px 12px -6px rgba(0, 0, 0, 0.55);
}

.switch-btn {
  position: relative;
  z-index: 1;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  border: none;
  border-radius: 9px;
  background: transparent;
  color: var(--nook-text-muted);
  cursor: pointer;
  transition: color var(--dur) var(--ease-out), transform var(--dur-fast) var(--ease-out);
}
.switch-btn svg {
  transition: transform var(--dur) var(--ease-spring);
}
.switch-btn:hover:not(.active) {
  color: var(--nook-text);
}
.switch-btn:focus-visible {
  outline: 2px solid var(--nook-primary);
  outline-offset: 1px;
}
.switch-btn:active {
  transform: scale(0.9);
}
.switch-btn.active {
  color: var(--nook-primary-deep);
}
.switch-btn.active svg {
  transform: scale(1.1);
}
html.dark .switch-btn.active {
  color: var(--nook-primary-soft);
}

@media (prefers-reduced-motion: reduce) {
  .switch-thumb,
  .switch-btn,
  .switch-btn svg {
    transition: none;
  }
}
</style>
