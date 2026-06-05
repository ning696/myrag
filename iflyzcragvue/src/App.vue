<script setup lang="ts">
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useUserStore } from '@/stores/user'
import { ChatDotRound, Files, Setting, UploadFilled } from '@element-plus/icons-vue'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

const isAuthPage = computed(() => route.path === '/login')

const navItems = computed(() => {
  const items = [
    { path: '/documents', label: '知识库', description: '文档与入库状态', icon: Files },
    { path: '/upload', label: '上传入库', description: '解析与切片预览', icon: UploadFilled },
    { path: '/chat', label: '智能问答', description: '基于知识库回答', icon: ChatDotRound }
  ]
  if (userStore.user?.role === 'ADMIN') {
    items.push({ path: '/tools', label: '工具管理', description: '启停实时工具', icon: Setting })
  }
  return items
})

const activePath = computed(() => {
  if (route.path.startsWith('/documents')) return '/documents'
  if (route.path.startsWith('/upload')) return '/upload'
  if (route.path.startsWith('/chat')) return '/chat'
  if (route.path.startsWith('/tools')) return '/tools'
  return route.path
})

const userName = computed(() => userStore.user?.nickname || userStore.user?.username || '已登录用户')

const go = (path: string) => {
  if (route.path !== path) {
    router.push(path)
  }
}

const logout = () => {
  userStore.logout()
  router.push('/login')
}
</script>

<template>
  <router-view v-if="isAuthPage" />

  <div v-else class="app-shell">
    <aside class="app-sidebar">
      <div class="brand">
        <div class="brand-mark">R</div>
        <div>
          <div class="brand-name">myRAG</div>
          <div class="brand-subtitle">企业知识库工作台</div>
        </div>
      </div>

      <nav class="side-nav" aria-label="主导航">
        <button
          v-for="item in navItems"
          :key="item.path"
          type="button"
          :class="['nav-item', { active: activePath === item.path }]"
          @click="go(item.path)"
        >
          <el-icon><component :is="item.icon" /></el-icon>
          <span>
            <strong>{{ item.label }}</strong>
            <small>{{ item.description }}</small>
          </span>
        </button>
      </nav>
    </aside>

    <section class="app-main">
      <header class="topbar">
        <div>
          <strong>可信知识问答</strong>
          <span>文档入库、切片审核与 RAG 对话</span>
        </div>
        <div class="user-area">
          <div class="avatar">{{ userName.slice(0, 1).toUpperCase() }}</div>
          <div class="user-meta">
            <strong>{{ userName }}</strong>
            <span>{{ userStore.user?.role || 'USER' }}</span>
          </div>
          <el-button text @click="logout">退出</el-button>
        </div>
      </header>

      <main class="workspace">
        <router-view />
      </main>
    </section>
  </div>
</template>

<style scoped>
.app-shell {
  display: flex;
  min-height: 100vh;
  background: var(--app-bg);
}

.app-sidebar {
  position: sticky;
  top: 0;
  display: flex;
  width: var(--sidebar-width);
  height: 100vh;
  flex: 0 0 var(--sidebar-width);
  flex-direction: column;
  gap: 22px;
  border-right: 1px solid var(--border);
  background: rgba(255, 255, 255, 0.92);
  padding: 18px 14px;
  backdrop-filter: blur(12px);
}

.brand {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 8px 8px 14px;
}

.brand-mark {
  display: grid;
  width: 38px;
  height: 38px;
  place-items: center;
  border-radius: var(--radius-lg);
  background: #111827;
  color: #fff;
  font-weight: 750;
}

.brand-name {
  color: var(--text-primary);
  font-size: 17px;
  font-weight: 750;
  line-height: 1.2;
}

.brand-subtitle {
  margin-top: 2px;
  color: var(--text-tertiary);
  font-size: 12px;
}

.side-nav {
  display: grid;
  gap: 6px;
}

.nav-item {
  display: flex;
  width: 100%;
  align-items: center;
  gap: 12px;
  border: 0;
  border-radius: var(--radius-lg);
  background: transparent;
  color: var(--text-secondary);
  cursor: pointer;
  padding: 12px;
  text-align: left;
  transition:
    background 0.18s ease,
    color 0.18s ease;
}

.nav-item .el-icon {
  flex: 0 0 auto;
  font-size: 18px;
}

.nav-item span {
  display: grid;
  min-width: 0;
  gap: 2px;
}

.nav-item strong {
  color: inherit;
  font-size: 14px;
  font-weight: 650;
  line-height: 1.25;
}

.nav-item small {
  overflow: hidden;
  color: var(--text-tertiary);
  font-size: 12px;
  line-height: 1.35;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.nav-item:hover,
.nav-item.active {
  background: var(--brand-soft);
  color: var(--brand);
}

.app-main {
  display: flex;
  min-width: 0;
  flex: 1;
  flex-direction: column;
}

.topbar {
  position: sticky;
  z-index: 5;
  top: 0;
  display: flex;
  min-height: var(--topbar-height);
  align-items: center;
  justify-content: space-between;
  gap: 18px;
  border-bottom: 1px solid var(--border);
  background: rgba(255, 255, 255, 0.88);
  padding: 0 24px;
  backdrop-filter: blur(12px);
}

.topbar > div:first-child {
  display: grid;
  min-width: 0;
  gap: 2px;
}

.topbar strong {
  color: var(--text-primary);
  font-size: 14px;
}

.topbar span {
  color: var(--text-tertiary);
  font-size: 12px;
}

.user-area {
  display: flex;
  align-items: center;
  gap: 10px;
}

.avatar {
  display: grid;
  width: 32px;
  height: 32px;
  place-items: center;
  border-radius: 50%;
  background: #172033;
  color: #fff;
  font-size: 13px;
  font-weight: 700;
}

.user-meta {
  display: grid;
  min-width: 0;
}

.user-meta strong,
.user-meta span {
  max-width: 130px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.workspace {
  min-width: 0;
  flex: 1;
  padding: 24px;
}

@media (max-width: 860px) {
  .app-shell {
    flex-direction: column;
  }

  .app-sidebar {
    position: static;
    width: 100%;
    height: auto;
    flex-basis: auto;
    border-right: 0;
    border-bottom: 1px solid var(--border);
    padding: 12px;
  }

  .brand {
    padding: 4px 4px 8px;
  }

  .side-nav {
    display: flex;
    gap: 8px;
    overflow-x: auto;
    padding-bottom: 2px;
  }

  .nav-item {
    min-width: 150px;
  }

  .topbar {
    min-height: auto;
    align-items: flex-start;
    padding: 12px 16px;
  }

  .topbar > div:first-child span,
  .user-meta {
    display: none;
  }

  .workspace {
    padding: 16px;
  }
}

@media (max-width: 520px) {
  .nav-item {
    min-width: 124px;
    padding: 10px;
  }

  .nav-item small {
    display: none;
  }
}
</style>
