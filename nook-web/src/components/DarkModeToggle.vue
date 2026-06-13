<script setup lang="ts">
import { useTheme } from '@/composables/useTheme'

const { isDark, toggleDark } = useTheme()
</script>

<template>
  <button
    class="dark-toggle"
    type="button"
    :aria-pressed="isDark"
    :aria-label="isDark ? '切换到亮色模式' : '切换到深色模式'"
    :title="isDark ? '亮色模式' : '深色模式'"
    @click="toggleDark"
  >
    <!-- 深色时显示太阳（点击回亮）；亮色时显示月亮（点击转深）—— 与设计稿一致 -->
    <svg v-if="isDark" viewBox="0 0 24 24" width="19" height="19" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
      <circle cx="12" cy="12" r="4" />
      <path d="M12 2v2M12 20v2M4.93 4.93l1.41 1.41M17.66 17.66l1.41 1.41M2 12h2M20 12h2M4.93 19.07l1.41-1.41M17.66 6.34l1.41-1.41" />
    </svg>
    <svg v-else viewBox="0 0 24 24" width="19" height="19" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
      <path d="M21 12.8A9 9 0 1 1 11.2 3a7 7 0 0 0 9.8 9.8Z" />
    </svg>
  </button>
</template>

<style scoped>
/* 描边图标按钮（设计稿 .icon-btn.outline）：细描边 + 顶部高光，hover 主色 */
.dark-toggle {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 36px;
  height: 36px;
  flex-shrink: 0;
  border-radius: var(--r-sm);
  border: 1px solid var(--line-strong);
  background: var(--surface);
  color: var(--ink-2);
  cursor: pointer;
  box-shadow: var(--inset-top);
  transition: background var(--dur) var(--ease), color var(--dur) var(--ease),
    border-color var(--dur) var(--ease), transform var(--dur-fast) var(--ease);
}
.dark-toggle:hover {
  border-color: var(--primary);
  background: var(--primary-soft);
  color: var(--primary-strong);
  transform: translateY(-1px);
}
.dark-toggle:active {
  transform: translateY(0.5px) scale(0.96);
}
.dark-toggle:focus-visible {
  box-shadow: var(--ring);
}
.dark-toggle svg {
  transition: transform var(--dur) var(--ease-spring);
}
.dark-toggle:hover svg {
  transform: rotate(-12deg) scale(1.08);
}

@media (prefers-reduced-motion: reduce) {
  .dark-toggle,
  .dark-toggle svg {
    transition: none;
  }
}
</style>
