<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { Check, Refresh, Setting, Tools } from '@element-plus/icons-vue'
import * as toolApi from '@/api/tool'
import * as skillApi from '@/api/skill'
import type { SkillConfig, ToolConfig, ToolGlobalConfig, ToolParamDefinition, ToolParamValue } from '@/types/api'

const tools = ref<ToolConfig[]>([])
const skills = ref<SkillConfig[]>([])
const globalConfig = ref<ToolGlobalConfig | null>(null)
const loading = ref(false)
const globalSaving = ref(false)
const saving = reactive<Record<string, boolean>>({})
const skillSaving = reactive<Record<string, boolean>>({})
const paramSaving = reactive<Record<string, boolean>>({})
const toolForms = reactive<Record<string, Record<string, ToolParamValue>>>({})
const globalForm = reactive<Record<string, ToolParamValue>>({})

const syncParamForm = (target: Record<string, ToolParamValue>, params: ToolParamDefinition[] = []) => {
  Object.keys(target).forEach((key) => delete target[key])
  params.forEach((param) => {
    target[param.key] = param.value
  })
}

const syncToolForms = () => {
  tools.value.forEach((tool) => {
    if (!toolForms[tool.toolName]) {
      toolForms[tool.toolName] = {}
    }
    syncParamForm(toolForms[tool.toolName], tool.params)
  })
}

const loadTools = async () => {
  loading.value = true
  try {
    const [toolRes, globalRes, skillRes] = await Promise.all([
      toolApi.listTools(),
      toolApi.getGlobalToolParams(),
      skillApi.listSkills(),
    ])
    tools.value = toolRes.data
    globalConfig.value = globalRes.data
    skills.value = skillRes.data
    syncToolForms()
    syncParamForm(globalForm, globalConfig.value.params)
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

const toggleSkill = async (skill: SkillConfig) => {
  skillSaving[skill.skillName] = true
  try {
    const res = await skillApi.toggleSkill(skill.skillName, { enabled: skill.enabled })
    Object.assign(skill, res.data)
    ElMessage.success(skill.enabled ? '技能已启用' : '技能已禁用')
  } catch {
    skill.enabled = !skill.enabled
  } finally {
    skillSaving[skill.skillName] = false
  }
}

const buildParams = (params: ToolParamDefinition[], form: Record<string, ToolParamValue>) =>
  params.reduce<Record<string, ToolParamValue>>((acc, param) => {
    acc[param.key] = form[param.key]
    return acc
  }, {})

const saveGlobalParams = async () => {
  if (!globalConfig.value) return
  globalSaving.value = true
  try {
    const res = await toolApi.updateGlobalToolParams({
      params: buildParams(globalConfig.value.params, globalForm),
    })
    globalConfig.value = res.data
    syncParamForm(globalForm, res.data.params)
    ElMessage.success('全局参数已保存')
  } catch {
    syncParamForm(globalForm, globalConfig.value.params)
  } finally {
    globalSaving.value = false
  }
}

const saveToolParams = async (tool: ToolConfig) => {
  const form = toolForms[tool.toolName]
  if (!form) return
  paramSaving[tool.toolName] = true
  try {
    const res = await toolApi.updateToolParams(tool.toolName, {
      params: buildParams(tool.params, form),
    })
    Object.assign(tool, res.data)
    syncParamForm(form, tool.params)
    ElMessage.success('工具参数已保存')
  } catch {
    syncParamForm(form, tool.params)
  } finally {
    paramSaving[tool.toolName] = false
  }
}

const formatDefault = (value: ToolParamValue) => {
  if (typeof value === 'boolean') return value ? 'true' : 'false'
  return String(value)
}

onMounted(loadTools)
</script>

<template>
  <section class="tools-page">
    <header class="page-header">
      <div>
        <h1 class="page-title">工具管理</h1>
        <p class="page-description">工具启停、全局调用限制和非敏感运行参数。</p>
      </div>
      <el-button :icon="Refresh" :loading="loading" @click="loadTools">刷新</el-button>
    </header>

    <section v-if="globalConfig" class="panel global-panel">
      <header class="section-head">
        <div class="tool-title">
          <el-icon><Setting /></el-icon>
          <div>
            <h2>全局设置</h2>
            <span>模型工具调用的统一运行参数</span>
          </div>
        </div>
        <el-button
          type="primary"
          :icon="Check"
          :loading="globalSaving"
          @click="saveGlobalParams"
        >
          保存
        </el-button>
      </header>

      <div class="param-grid">
        <label v-for="param in globalConfig.params" :key="param.key" class="param-field">
          <span class="param-label">
            {{ param.label }}
            <el-tag v-if="param.overridden" size="small" type="warning">已覆盖</el-tag>
            <el-tag v-else size="small">默认</el-tag>
          </span>
          <el-switch
            v-if="param.type === 'boolean'"
            v-model="globalForm[param.key]"
            active-text="启用"
            inactive-text="禁用"
          />
          <el-input-number
            v-else-if="param.type === 'integer' || param.type === 'number'"
            v-model="globalForm[param.key]"
            :min="param.min"
            :max="param.max"
            :step="param.type === 'number' ? 0.1 : 1"
            controls-position="right"
          />
          <el-select v-else-if="param.type === 'select'" v-model="globalForm[param.key]">
            <el-option
              v-for="option in param.options || []"
              :key="option"
              :label="option"
              :value="option"
            />
          </el-select>
          <el-input v-else v-model="globalForm[param.key]" />
          <span class="param-meta">默认值：{{ formatDefault(param.defaultValue) }}</span>
        </label>
      </div>
    </section>

    <div class="tool-grid">
      <article v-for="tool in tools" :key="tool.toolName" class="panel tool-panel">
        <header class="section-head">
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

        <div v-if="tool.params?.length" class="param-grid">
          <label v-for="param in tool.params" :key="param.key" class="param-field">
            <span class="param-label">
              {{ param.label }}
              <el-tag v-if="param.overridden" size="small" type="warning">已覆盖</el-tag>
              <el-tag v-else size="small">默认</el-tag>
            </span>
            <el-switch
              v-if="param.type === 'boolean'"
              v-model="toolForms[tool.toolName][param.key]"
              active-text="启用"
              inactive-text="禁用"
            />
            <el-input-number
              v-else-if="param.type === 'integer' || param.type === 'number'"
              v-model="toolForms[tool.toolName][param.key]"
              :min="param.min"
              :max="param.max"
              :step="param.type === 'number' ? 0.1 : 1"
              controls-position="right"
            />
            <el-select
              v-else-if="param.type === 'select'"
              v-model="toolForms[tool.toolName][param.key]"
            >
              <el-option
                v-for="option in param.options || []"
                :key="option"
                :label="option"
                :value="option"
              />
            </el-select>
            <el-input v-else v-model="toolForms[tool.toolName][param.key]" />
            <span class="param-meta">默认值：{{ formatDefault(param.defaultValue) }}</span>
          </label>
        </div>

        <div v-if="tool.params?.length" class="panel-actions">
          <el-button
            type="primary"
            :icon="Check"
            :loading="paramSaving[tool.toolName]"
            @click="saveToolParams(tool)"
          >
            保存参数
          </el-button>
        </div>
      </article>
    </div>

    <section class="skill-section">
      <header class="section-title-row">
        <div>
          <h2>技能管理</h2>
          <p>任务型多轮对话能力，启停后立即影响新的技能触发。</p>
        </div>
      </header>

      <div class="tool-grid">
        <article v-for="skill in skills" :key="skill.skillName" class="panel tool-panel">
          <header class="section-head">
            <div class="tool-title">
              <el-icon><Tools /></el-icon>
              <div>
                <h2>{{ skill.displayName || skill.skillName }}</h2>
                <span>{{ skill.description }}</span>
              </div>
            </div>
            <el-switch
              v-model="skill.enabled"
              :loading="skillSaving[skill.skillName]"
              active-text="启用"
              inactive-text="禁用"
              @change="toggleSkill(skill)"
            />
          </header>

          <div class="tool-status">
            <el-tag :type="skill.available ? 'success' : 'warning'" size="small">
              {{ skill.available ? '可用' : '不可用' }}
            </el-tag>
            <el-tag size="small">{{ skill.skillName }}</el-tag>
          </div>
        </article>
      </div>
    </section>

    <div v-if="!loading && !tools.length && !skills.length" class="panel empty-state">
      <el-icon><Tools /></el-icon>
      <h3>暂无工具或技能</h3>
      <p>后端未注册可管理能力。</p>
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

.skill-section {
  display: grid;
  gap: 12px;
}

.section-title-row h2 {
  color: var(--text-primary);
  font-size: 16px;
  font-weight: 700;
}

.section-title-row p {
  margin-top: 4px;
  color: var(--text-secondary);
  font-size: 13px;
}

.global-panel {
  display: grid;
  gap: 16px;
  padding: 18px;
}

.tool-panel {
  display: grid;
  align-content: start;
  gap: 16px;
  padding: 18px;
}

.section-head {
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

.param-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(220px, 1fr));
  gap: 14px;
}

.param-field {
  display: grid;
  min-width: 0;
  gap: 7px;
}

.param-label {
  display: flex;
  min-height: 24px;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  color: var(--text-primary);
  font-size: 13px;
  font-weight: 650;
}

.param-meta {
  color: var(--text-tertiary);
  font-size: 12px;
  line-height: 1.4;
  overflow-wrap: anywhere;
}

.panel-actions {
  display: flex;
  justify-content: flex-end;
}

@media (max-width: 640px) {
  .page-header,
  .section-head {
    align-items: stretch;
    flex-direction: column;
  }
}
</style>
