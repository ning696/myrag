<script setup lang="ts">
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Lock, User, Connection, DataLine, Files } from '@element-plus/icons-vue'
import { useUserStore } from '@/stores/user'

const router = useRouter()
const userStore = useUserStore()

const form = ref({
  username: '',
  password: '',
  rememberMe: false
})
const loading = ref(false)

const handleLogin = async () => {
  if (!form.value.username || !form.value.password) {
    ElMessage.error('请输入用户名和密码')
    return
  }
  loading.value = true
  try {
    await userStore.login(form.value.username, form.value.password, form.value.rememberMe)
    ElMessage.success('登录成功')
    router.push('/documents')
  } catch (error: any) {
    ElMessage.error(error.message || '登录失败')
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <main class="login-page">
    <section class="login-hero" aria-label="产品介绍">
      <div class="brand-row">
        <div class="brand-mark">R</div>
        <div>
          <strong>myRAG</strong>
          <span>企业知识库工作台</span>
        </div>
      </div>

      <div class="hero-copy">
        <p class="eyebrow">Knowledge Intelligence Workspace</p>
        <h1>把企业文档转化为可追溯的 AI 问答能力</h1>
        <p>
          统一管理文档入库、切片审核和智能对话，让每一次回答都能回到可信来源。
        </p>
      </div>

      <div class="feature-grid">
        <div class="feature-item">
          <el-icon><Files /></el-icon>
          <span>文档知识库</span>
        </div>
        <div class="feature-item">
          <el-icon><DataLine /></el-icon>
          <span>切片可审计</span>
        </div>
        <div class="feature-item">
          <el-icon><Connection /></el-icon>
          <span>答案可溯源</span>
        </div>
      </div>
    </section>

    <section class="login-panel" aria-label="登录">
      <div class="panel-head">
        <h2>登录工作台</h2>
        <p>使用你的账号进入知识库管理与问答空间。</p>
      </div>

      <el-form :model="form" label-position="top" class="login-form" @submit.prevent>
        <el-form-item label="用户名">
          <el-input v-model="form.username" placeholder="请输入用户名" size="large" clearable>
            <template #prefix>
              <el-icon><User /></el-icon>
            </template>
          </el-input>
        </el-form-item>

        <el-form-item label="密码">
          <el-input
            v-model="form.password"
            type="password"
            placeholder="请输入密码"
            size="large"
            show-password
            @keyup.enter="handleLogin"
          >
            <template #prefix>
              <el-icon><Lock /></el-icon>
            </template>
          </el-input>
        </el-form-item>

        <div class="form-row">
          <el-checkbox v-model="form.rememberMe">记住登录状态</el-checkbox>
          <span>安全会话由 JWT 保护</span>
        </div>

        <el-button type="primary" size="large" :loading="loading" class="login-button" @click="handleLogin">
          登录
        </el-button>
      </el-form>

      <p class="login-note">请使用管理员或用户账号登录。开发环境可按项目初始化账号配置访问。</p>
    </section>
  </main>
</template>

<style scoped>
.login-page {
  display: grid;
  min-height: 100vh;
  grid-template-columns: minmax(0, 1.05fr) minmax(360px, 480px);
  background:
    radial-gradient(circle at 18% 18%, rgba(31, 94, 255, 0.08), transparent 28%),
    linear-gradient(135deg, #f8fafc 0%, #eef2f7 100%);
}

.login-hero {
  display: flex;
  min-width: 0;
  flex-direction: column;
  justify-content: space-between;
  padding: 56px clamp(28px, 6vw, 88px);
}

.brand-row {
  display: flex;
  align-items: center;
  gap: 12px;
}

.brand-mark {
  display: grid;
  width: 42px;
  height: 42px;
  place-items: center;
  border-radius: var(--radius-lg);
  background: #111827;
  color: #fff;
  font-weight: 760;
}

.brand-row strong,
.brand-row span {
  display: block;
}

.brand-row strong {
  font-size: 18px;
  line-height: 1.2;
}

.brand-row span {
  color: var(--text-secondary);
  font-size: 13px;
}

.hero-copy {
  max-width: 660px;
  padding: 72px 0;
}

.eyebrow {
  margin-bottom: 16px;
  color: var(--brand);
  font-size: 13px;
  font-weight: 700;
  letter-spacing: 0.08em;
  text-transform: uppercase;
}

.hero-copy h1 {
  max-width: 620px;
  color: #101828;
  font-size: clamp(34px, 4.6vw, 58px);
  font-weight: 720;
  line-height: 1.08;
  letter-spacing: 0;
}

.hero-copy p:last-child {
  max-width: 560px;
  margin-top: 22px;
  color: var(--text-secondary);
  font-size: 17px;
  line-height: 1.8;
}

.feature-grid {
  display: grid;
  max-width: 660px;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 12px;
}

.feature-item {
  display: flex;
  align-items: center;
  gap: 10px;
  border: 1px solid rgba(223, 229, 238, 0.9);
  border-radius: var(--radius-lg);
  background: rgba(255, 255, 255, 0.72);
  padding: 14px;
  color: var(--text-secondary);
  font-size: 14px;
  box-shadow: var(--shadow-sm);
}

.feature-item .el-icon {
  color: var(--brand);
  font-size: 18px;
}

.login-panel {
  align-self: center;
  width: min(100% - 40px, 420px);
  justify-self: center;
  border: 1px solid var(--border-subtle);
  border-radius: var(--radius-lg);
  background: rgba(255, 255, 255, 0.92);
  padding: 32px;
  box-shadow: var(--shadow-md);
  backdrop-filter: blur(16px);
}

.panel-head h2 {
  color: var(--text-primary);
  font-size: 24px;
  font-weight: 700;
}

.panel-head p {
  margin-top: 8px;
  color: var(--text-secondary);
  font-size: 14px;
  line-height: 1.6;
}

.login-form {
  margin-top: 28px;
}

.form-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin: 2px 0 22px;
  color: var(--text-tertiary);
  font-size: 13px;
}

.login-button {
  width: 100%;
}

.login-note {
  margin-top: 18px;
  color: var(--text-tertiary);
  font-size: 12px;
  line-height: 1.7;
}

@media (max-width: 900px) {
  .login-page {
    grid-template-columns: 1fr;
  }

  .login-hero {
    padding: 32px 22px 12px;
  }

  .hero-copy {
    padding: 42px 0 28px;
  }

  .login-panel {
    align-self: start;
    margin: 0 auto 28px;
  }
}

@media (max-width: 560px) {
  .feature-grid {
    grid-template-columns: 1fr;
  }

  .login-panel {
    width: calc(100% - 28px);
    padding: 24px;
  }

  .form-row {
    align-items: flex-start;
    flex-direction: column;
    gap: 6px;
  }
}
</style>
