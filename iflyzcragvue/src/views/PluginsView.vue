<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { Check, Refresh, Setting } from '@element-plus/icons-vue'
import * as pluginApi from '@/api/plugin'
import type { PluginConfig } from '@/types/api'

const plugins = ref<PluginConfig[]>([])
const loading = ref(false)
const saving = reactive<Record<string, boolean>>({})
const configDrafts = reactive<Record<string, string>>({})
const hookDrafts = reactive<Record<string, 'before' | 'after' | 'both'>>({})
const priorityDrafts = reactive<Record<string, number>>({})

const loadPlugins = async () => {
  loading.value = true
  try {
    const res = await pluginApi.listPlugins()
    plugins.value = res.data
    for (const plugin of res.data) {
      configDrafts[plugin.pluginName] = plugin.configJson || '{}'
      hookDrafts[plugin.pluginName] = plugin.hookType || 'both'
      priorityDrafts[plugin.pluginName] = plugin.priority ?? 0
    }
  } finally {
    loading.value = false
  }
}

const togglePlugin = async (plugin: PluginConfig) => {
  saving[plugin.pluginName] = true
  try {
    const res = await pluginApi.togglePlugin(plugin.pluginName, { enabled: plugin.enabled })
    Object.assign(plugin, res.data)
    ElMessage.success(plugin.enabled ? '插件已启用' : '插件已禁用')
  } catch (error) {
    plugin.enabled = !plugin.enabled
    throw error
  } finally {
    saving[plugin.pluginName] = false
  }
}

const saveConfig = async (plugin: PluginConfig) => {
  try {
    JSON.parse(configDrafts[plugin.pluginName] || '{}')
  } catch {
    ElMessage.error('配置必须是合法 JSON')
    return
  }

  saving[plugin.pluginName] = true
  try {
    const res = await pluginApi.updatePluginConfig(plugin.pluginName, {
      configJson: configDrafts[plugin.pluginName],
      hookType: hookDrafts[plugin.pluginName],
      priority: priorityDrafts[plugin.pluginName]
    })
    Object.assign(plugin, res.data)
    ElMessage.success('配置已保存')
  } finally {
    saving[plugin.pluginName] = false
  }
}

onMounted(loadPlugins)
</script>

<template>
  <section class="plugins-page">
    <header class="page-header">
      <div>
        <h1 class="page-title">插件管理</h1>
        <p class="page-description">启停 RAG 钩子插件，并维护非敏感运行参数。</p>
      </div>
      <el-button :icon="Refresh" :loading="loading" @click="loadPlugins">刷新</el-button>
    </header>

    <div class="plugin-grid">
      <article v-for="plugin in plugins" :key="plugin.pluginName" class="panel plugin-panel">
        <header class="plugin-head">
          <div class="plugin-title">
            <el-icon><Setting /></el-icon>
            <div>
              <h2>{{ plugin.pluginName }}</h2>
              <span>{{ plugin.description }}</span>
            </div>
          </div>
          <el-switch
            v-model="plugin.enabled"
            :loading="saving[plugin.pluginName]"
            active-text="启用"
            inactive-text="禁用"
            @change="togglePlugin(plugin)"
          />
        </header>

        <div class="plugin-status">
          <el-tag :type="plugin.registered ? 'success' : 'warning'" size="small">
            {{ plugin.registered ? '已注册' : '未注册' }}
          </el-tag>
          <el-tag size="small">Hook {{ plugin.hookType }}</el-tag>
          <el-tag size="small">Priority {{ plugin.priority }}</el-tag>
        </div>

        <el-form label-position="top" class="plugin-form">
          <div class="form-row">
            <el-form-item label="Hook">
              <el-select v-model="hookDrafts[plugin.pluginName]">
                <el-option label="before" value="before" />
                <el-option label="after" value="after" />
                <el-option label="both" value="both" />
              </el-select>
            </el-form-item>
            <el-form-item label="Priority">
              <el-input-number v-model="priorityDrafts[plugin.pluginName]" :min="-1000" :max="1000" />
            </el-form-item>
          </div>

          <el-form-item label="配置 JSON">
            <el-input
              v-model="configDrafts[plugin.pluginName]"
              type="textarea"
              :autosize="{ minRows: 7, maxRows: 12 }"
              spellcheck="false"
            />
          </el-form-item>

          <p v-if="plugin.pluginName === 'WebSearchPlugin'" class="config-note">
            Tavily 密钥由后端环境变量 TAVILY_API_KEY 提供；这里不要填写 apiKey、token 或 password。
          </p>

          <div class="plugin-actions">
            <el-button
              type="primary"
              :icon="Check"
              :loading="saving[plugin.pluginName]"
              @click="saveConfig(plugin)"
            >
              保存配置
            </el-button>
          </div>
        </el-form>
      </article>
    </div>

    <div v-if="!loading && !plugins.length" class="panel empty-state">
      <el-icon><Setting /></el-icon>
      <h3>暂无插件</h3>
      <p>后端未注册可管理插件。</p>
    </div>
  </section>
</template>

<style scoped>
.plugins-page {
  display: grid;
  width: min(100%, 1160px);
  gap: 18px;
  margin: 0 auto;
}

.page-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
}

.plugin-grid {
  display: grid;
  gap: 14px;
}

.plugin-panel {
  display: grid;
  gap: 16px;
  padding: 18px;
}

.plugin-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 14px;
}

.plugin-title {
  display: flex;
  min-width: 0;
  gap: 10px;
}

.plugin-title .el-icon {
  margin-top: 2px;
  color: var(--brand);
  font-size: 20px;
}

.plugin-title h2 {
  color: var(--text-primary);
  font-size: 16px;
  font-weight: 700;
}

.plugin-title span,
.config-note {
  color: var(--text-secondary);
  font-size: 13px;
  line-height: 1.6;
}

.plugin-status {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.plugin-form {
  display: grid;
  gap: 4px;
}

.form-row {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 180px;
  gap: 14px;
}

.plugin-actions {
  display: flex;
  justify-content: flex-end;
}

.empty-state {
  min-height: 260px;
}

@media (max-width: 720px) {
  .page-header,
  .plugin-head {
    align-items: stretch;
    flex-direction: column;
  }

  .form-row {
    grid-template-columns: 1fr;
  }
}
</style>
