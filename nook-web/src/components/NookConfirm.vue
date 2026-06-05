<script setup lang="ts">
import NookModal from './NookModal.vue'
import { confirmState, settleConfirm } from '@/composables/useConfirm'
</script>

<template>
  <NookModal
    :model-value="confirmState.visible"
    :title="confirmState.title"
    :width="400"
    @update:model-value="(v: boolean) => !v && settleConfirm(false)"
  >
    <p class="confirm-msg">{{ confirmState.message }}</p>
    <template #footer>
      <button class="nk-btn nk-btn--ghost" type="button" @click="settleConfirm(false)">
        {{ confirmState.cancelText }}
      </button>
      <button
        class="nk-btn"
        :class="confirmState.danger ? 'nk-btn--danger' : 'nk-btn--primary'"
        type="button"
        @click="settleConfirm(true)"
      >
        {{ confirmState.confirmText }}
      </button>
    </template>
  </NookModal>
</template>

<style scoped>
.confirm-msg {
  margin: 0;
  font-size: 14px;
  line-height: 1.65;
  color: var(--nook-text-muted);
}
</style>
