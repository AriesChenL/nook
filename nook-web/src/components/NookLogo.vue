<script setup lang="ts">
/**
 * Nook 品牌标记 —— Doorway「负空间门廊」
 * 实心方块以负空间挖出一道门廊，门内一粒发光的点：走进属于你的角落。
 * 内联 SVG，跟随当前配色变量（--primary / --on-primary / --accent）。
 */
withDefaults(defineProps<{ size?: number | string; rounded?: boolean }>(), {
  size: 32,
  rounded: true
})
</script>

<template>
  <svg
    class="nook-logo"
    :class="{ 'is-rounded': rounded }"
    :width="size"
    :height="size"
    viewBox="0 0 64 64"
    xmlns="http://www.w3.org/2000/svg"
    role="img"
    aria-label="Nook"
  >
    <rect width="64" height="64" :rx="rounded ? 17 : 0" fill="var(--primary)" />
    <path d="M20 48 V30 a12 12 0 0 1 24 0 V48 Z" fill="var(--on-primary)" />
    <path d="M27 48 V31 a5 5 0 0 1 10 0 V48 Z" fill="var(--primary)" />
    <circle cx="32" cy="38" r="3.1" fill="var(--accent)" />
  </svg>
</template>

<style scoped>
.nook-logo {
  display: block;
  flex-shrink: 0;
  box-shadow: var(--elev-1), inset 0 1px 0 rgba(255, 255, 255, 0.18);
  border-radius: 26.5%;
}
.nook-logo:not(.is-rounded) {
  box-shadow: none;
  border-radius: 0;
}
</style>
