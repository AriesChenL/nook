<script setup lang="ts">
import NookConfirm from '@/components/NookConfirm.vue'
import NookToast from '@/components/NookToast.vue'
</script>

<template>
  <router-view />
  <!-- 全局确认框宿主 -->
  <NookConfirm />
  <!-- 全局 Toast 宿主 -->
  <NookToast />
</template>
