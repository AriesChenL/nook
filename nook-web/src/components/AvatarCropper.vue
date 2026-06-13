<script setup lang="ts">
import { ref, watch } from 'vue'
import NookModal from './NookModal.vue'

const props = defineProps<{
  modelValue: boolean
  /** 选中图片的 objectURL */
  src: string
}>()
const emit = defineEmits<{
  (e: 'update:modelValue', v: boolean): void
  (e: 'confirm', blob: Blob): void
}>()

// 裁剪框（显示像素）与输出尺寸
const FRAME = 280
const OUT = 256

const imgEl = ref<HTMLImageElement | null>(null)
const scale = ref(1)
const minScale = ref(1)
const maxScale = ref(3)
const tx = ref(0)
const ty = ref(0)
const ready = ref(false)

let natW = 0
let natH = 0

function clampPan() {
  // 图片始终覆盖裁剪框
  const w = natW * scale.value
  const h = natH * scale.value
  tx.value = Math.min(0, Math.max(FRAME - w, tx.value))
  ty.value = Math.min(0, Math.max(FRAME - h, ty.value))
}

function onLoad() {
  const el = imgEl.value
  if (!el) return
  natW = el.naturalWidth
  natH = el.naturalHeight
  // 以「覆盖」为最小缩放，初始居中
  minScale.value = FRAME / Math.min(natW, natH)
  maxScale.value = minScale.value * 4
  scale.value = minScale.value
  tx.value = (FRAME - natW * scale.value) / 2
  ty.value = (FRAME - natH * scale.value) / 2
  ready.value = true
}

// 围绕裁剪框中心缩放，保持中心点不漂移
function setScale(next: number) {
  const ns = Math.min(maxScale.value, Math.max(minScale.value, next))
  const c = FRAME / 2
  const imgX = (c - tx.value) / scale.value
  const imgY = (c - ty.value) / scale.value
  tx.value = c - imgX * ns
  ty.value = c - imgY * ns
  scale.value = ns
  clampPan()
}

function onZoom(e: Event) {
  setScale(Number((e.target as HTMLInputElement).value))
}
function onWheel(e: WheelEvent) {
  setScale(scale.value * (e.deltaY < 0 ? 1.08 : 0.926))
}

// 拖动平移
let dragging = false
let lastX = 0
let lastY = 0
function onDown(e: PointerEvent) {
  dragging = true
  lastX = e.clientX
  lastY = e.clientY
  ;(e.currentTarget as HTMLElement).setPointerCapture(e.pointerId)
}
function onMove(e: PointerEvent) {
  if (!dragging) return
  tx.value += e.clientX - lastX
  ty.value += e.clientY - lastY
  lastX = e.clientX
  lastY = e.clientY
  clampPan()
}
function onUp(e: PointerEvent) {
  dragging = false
  ;(e.currentTarget as HTMLElement).releasePointerCapture?.(e.pointerId)
}

function cancel() {
  emit('update:modelValue', false)
}

function confirm() {
  const el = imgEl.value
  if (!el) return
  const canvas = document.createElement('canvas')
  canvas.width = OUT
  canvas.height = OUT
  const ctx = canvas.getContext('2d')
  if (!ctx) return
  // 白底，避免透明 PNG 转 JPEG 出现黑块
  ctx.fillStyle = '#ffffff'
  ctx.fillRect(0, 0, OUT, OUT)
  // 裁剪框对应的原图源矩形
  const sx = -tx.value / scale.value
  const sy = -ty.value / scale.value
  const sSize = FRAME / scale.value
  ctx.imageSmoothingQuality = 'high'
  ctx.drawImage(el, sx, sy, sSize, sSize, 0, 0, OUT, OUT)
  canvas.toBlob(
    (blob) => {
      if (blob) emit('confirm', blob)
    },
    'image/jpeg',
    0.9
  )
}

// 重新打开 / 换图时重置
watch(
  () => [props.modelValue, props.src],
  () => {
    ready.value = false
  }
)
</script>

<template>
  <NookModal
    :model-value="modelValue"
    title="裁剪头像"
    :width="360"
    @update:model-value="(v) => emit('update:modelValue', v)"
  >
    <div class="cropper">
      <div
        class="stage"
        @pointerdown="onDown"
        @pointermove="onMove"
        @pointerup="onUp"
        @pointercancel="onUp"
        @wheel.prevent="onWheel"
      >
        <img
          ref="imgEl"
          :src="src"
          alt=""
          draggable="false"
          class="crop-img"
          :style="{ transform: `translate(${tx}px, ${ty}px) scale(${scale})`, visibility: ready ? 'visible' : 'hidden' }"
          @load="onLoad"
        />
        <div class="frame-ring" aria-hidden="true" />
      </div>

      <div class="zoom">
        <svg viewBox="0 0 24 24" width="15" height="15" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round"><circle cx="11" cy="11" r="7" /><line x1="8" y1="11" x2="14" y2="11" /></svg>
        <input
          type="range"
          :min="minScale"
          :max="maxScale"
          :step="(maxScale - minScale) / 100 || 0.01"
          :value="scale"
          aria-label="缩放"
          @input="onZoom"
        />
        <svg viewBox="0 0 24 24" width="17" height="17" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round"><circle cx="11" cy="11" r="7" /><line x1="8" y1="11" x2="14" y2="11" /><line x1="11" y1="8" x2="11" y2="14" /></svg>
      </div>
      <p class="hint">拖动调整位置 · 滚轮或滑块缩放</p>
    </div>

    <template #footer>
      <button class="nk-btn nk-btn--ghost" type="button" @click="cancel">取消</button>
      <button class="nk-btn use-btn" type="button" @click="confirm">使用</button>
    </template>
  </NookModal>
</template>

<style scoped>
.cropper {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: var(--space-4);
}
.stage {
  position: relative;
  width: 280px;
  height: 280px;
  border-radius: var(--r-md);
  overflow: hidden;
  background:
    repeating-conic-gradient(rgba(0, 0, 0, 0.04) 0% 25%, transparent 0% 50%) 50% / 20px 20px,
    var(--nook-surface-sunken);
  cursor: grab;
  touch-action: none;
  user-select: none;
}
.stage:active { cursor: grabbing; }
.crop-img {
  position: absolute;
  top: 0;
  left: 0;
  transform-origin: 0 0;
  max-width: none;
  will-change: transform;
}
/* 裁剪框内圈描边，提示头像形状（圆角方形，与全站头像一致） */
.frame-ring {
  position: absolute;
  inset: 0;
  border-radius: var(--r-md);
  box-shadow: inset 0 0 0 2px rgba(255, 255, 255, 0.85), inset 0 0 0 3px var(--nook-surface-border);
  pointer-events: none;
}
:global(html.dark) .frame-ring {
  box-shadow: inset 0 0 0 2px color-mix(in srgb, var(--primary) 40%, transparent);
}

.zoom {
  display: flex;
  align-items: center;
  gap: var(--space-3);
  width: 280px;
  color: var(--nook-text-muted);
}
.zoom input[type='range'] {
  flex: 1;
  -webkit-appearance: none;
  appearance: none;
  height: 4px;
  border-radius: var(--r-pill);
  background: var(--nook-surface-border);
  outline: none;
  cursor: pointer;
}
.zoom input[type='range']::-webkit-slider-thumb {
  -webkit-appearance: none;
  appearance: none;
  width: 16px;
  height: 16px;
  border-radius: 50%;
  background: var(--nook-primary);
  border: 2px solid #fff;
  box-shadow: var(--shadow-sm);
  cursor: grab;
}
.zoom input[type='range']::-moz-range-thumb {
  width: 16px;
  height: 16px;
  border: 2px solid #fff;
  border-radius: 50%;
  background: var(--nook-primary);
  cursor: grab;
}
.hint {
  margin: 0;
  font-size: var(--text-xs);
  color: var(--nook-text-faint);
}
/* 「使用」确认键：与资料页「保存」一致——浅青 wash + 深青字 + 细描边 + 柔光 */
.use-btn {
  background: var(--nook-gradient-wash);
  color: var(--nook-primary-deep);
  box-shadow: inset 0 0 0 1px var(--nook-surface-border),
    0 8px 22px -14px color-mix(in srgb, var(--primary) 70%, transparent);
}
.use-btn:hover:not(:disabled) {
  transform: translateY(-1px);
  box-shadow: inset 0 0 0 1px var(--nook-primary),
    0 12px 26px -14px color-mix(in srgb, var(--primary) 80%, transparent);
}
.use-btn:active:not(:disabled) {
  transform: translateY(0) scale(0.985);
}
:global(html.dark) .use-btn {
  color: var(--nook-primary-soft);
}
</style>
