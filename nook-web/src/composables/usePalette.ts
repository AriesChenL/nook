import { ref, watchEffect } from 'vue'

/** 11 套配色，珊瑚 coral 为默认。结构见 styles/tokens.css */
export interface PaletteOption {
  value: string
  label: string
  /** 用于色板预览的主色（与 tokens.css 中 --primary 对齐） */
  swatch: string
}

export const PALETTES: PaletteOption[] = [
  { value: 'coral', label: '珊瑚', swatch: 'oklch(0.64 0.15 32)' },
  { value: 'clay', label: '陶土', swatch: 'oklch(0.635 0.135 43)' },
  { value: 'sage', label: '鼠尾草', swatch: 'oklch(0.575 0.082 158)' },
  { value: 'dusk', label: '暮色', swatch: 'oklch(0.57 0.125 282)' },
  { value: 'slate', label: '雾霾蓝', swatch: 'oklch(0.55 0.085 258)' },
  { value: 'pine', label: '墨绿', swatch: 'oklch(0.5 0.078 170)' },
  { value: 'indigo', label: '靛青', swatch: 'oklch(0.52 0.155 274)' },
  { value: 'plum', label: '莓紫', swatch: 'oklch(0.53 0.115 322)' },
  { value: 'graphite', label: '石墨金', swatch: 'oklch(0.4 0.025 265)' },
  { value: 'teal', label: '深湖青', swatch: 'oklch(0.58 0.085 185)' },
  { value: 'taupe', label: '奥金棕', swatch: 'oklch(0.58 0.052 60)' }
]

const STORAGE_KEY = 'nook.palette'
const DEFAULT_PALETTE = 'coral'

function readInitial(): string {
  const v = localStorage.getItem(STORAGE_KEY)
  return v && PALETTES.some((p) => p.value === v) ? v : DEFAULT_PALETTE
}

const palette = ref<string>(readInitial())

watchEffect(() => {
  localStorage.setItem(STORAGE_KEY, palette.value)
  document.documentElement.dataset.palette = palette.value
})

export function usePalette() {
  function setPalette(p: string) {
    if (PALETTES.some((o) => o.value === p)) palette.value = p
  }
  return { palette, setPalette }
}
