import { createRouter, createWebHistory } from 'vue-router'
import { useAuthStore } from '@/stores/auth'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    {
      path: '/login',
      name: 'login',
      component: () => import('@/views/LoginView.vue'),
      meta: { layout: 'auth' }
    },
    {
      path: '/register',
      name: 'register',
      component: () => import('@/views/RegisterView.vue'),
      meta: { layout: 'auth' }
    },
    {
      path: '/',
      component: () => import('@/layouts/AppLayout.vue'),
      meta: { requiresAuth: true },
      redirect: '/chat',
      children: [
        { path: 'home', redirect: '/chat' },
        {
          path: 'chat',
          name: 'chat',
          component: () => import('@/views/chat/ChatListView.vue'),
          children: [
            { path: ':id', name: 'chat-room', component: () => import('@/views/chat/ChatRoomView.vue') }
          ]
        },
        { path: 'contacts', name: 'contacts', component: () => import('@/views/contacts/ContactsView.vue') },
        { path: 'ai',       name: 'ai',       component: () => import('@/views/ai/AiView.vue') },
        { path: 'profile',  name: 'profile',  component: () => import('@/views/ProfileView.vue') }
      ]
    },
    { path: '/:pathMatch(.*)*', redirect: '/chat' }
  ]
})

router.beforeEach((to) => {
  const auth = useAuthStore()
  if (to.meta.requiresAuth && !auth.token) {
    return { name: 'login', query: { redirect: to.fullPath } }
  }
  if (auth.token && (to.name === 'login' || to.name === 'register')) {
    return { path: '/chat' }
  }
})

export default router
