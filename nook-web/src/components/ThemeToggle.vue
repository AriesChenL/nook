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
  { value: 'auto', label: '自动' },
  { value: 'dark', label: '深色' }
]

const current = computed(() => options.find((o) => o.value === mode.value) ?? options[1])
</script>

<template>
  <el-popover
    trigger="click"
    placement="bottom-end"
    :width="220"
    :show-arrow="false"
    :offset="8"
    popper-class="theme-popper"
    transition="el-zoom-in-top"
  >
    <template #reference>
      <button
        type="button"
        class="theme-trigger"
        :aria-label="`切换主题，当前：${current.label}`"
      >
        <!-- light -->
        <svg v-if="mode === 'light'" viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
          <circle cx="12" cy="12" r="4" />
          <path d="M12 2v2M12 20v2M4.93 4.93l1.41 1.41M17.66 17.66l1.41 1.41M2 12h2M20 12h2M4.93 19.07l1.41-1.41M17.66 6.34l1.41-1.41" />
        </svg>
        <!-- auto (half-filled circle) -->
        <svg v-else-if="mode === 'auto'" viewBox="0 0 24 24" width="18" height="18" aria-hidden="true">
          <circle cx="12" cy="12" r="9" fill="none" stroke="currentColor" stroke-width="1.8" />
          <path d="M12 3 a9 9 0 0 1 0 18 Z" fill="currentColor" />
        </svg>
        <!-- dark -->
        <svg v-else viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
          <path d="M21 12.8A9 9 0 1 1 11.2 3a7 7 0 0 0 9.8 9.8Z" />
        </svg>
      </button>
    </template>

    <div class="theme-seg" role="radiogroup" aria-label="主题">
      <button
        v-for="o in options"
        :key="o.value"
        type="button"
        role="radio"
        :aria-checked="mode === o.value"
        :class="['seg-btn', { active: mode === o.value }]"
        @click="setMode(o.value)"
      >
        <svg v-if="o.value === 'light'" viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
          <circle cx="12" cy="12" r="4" />
          <path d="M12 2v2M12 20v2M4.93 4.93l1.41 1.41M17.66 17.66l1.41 1.41M2 12h2M20 12h2M4.93 19.07l1.41-1.41M17.66 6.34l1.41-1.41" />
        </svg>
        <svg v-else-if="o.value === 'auto'" viewBox="0 0 24 24" width="16" height="16" aria-hidden="true">
          <circle cx="12" cy="12" r="9" fill="none" stroke="currentColor" stroke-width="1.8" />
          <path d="M12 3 a9 9 0 0 1 0 18 Z" fill="currentColor" />
        </svg>
        <svg v-else viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
          <path d="M21 12.8A9 9 0 1 1 11.2 3a7 7 0 0 0 9.8 9.8Z" />
        </svg>
        <span>{{ o.label }}</span>
      </button>
    </div>
  </el-popover>
</template>

<style scoped>
.theme-trigger {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 36px;
  height: 36px;
  border-radius: 12px;
  border: 1px solid var(--nook-surface-border);
  background: var(--nook-surface);
  backdrop-filter: blur(12px) saturate(140%);
  -webkit-backdrop-filter: blur(12px) saturate(140%);
  color: var(--nook-text);
  cursor: pointer;
  transition: border-color 200ms ease, background 200ms ease, color 200ms ease, transform 200ms ease;
}
.theme-trigger:hover {
  border-color: var(--nook-primary);
  color: var(--nook-primary-deep);
}
html.dark .theme-trigger:hover {
  color: var(--nook-primary-soft);
}
.theme-trigger:focus-visible {
  outline: 3px solid rgba(20, 184, 166, 0.45);
  outline-offset: 2px;
}
.theme-trigger:active {
  transform: scale(0.96);
}
</style>

<!-- 全局：popover 内容由 teleport 渲染到 body，不能用 scoped -->
<style>
.theme-popper.el-popover.el-popper {
  padding: 6px;
  border-radius: 16px;
  border: 1px solid var(--nook-surface-border);
  background: var(--nook-surface);
  backdrop-filter: blur(18px) saturate(150%);
  -webkit-backdrop-filter: blur(18px) saturate(150%);
  box-shadow: 0 20px 50px -20px rgba(15, 118, 110, 0.35);
}
html.dark .theme-popper.el-popover.el-popper {
  box-shadow: 0 20px 50px -20px rgba(0, 0, 0, 0.6);
}

.theme-seg {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 4px;
  padding: 2px;
  border-radius: 12px;
  background: rgba(15, 118, 110, 0.06);
}
html.dark .theme-seg {
  background: rgba(94, 234, 212, 0.08);
}

.seg-btn {
  display: inline-flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 4px;
  padding: 8px 6px;
  border: none;
  border-radius: 10px;
  background: transparent;
  color: var(--nook-text-muted);
  font-family: var(--nook-font-sans);
  font-size: 12.5px;
  font-weight: 500;
  cursor: pointer;
  transition: background 180ms ease, color 180ms ease, transform 180ms ease;
}
.seg-btn:hover:not(.active) {
  color: var(--nook-text);
  background: rgba(20, 184, 166, 0.08);
}
.seg-btn:focus-visible {
  outline: 2px solid var(--nook-primary);
  outline-offset: 1px;
}
.seg-btn:active {
  transform: scale(0.96);
}
.seg-btn.active {
  color: #fff;
  background: linear-gradient(135deg, #14b8a6, #0f766e);
  box-shadow: 0 6px 14px -6px rgba(20, 184, 166, 0.6);
}
html.dark .seg-btn.active {
  background: linear-gradient(135deg, #2dd4bf, #0f766e);
}
</style>
