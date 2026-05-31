<script setup lang="ts">
import { computed } from 'vue'
import { fileKind, type Message } from '@/api/im'

const props = defineProps<{ message: Message }>()

// 文件消息：contentType 2=图片 3=文件（音视频/文档按 MIME 细分）
const isFileMsg = computed(() => props.message.contentType === 2 || props.message.contentType === 3)
const kind = computed(() => fileKind(props.message.mediaType))

function fmtSize(n?: number): string {
  if (!n) return ''
  if (n < 1024) return `${n} B`
  if (n < 1024 * 1024) return `${(n / 1024).toFixed(1)} KB`
  return `${(n / 1024 / 1024).toFixed(1)} MB`
}

function openImage() {
  if (props.message.fileUrl) window.open(props.message.fileUrl, '_blank', 'noopener')
}
</script>

<template>
  <!-- 撤回 / 文本 / 系统：纯文本 -->
  <span v-if="message.recalled || !isFileMsg" class="text">{{ message.content }}</span>

  <!-- 图片 -->
  <img
    v-else-if="kind === 'image'"
    class="msg-image"
    :src="message.fileUrl"
    :alt="message.fileName"
    loading="lazy"
    @click="openImage"
  />

  <!-- 视频 -->
  <video
    v-else-if="kind === 'video'"
    class="msg-video"
    :src="message.fileUrl"
    controls
    preload="metadata"
  />

  <!-- 音频 -->
  <audio
    v-else-if="kind === 'audio'"
    class="msg-audio"
    :src="message.fileUrl"
    controls
    preload="metadata"
  />

  <!-- 其它文件：下载卡片 -->
  <a
    v-else
    class="msg-file"
    :href="message.fileUrl"
    :download="message.fileName"
    target="_blank"
    rel="noopener"
  >
    <span class="file-icon">
      <svg viewBox="0 0 24 24" width="22" height="22" fill="none" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round">
        <path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z" /><polyline points="14 2 14 8 20 8" />
      </svg>
    </span>
    <span class="file-info">
      <span class="file-name">{{ message.fileName }}</span>
      <span class="file-size">{{ fmtSize(message.fileSize) }}</span>
    </span>
  </a>
</template>

<style scoped>
.text {
  white-space: pre-wrap;
  word-break: break-word;
}
.msg-image {
  display: block;
  max-width: 240px;
  max-height: 280px;
  border-radius: 10px;
  object-fit: cover;
  cursor: zoom-in;
}
.msg-video {
  display: block;
  max-width: 280px;
  max-height: 320px;
  border-radius: 10px;
  background: #000;
}
.msg-audio {
  width: 240px;
  height: 40px;
}
.msg-file {
  display: flex;
  align-items: center;
  gap: 10px;
  min-width: 180px;
  max-width: 260px;
  text-decoration: none;
  color: inherit;
}
.file-icon {
  flex-shrink: 0;
  display: inline-flex;
  opacity: 0.85;
}
.file-info {
  display: flex;
  flex-direction: column;
  min-width: 0;
}
.file-name {
  font-size: 13px;
  font-weight: 600;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.file-size {
  font-size: 11px;
  opacity: 0.7;
}
</style>
