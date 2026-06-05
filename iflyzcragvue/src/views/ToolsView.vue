<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { Refresh, Tools } from '@element-plus/icons-vue'
import * as toolApi from '@/api/tool'
import type { ToolConfig } from '@/types/api'

const tools = ref<ToolConfig[]>([])
const loading = ref(false)
const saving = reactive<Record<string, boolean>>({})

const loadTools = async () => {
  loading.value = true
  try {
    const res = await toolApi.listTools()
    tools.value = res.data
  } finally {
    loading.value = false
  }
}

const toggleTool = async (tool: ToolConfig) => {
  saving[tool.toolName] = true
  try {
    const res = await toolApi.toggleTool(tool.toolName, { enabled: tool.enabled })
    Object.assign(tool, res.data)
    ElMessage.success(tool.enabled ? '工具已启用' : '工具已禁用')
  } catch {
    tool.enabled = !tool.enabled
  } finally {
    saving[tool.toolName] = false
  }
}

onMounted(loadTools)
</script>

<template>
  <section class="tools-page">
    <header class="page-header">
      <div>
        <h1 class="page-title">工具管理</h1>
        <p class="page-description">启停模型可调用的实时工具，参数由后端配置文件维护。</p>
      </div>
      <el-button :icon="Refresh" :loading="loading" @click="loadTools">刷新</el-button>
    </header>

    <div class="tool-grid">
      <article v-for="tool in tools" :key="tool.toolName" class="panel tool-panel">
        <header class="tool-head">
          <div class="tool-title">
            <el-icon><Tools /></el-icon>
            <div>
              <h2>{{ tool.displayName || tool.toolName }}</h2>
              <span>{{ tool.description }}</span>
            </div>
          </div>
          <el-switch
            v-model="tool.enabled"
            :loading="saving[tool.toolName]"
            active-text="启用"
            inactive-text="禁用"
            @change="toggleTool(tool)"
          />
        </header>

        <div class="tool-status">
          <el-tag :type="tool.available ? 'success' : 'warning'" size="small">
            {{ tool.available ? '可用' : '不可用' }}
          </el-tag>
          <el-tag size="small">{{ tool.toolName }}</el-tag>
        </div>
      </article>
    </div>

    <div v-if="!loading && !tools.length" class="panel empty-state">
      <el-icon><Tools /></el-icon>
      <h3>暂无工具</h3>
      <p>后端未注册可管理工具。</p>
    </div>
  </section>
</template>

<style scoped>
.tools-page {
  display: grid;
  gap: 18px;
}

.page-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
}

.tool-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(300px, 1fr));
  gap: 14px;
}

.tool-panel {
  display: grid;
  gap: 14px;
  padding: 18px;
}

.tool-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 14px;
}

.tool-title {
  display: flex;
  min-width: 0;
  align-items: flex-start;
  gap: 12px;
}

.tool-title .el-icon {
  margin-top: 2px;
  color: var(--brand);
  font-size: 20px;
}

.tool-title h2 {
  color: var(--text-primary);
  font-size: 16px;
  font-weight: 700;
  line-height: 1.3;
}

.tool-title span {
  display: block;
  margin-top: 4px;
  color: var(--text-secondary);
  font-size: 13px;
  line-height: 1.5;
}

.tool-status {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

@media (max-width: 640px) {
  .page-header,
  .tool-head {
    align-items: stretch;
    flex-direction: column;
  }
}
</style>
