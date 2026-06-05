import { defineStore } from 'pinia'
import { ref } from 'vue'
import { listFriendRequests } from '@/api/user'

// 全局「待处理好友申请」计数：供侧栏「联系人」角标使用，
// 由 AppLayout 全局轮询刷新，使 B 在任何页面都能感知到新申请。
export const useFriendStore = defineStore('friends', () => {
  const pendingCount = ref(0)

  async function refreshPending() {
    try {
      const rs = await listFriendRequests()
      pendingCount.value = rs.filter((r) => r.status === 0).length
    } catch {
      /* 静默：轮询失败不打扰用户 */
    }
  }

  return { pendingCount, refreshPending }
})
