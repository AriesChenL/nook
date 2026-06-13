<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { toast } from '@/composables/useToast'
import { listFriends, type Friend } from '@/api/user'
import { createGroup, type Conversation } from '@/api/im'

const props = defineProps<{ modelValue: boolean }>()
const emit = defineEmits<{
  'update:modelValue': [boolean]
  created: [Conversation]
}>()

const friends = ref<Friend[]>([])
const loading = ref(false)
const selected = ref<Set<string>>(new Set())
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

function toggle(id: string) {
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
    toast.warning('至少选择 1 位好友')
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
    toast.success('群聊已创建')
    emit('created', conv)
    emit('update:modelValue', false)
  } catch (e: any) {
    toast.error(e?.message ?? '建群失败')
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
/* 遮罩 + 弹窗（设计稿 .scrim / .modal）*/
.overlay {
  position: fixed;
  inset: 0;
  z-index: 2000;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 24px;
  background: hsl(var(--sh-color) / 0.32);
  backdrop-filter: blur(6px);
  -webkit-backdrop-filter: blur(6px);
}
.panel {
  position: relative;
  display: flex;
  flex-direction: column;
  width: 100%;
  max-width: 460px;
  max-height: calc(100vh - 48px);
  border-radius: var(--r-lg);
  border: 1px solid var(--line);
  background: var(--surface);
  box-shadow: var(--elev-float), var(--inset-top);
  overflow: hidden;
}
.panel-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 20px 22px 14px;
}
.panel-head h3 {
  margin: 0;
  font-family: var(--font-display);
  font-size: var(--t-lg);
  font-weight: 600;
  color: var(--ink);
}
.x {
  display: inline-flex;
  width: 32px;
  height: 32px;
  margin: -4px -4px 0 0;
  align-items: center;
  justify-content: center;
  border-radius: var(--r-xs);
  border: none;
  background: transparent;
  color: var(--ink-2);
  cursor: pointer;
  transition: background var(--dur) var(--ease), color var(--dur) var(--ease);
}
.x:hover {
  background: var(--primary-soft);
  color: var(--primary-strong);
}

.field {
  padding: 0 22px 10px;
}
.field input {
  width: 100%;
  height: 42px;
  padding: 0 14px;
  border-radius: var(--r-sm);
  border: 1px solid var(--line-strong);
  background: var(--surface);
  font: inherit;
  font-size: var(--t-base);
  color: var(--ink);
  outline: none;
  box-shadow: inset 0 1px 3px hsl(var(--sh-color) / 0.07);
  transition: border-color var(--dur) var(--ease), box-shadow var(--dur) var(--ease);
}
.field input::placeholder { color: var(--ink-3); }
.field input:focus {
  border-color: var(--primary);
  box-shadow: var(--ring), inset 0 1px 3px hsl(var(--sh-color) / 0.05);
}

.chips {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  padding: 0 22px 10px;
}
.chip {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 4px 10px;
  border-radius: var(--r-pill);
  background: var(--primary-soft);
  color: var(--primary-strong);
  font-size: 12.5px;
  cursor: pointer;
}

.list {
  flex: 1;
  overflow-y: auto;
  padding: 4px 14px;
  min-height: 120px;
}
.list::-webkit-scrollbar {
  width: 5px;
}
.list::-webkit-scrollbar-thumb {
  background: hsl(var(--sh-color) / 0.2);
  border-radius: 3px;
}
.empty {
  padding: 30px 0;
  text-align: center;
  color: var(--ink-3);
  font-size: 13px;
}
.row {
  display: flex;
  align-items: center;
  gap: 12px;
  width: 100%;
  padding: 8px 10px;
  border: none;
  border-radius: var(--r-md);
  background: transparent;
  cursor: pointer;
  text-align: left;
  font: inherit;
  transition: background var(--dur) var(--ease);
}
.row:hover {
  background: var(--surface-2);
}
.row.on {
  background: var(--primary-soft);
}
/* 勾选框（设计稿 .check）*/
.check {
  flex-shrink: 0;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 20px;
  height: 20px;
  border-radius: 6px;
  border: 1px solid var(--line-strong);
  background: var(--surface);
  box-shadow: inset 0 1px 2px hsl(var(--sh-color) / 0.1);
  color: var(--on-primary);
  transition: background var(--dur) var(--ease), border-color var(--dur) var(--ease);
}
.check.on {
  background: var(--grad-primary);
  border-color: transparent;
}
.avatar {
  flex-shrink: 0;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 36px;
  height: 36px;
  border-radius: 38%;
  background: var(--grad-primary);
  color: var(--on-primary);
  font-weight: 700;
  font-family: var(--font-display);
  font-size: 14px;
  box-shadow: var(--elev-1), inset 0 1px 0 rgba(255, 255, 255, 0.2);
}
.name {
  font-size: var(--t-base);
  font-weight: 500;
  color: var(--ink);
}

.panel-foot {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 14px 22px 18px;
  border-top: 1px solid var(--line);
  margin-top: 6px;
}
.count {
  font-size: 12.5px;
  color: var(--ink-3);
}
.acts {
  display: flex;
  gap: 8px;
}
.btn {
  height: 40px;
  padding: 0 18px;
  border-radius: var(--r-sm);
  border: 1px solid transparent;
  font-family: var(--font-sans);
  font-size: var(--t-base);
  font-weight: 600;
  cursor: pointer;
  transition: filter var(--dur) var(--ease), transform var(--dur-fast) var(--ease), box-shadow var(--dur) var(--ease), background var(--dur) var(--ease), border-color var(--dur) var(--ease);
}
.btn.primary {
  background: var(--grad-primary);
  color: var(--on-primary);
  box-shadow: var(--elev-1), inset 0 1px 0 rgba(255, 255, 255, 0.28);
}
.btn.primary:hover:not(:disabled) {
  filter: brightness(1.04);
  transform: translateY(-1px);
  box-shadow: var(--elev-2), inset 0 1px 0 rgba(255, 255, 255, 0.32);
}
.btn.ghost {
  background: var(--surface);
  border-color: var(--line-strong);
  color: var(--ink);
  box-shadow: var(--elev-1), var(--inset-top);
}
.btn.ghost:hover:not(:disabled) {
  border-color: var(--primary);
  color: var(--primary-strong);
}
.btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.fade-enter-active,
.fade-leave-active {
  transition: opacity var(--dur) var(--ease-out);
}
.fade-enter-active .panel {
  transition: transform var(--dur-slow) var(--ease-spring), opacity var(--dur-slow) var(--ease-out);
}
.fade-leave-active .panel {
  transition: transform 160ms ease, opacity 160ms ease;
}
.fade-enter-from,
.fade-leave-to {
  opacity: 0;
}
.fade-enter-from .panel {
  opacity: 0;
  transform: translateY(18px) scale(0.96);
}
.fade-leave-to .panel {
  opacity: 0;
  transform: translateY(8px) scale(0.98);
}
@media (prefers-reduced-motion: reduce) {
  .fade-enter-active,
  .fade-leave-active,
  .fade-enter-active .panel,
  .fade-leave-active .panel {
    transition: none;
  }
}
</style>
