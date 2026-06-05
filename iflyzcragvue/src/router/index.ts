import { createRouter, createWebHistory } from 'vue-router'
import { useUserStore } from '@/stores/user'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    {
      path: '/login',
      name: 'Login',
      component: () => import('@/views/LoginView.vue'),
      meta: { requiresAuth: false }
    },
    {
      path: '/documents',
      name: 'Documents',
      component: () => import('@/views/DocumentsView.vue'),
      meta: { requiresAuth: true }
    },
    {
      path: '/documents/:id/preview',
      name: 'ChunkPreview',
      component: () => import('@/views/ChunkPreviewView.vue'),
      meta: { requiresAuth: true }
    },
    {
      path: '/upload',
      name: 'Upload',
      component: () => import('@/views/UploadView.vue'),
      meta: { requiresAuth: true }
    },
    {
      path: '/chat',
      name: 'Chat',
      component: () => import('@/views/ChatView.vue'),
      meta: { requiresAuth: true }
    },
    {
      path: '/tools',
      name: 'Tools',
      component: () => import('@/views/ToolsView.vue'),
      meta: { requiresAuth: true, requiresAdmin: true }
    },
    {
      path: '/',
      redirect: '/documents'
    }
  ]
})

router.beforeEach(async (to, from, next) => {
  const userStore = useUserStore()
  if (to.meta.requiresAuth && !userStore.token) {
    next('/login')
    return
  }
  if (userStore.token && !userStore.user) {
    try {
      await userStore.fetchMe()
    } catch {
      next('/login')
      return
    }
  }
  if (to.meta.requiresAdmin && userStore.user?.role !== 'ADMIN') {
    next('/documents')
  } else if (to.path === '/login' && userStore.token) {
    next('/documents')
  } else {
    next()
  }
})

export default router
