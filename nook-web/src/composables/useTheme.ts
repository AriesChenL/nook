import { ref, watchEffect } from 'vue'

export type ThemeMode = 'auto' | 'light' | 'dark'

const STORAGE_KEY = 'nook.theme'
const mql = window.matchMedia('(prefers-color-scheme: dark)')

function readInitial(): ThemeMode {
  const v = localStorage.getItem(STORAGE_KEY)
  return v === 'light' || v === 'dark' || v === 'auto' ? v : 'auto'
}

const mode = ref<ThemeMode>(readInitial())

function applyTheme() {
  const dark = mode.value === 'dark' || (mode.value === 'auto' && mql.matches)
  document.documentElement.classList.toggle('dark', dark)
}

mql.addEventListener('change', () => {
  if (mode.value === 'auto') applyTheme()
})

watchEffect(() => {
  localStorage.setItem(STORAGE_KEY, mode.value)
  applyTheme()
})

export function useTheme() {
  function setMode(m: ThemeMode) {
    mode.value = m
  }
  return { mode, setMode }
}
