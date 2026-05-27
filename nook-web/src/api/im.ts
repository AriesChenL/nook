import http from './http'
import { USE_MOCK, delay, mockConversations, mockMessages, type MockConversation, type MockMessage } from './_mock'

export type Conversation = MockConversation
export type Message = MockMessage

export function listConversations() {
  if (USE_MOCK) return delay([...mockConversations])
  return http.get<unknown, Conversation[]>('/im/conversations')
}

export function listMessages(conversationId: number) {
  if (USE_MOCK) return delay(mockMessages[conversationId] ?? [])
  return http.get<unknown, Message[]>(`/im/conversations/${conversationId}/messages`)
}

export function sendMessage(conversationId: number, content: string) {
  if (USE_MOCK) {
    const msg: Message = {
      id: Date.now(),
      conversationId,
      senderId: 0,
      senderName: 'me',
      contentType: 1,
      content,
      createdAt: new Date().toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' }),
      mine: true
    }
    ;(mockMessages[conversationId] ??= []).push(msg)
    return delay(msg, 120)
  }
  return http.post<unknown, Message>(`/im/conversations/${conversationId}/messages`, { content })
}
