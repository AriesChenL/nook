import { computed, ref, watchEffect } from 'vue'

export type ThemeMode = 'auto' | 'light' | 'dark'

const STORAGE_KEY = 'nook.theme'
const mql = window.matchMedia('(prefers-color-scheme: dark)')

function readInitial(): ThemeMode {
  const v = localStorage.getItem(STORAGE_KEY)
  return v === 'light' || v === 'dark' || v === 'auto' ? v : 'auto'
}

const mode = ref<ThemeMode>(readInitial())
const systemDark = ref(mql.matches)

// 当前实际是否深色（auto 时跟随系统）
const isDark = computed(() => mode.value === 'dark' || (mode.value === 'auto' && systemDark.value))

function applyTheme() {
  document.documentElement.classList.toggle('dark', isDark.value)
}

mql.addEventListener('change', (e) => {
  systemDark.value = e.matches
})

watchEffect(() => {
  localStorage.setItem(STORAGE_KEY, mode.value)
  applyTheme()
})

export function useTheme() {
  function setMode(m: ThemeMode) {
    mode.value = m
  }
  // 单按钮切换：直接锁定为亮/深（不再回到 auto），与设计稿的太阳/月亮按钮一致
  function toggleDark() {
    mode.value = isDark.value ? 'light' : 'dark'
  }
  return { mode, isDark, setMode, toggleDark }
}
