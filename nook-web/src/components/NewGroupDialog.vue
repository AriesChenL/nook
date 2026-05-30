<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { listFriends, type Friend } from '@/api/user'
import { createGroup, type Conversation } from '@/api/im'

const props = defineProps<{ modelValue: boolean }>()
const emit = defineEmits<{
  'update:modelValue': [boolean]
  created: [Conversation]
}>()

const friends = ref<Friend[]>([])
const loading = ref(false)
const selected = ref<Set<number>>(new Set())
const groupName = ref('')
const keyword = ref('')
const submitting = ref(false)

const filtered = computed(() => {
  const k = keyword.value.trim().toLowerCase()
  if (!k) return friends.value
  return friends.value.filter(
    (f) => f.nickname.toLowerCase().includes(k) || f.username.toLowerCase().includes(k)
  )
})

const selectedFriends = computed(() => friends.value.filter((f) => selected.value.has(f.userId)))

watch(
  () => props.modelValue,
  async (open) => {
    if (!open) return
    selected.value = new Set()
    groupName.value = ''
    keyword.value = ''
    if (!friends.value.length) {
      loading.value = true
      try {
        friends.value = await listFriends()
      } finally {
        loading.value = false
      }
    }
  }
)

function toggle(id: number) {
  const s = new Set(selected.value)
  if (s.has(id)) s.delete(id)
  else s.add(id)
  selected.value = s
}

function close() {
  if (submitting.value) return
  emit('update:modelValue', false)
}

async function submit() {
  const ids = [...selected.value]
  if (!ids.length) {
    ElMessage.warning('至少选择 1 位好友')
    return
  }
  const fallback = selectedFriends.value
    .map((f) => f.nickname)
    .slice(0, 3)
    .join('、')
  const name = groupName.value.trim() || fallback
  submitting.value = true
  try {
    const conv = await createGroup(name, ids)
    ElMessage.success('群聊已创建')
    emit('created', conv)
    emit('update:modelValue', false)
  } catch (e: any) {
    ElMessage.error(e?.message ?? '建群失败')
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <Teleport to="body">
    <Transition name="fade">
      <div v-if="modelValue" class="overlay" @click.self="close">
        <div class="panel" role="dialog" aria-modal="true" aria-label="发起群聊">
          <header class="panel-head">
            <h3>发起群聊</h3>
            <button class="x" type="button" aria-label="关闭" @click="close">
              <svg viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round"><line x1="18" y1="6" x2="6" y2="18" /><line x1="6" y1="6" x2="18" y2="18" /></svg>
            </button>
          </header>

          <div class="field">
            <input v-model="groupName" maxlength="30" placeholder="群名称（留空自动生成）" />
          </div>

          <div class="field search">
            <input v-model="keyword" placeholder="搜索好友" />
          </div>

          <div v-if="selectedFriends.length" class="chips">
            <span v-for="f in selectedFriends" :key="f.userId" class="chip" @click="toggle(f.userId)">
              {{ f.nickname }}
              <svg viewBox="0 0 24 24" width="12" height="12" fill="none" stroke="currentColor" stroke-width="2.4" stroke-linecap="round"><line x1="18" y1="6" x2="6" y2="18" /><line x1="6" y1="6" x2="18" y2="18" /></svg>
            </span>
          </div>

          <div class="list" :aria-busy="loading || undefined">
            <el-skeleton v-if="loading" :rows="5" animated />
            <div v-else-if="!filtered.length" class="empty">没有可选好友</div>
            <button
              v-for="f in filtered"
              v-else
              :key="f.userId"
              type="button"
              :class="['row', { on: selected.has(f.userId) }]"
              @click="toggle(f.userId)"
            >
              <span class="check" :class="{ on: selected.has(f.userId) }">
                <svg v-if="selected.has(f.userId)" viewBox="0 0 24 24" width="12" height="12" fill="none" stroke="currentColor" stroke-width="3" stroke-linecap="round" stroke-linejoin="round"><polyline points="20 6 9 17 4 12" /></svg>
              </span>
              <span class="avatar">{{ (f.nickname[0] ?? '?').toUpperCase() }}</span>
              <span class="name">{{ f.nickname }}</span>
            </button>
          </div>

          <footer class="panel-foot">
            <span class="count">已选 {{ selected.size }}</span>
            <div class="acts">
              <button class="btn ghost" type="button" @click="close">取消</button>
              <button class="btn primary" type="button" :disabled="submitting || !selected.size" @click="submit">
                {{ submitting ? '创建中…' : '创建群聊' }}
              </button>
            </div>
          </footer>
        </div>
      </div>
    </Transition>
  </Teleport>
</template>

<style scoped>
.overlay {
  position: fixed;
  inset: 0;
  z-index: 2000;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 20px;
  background: rgba(15, 23, 42, 0.4);
  backdrop-filter: blur(4px);
}
.panel {
  display: flex;
  flex-direction: column;
  width: 100%;
  max-width: 420px;
  max-height: 80vh;
  border-radius: 20px;
  border: 1px solid var(--nook-surface-border);
  background: var(--nook-surface);
  backdrop-filter: blur(20px) saturate(160%);
  -webkit-backdrop-filter: blur(20px) saturate(160%);
  box-shadow: 0 30px 60px -20px rgba(15, 118, 110, 0.45);
  overflow: hidden;
}
.panel-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 18px 20px 12px;
}
.panel-head h3 {
  margin: 0;
  font-family: var(--nook-font-display);
  font-size: 17px;
  font-weight: 700;
  color: var(--nook-text);
}
.x {
  display: inline-flex;
  width: 30px;
  height: 30px;
  align-items: center;
  justify-content: center;
  border-radius: 8px;
  border: none;
  background: transparent;
  color: var(--nook-text-muted);
  cursor: pointer;
}
.x:hover {
  background: rgba(20, 184, 166, 0.1);
  color: var(--nook-text);
}

.field {
  padding: 0 20px 10px;
}
.field input {
  width: 100%;
  height: 40px;
  padding: 0 14px;
  border-radius: 11px;
  border: 1px solid var(--nook-surface-border);
  background: rgba(255, 255, 255, 0.6);
  font: inherit;
  font-size: 14px;
  color: var(--nook-text);
  outline: none;
  transition: border-color 180ms ease;
}
html.dark .field input {
  background: rgba(4, 47, 46, 0.5);
}
.field input:focus {
  border-color: var(--nook-primary);
}

.chips {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  padding: 0 20px 10px;
}
.chip {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 4px 8px;
  border-radius: 8px;
  background: rgba(20, 184, 166, 0.12);
  color: var(--nook-primary-deep);
  font-size: 12.5px;
  cursor: pointer;
}
html.dark .chip {
  color: var(--nook-primary-soft);
}

.list {
  flex: 1;
  overflow-y: auto;
  padding: 4px 12px;
  min-height: 120px;
}
.list::-webkit-scrollbar {
  width: 5px;
}
.list::-webkit-scrollbar-thumb {
  background: rgba(20, 184, 166, 0.25);
  border-radius: 3px;
}
.empty {
  padding: 30px 0;
  text-align: center;
  color: var(--nook-text-muted);
  font-size: 13px;
}
.row {
  display: flex;
  align-items: center;
  gap: 12px;
  width: 100%;
  padding: 8px 10px;
  border: none;
  border-radius: 11px;
  background: transparent;
  cursor: pointer;
  text-align: left;
  font: inherit;
  transition: background 150ms ease;
}
.row:hover {
  background: rgba(20, 184, 166, 0.08);
}
.row.on {
  background: rgba(20, 184, 166, 0.1);
}
.check {
  flex-shrink: 0;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 20px;
  height: 20px;
  border-radius: 50%;
  border: 1.8px solid var(--nook-surface-border);
  color: #fff;
  transition: background 150ms ease, border-color 150ms ease;
}
.check.on {
  background: var(--nook-primary);
  border-color: var(--nook-primary);
}
.avatar {
  flex-shrink: 0;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 36px;
  height: 36px;
  border-radius: 10px;
  background: linear-gradient(135deg, #14b8a6, #fb923c);
  color: #fff;
  font-weight: 700;
  font-family: var(--nook-font-display);
  font-size: 14px;
}
.name {
  font-size: 14px;
  font-weight: 500;
  color: var(--nook-text);
}

.panel-foot {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 20px 16px;
  border-top: 1px solid var(--nook-surface-border);
}
.count {
  font-size: 12.5px;
  color: var(--nook-text-muted);
}
.acts {
  display: flex;
  gap: 8px;
}
.btn {
  height: 36px;
  padding: 0 16px;
  border-radius: 10px;
  border: none;
  font: inherit;
  font-size: 13.5px;
  font-weight: 500;
  cursor: pointer;
  transition: filter 180ms ease, background 180ms ease, border-color 180ms ease;
}
.btn.primary {
  background: linear-gradient(135deg, #14b8a6, #0f766e);
  color: #fff;
}
.btn.primary:hover:not(:disabled) {
  filter: brightness(1.06);
}
.btn.ghost {
  background: transparent;
  border: 1px solid var(--nook-surface-border);
  color: var(--nook-text);
}
.btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.fade-enter-active,
.fade-leave-active {
  transition: opacity 200ms ease;
}
.fade-enter-from,
.fade-leave-to {
  opacity: 0;
}
@media (prefers-reduced-motion: reduce) {
  .fade-enter-active,
  .fade-leave-active {
    transition: none;
  }
}
</style>
