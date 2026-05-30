import { defineStore } from 'pinia'
import { ref } from 'vue'

// 好友在线状态：由 WS 推送的 {type:"presence", data:{userId, online}} 维护。
// 局限：后端仅在状态跳变时推送，无「上线快照」，故初次连上时各好友状态未知（默认离线）。
export const usePresenceStore = defineStore('presence', () => {
  const onlineIds = ref<Set<number>>(new Set())

  function setOnline(userId: number, online: boolean) {
    if (!userId) return
    const next = new Set(onlineIds.value)
    if (online) next.add(userId)
    else next.delete(userId)
    onlineIds.value = next
  }

  function isOnline(userId?: number): boolean {
    return userId != null && onlineIds.value.has(userId)
  }

  function reset() {
    onlineIds.value = new Set()
  }

  return { onlineIds, setOnline, isOnline, reset }
})
