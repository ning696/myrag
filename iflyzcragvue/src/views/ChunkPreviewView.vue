<script setup lang="ts">
import { computed, ref, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Back, Check, Document, Refresh, Setting } from '@element-plus/icons-vue'
import { useDocumentStore } from '@/stores/document'
import * as docApi from '@/api/document'

const route = useRoute()
const router = useRouter()
const docStore = useDocumentStore()
const documentId = ref(Number(route.params.id))
const chunks = ref(docStore.previewChunks)
const params = ref(docStore.previewParams)
const rechunking = ref(false)
const ingesting = ref(false)
const progress = ref(0)

const totalChars = computed(() => chunks.value.reduce((sum, chunk) => sum + (chunk.content?.length || 0), 0))
const averageChars = computed(() => (chunks.value.length ? Math.round(totalChars.value / chunks.value.length) : 0))

const handleRechunk = async () => {
  rechunking.value = true
  try {
    const res = await docApi.rechunk(documentId.value, params.value)
    chunks.value = res.data.chunks
    docStore.setPreview(documentId.value, res.data.chunks, res.data.params)
    ElMessage.success('重新切片完成')
  } catch (error: any) {
    ElMessage.error('重切失败：' + error.message)
  } finally {
    rechunking.value = false
  }
}

const handleConfirmIngest = async () => {
  try {
    await docApi.confirmIngest(documentId.value)
    ingesting.value = true
    pollProgress()
  } catch (error: any) {
    ElMessage.error('入库失败：' + error.message)
  }
}

const pollProgress = async () => {
  const timer = setInterval(async () => {
    try {
      const res = await docApi.getIngestProgress(documentId.value)
      if (res.data.status === 'completed') {
        clearInterval(timer)
        ElMessage.success('入库完成')
        router.push('/documents')
      } else if (res.data.status === 'failed') {
        clearInterval(timer)
        ElMessage.error('入库失败')
        ingesting.value = false
      } else {
        progress.value = res.data.total ? Math.round((res.data.processed / res.data.total) * 100) : 0
      }
    } catch (error) {
      clearInterval(timer)
      ingesting.value = false
    }
  }, 2000)
}

onMounted(() => {
  if (!chunks.value.length) {
    ElMessage.error('预览数据已过期，请重新上传文档')
    router.push('/documents')
  }
})
</script>

<template>
  <section class="page preview-page">
    <div class="page-header">
      <div>
        <h1 class="page-title">切片预览</h1>
        <p class="page-description">确认 chunk 边界、标题与关键词，保证后续检索和引用可追溯。</p>
      </div>
      <div class="page-actions">
        <el-button :icon="Back" @click="$router.push('/documents')">返回知识库</el-button>
      </div>
    </div>

    <div class="preview-layout">
      <aside class="panel params-panel">
        <div class="panel-title">
          <el-icon><Setting /></el-icon>
          <span>切片参数</span>
        </div>

        <el-form label-position="top" class="params-form">
          <el-form-item label="切片大小">
            <el-slider v-model="params.size" :min="100" :max="2000" :step="50" show-input />
          </el-form-item>
          <el-form-item label="重叠长度">
            <el-slider v-model="params.overlap" :min="0" :max="300" :step="10" show-input />
          </el-form-item>
          <el-form-item label="切片策略">
            <el-select v-model="params.strategy">
              <el-option label="递归切片" value="RECURSIVE" />
              <el-option label="按标题切片" value="BY_HEADING" />
            </el-select>
          </el-form-item>
          <el-button type="primary" :icon="Refresh" :loading="rechunking" class="full-button" @click="handleRechunk">
            重新切片
          </el-button>
        </el-form>

        <div class="param-note">
          <strong>建议范围</strong>
          <p>PDF 报告可从 500/80 起步，Markdown 可使用 600/100；重叠保留上下文但会增加索引成本。</p>
        </div>
      </aside>

      <main class="chunks-panel">
        <div class="stats-row">
          <div class="stat-item">
            <span>Chunk 数</span>
            <strong>{{ chunks.length }}</strong>
          </div>
          <div class="stat-item">
            <span>平均字符</span>
            <strong>{{ averageChars }}</strong>
          </div>
          <div class="stat-item">
            <span>切片策略</span>
            <strong>{{ params.strategy }}</strong>
          </div>
        </div>

        <div v-if="ingesting" class="panel ingest-panel">
          <el-progress :percentage="progress" />
          <h2>正在写入知识库</h2>
          <p>系统正在生成向量并写入检索索引，请稍候。</p>
        </div>

        <template v-else>
          <div class="chunk-list">
            <article v-for="chunk in chunks" :key="chunk.chunkIndex" class="panel chunk-card">
              <header>
                <div class="chunk-title">
                  <span class="chunk-index">#{{ chunk.chunkIndex + 1 }}</span>
                  <strong>{{ chunk.title || '未命名片段' }}</strong>
                </div>
                <div class="keyword-list">
                  <el-tag v-for="kw in chunk.keywords || []" :key="kw" size="small">{{ kw }}</el-tag>
                </div>
              </header>
              <div class="chunk-content">{{ chunk.content }}</div>
              <footer>
                <span>
                  <el-icon><Document /></el-icon>
                  {{ chunk.content?.length || 0 }} 字符
                </span>
              </footer>
            </article>
          </div>

          <div class="confirm-bar">
            <div>
              <strong>确认切片后即可入库</strong>
              <span>入库完成后，该文档会参与知识库检索和问答引用。</span>
            </div>
            <el-button type="primary" size="large" :icon="Check" @click="handleConfirmIngest">确认入库</el-button>
          </div>
        </template>
      </main>
    </div>
  </section>
</template>

<style scoped>
.preview-layout {
  display: grid;
  grid-template-columns: 320px minmax(0, 1fr);
  gap: 18px;
  align-items: start;
}

.params-panel {
  position: sticky;
  top: calc(var(--topbar-height) + 24px);
  padding: 18px;
}

.panel-title {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 18px;
  color: var(--text-primary);
  font-weight: 700;
}

.params-form {
  display: grid;
  gap: 4px;
}

.full-button {
  width: 100%;
}

.param-note {
  margin-top: 18px;
  border-radius: var(--radius-lg);
  background: var(--brand-soft);
  padding: 14px;
  color: #1d4ed8;
}

.param-note strong {
  font-size: 14px;
}

.param-note p {
  margin-top: 6px;
  font-size: 13px;
  line-height: 1.7;
}

.chunks-panel {
  display: grid;
  min-width: 0;
  gap: 14px;
}

.stats-row {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 12px;
}

.stat-item {
  display: grid;
  gap: 8px;
  border: 1px solid var(--border-subtle);
  border-radius: var(--radius-lg);
  background: var(--surface);
  padding: 14px;
}

.stat-item span {
  color: var(--text-secondary);
  font-size: 13px;
}

.stat-item strong {
  overflow: hidden;
  color: var(--text-primary);
  font-size: 22px;
  line-height: 1;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.ingest-panel {
  display: grid;
  gap: 12px;
  padding: 28px;
  text-align: center;
}

.ingest-panel h2 {
  color: var(--text-primary);
  font-size: 18px;
}

.ingest-panel p {
  color: var(--text-secondary);
}

.chunk-list {
  display: grid;
  gap: 12px;
}

.chunk-card {
  overflow: hidden;
}

.chunk-card header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 14px;
  border-bottom: 1px solid var(--border-subtle);
  padding: 14px 16px;
}

.chunk-title {
  display: flex;
  min-width: 0;
  align-items: center;
  gap: 10px;
}

.chunk-title strong {
  overflow: hidden;
  color: var(--text-primary);
  font-size: 15px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.chunk-index {
  flex: 0 0 auto;
  color: var(--brand);
  font-weight: 750;
}

.keyword-list {
  display: flex;
  justify-content: flex-end;
  gap: 6px;
  flex-wrap: wrap;
}

.chunk-content {
  max-height: 240px;
  overflow: auto;
  padding: 16px;
  color: #1f2937;
  font-size: 14px;
  line-height: 1.8;
  white-space: pre-wrap;
  word-break: break-word;
}

.chunk-card footer {
  display: flex;
  justify-content: flex-end;
  border-top: 1px solid var(--border-subtle);
  padding: 10px 16px;
  color: var(--text-tertiary);
  font-size: 12px;
}

.chunk-card footer span {
  display: inline-flex;
  align-items: center;
  gap: 4px;
}

.confirm-bar {
  position: sticky;
  bottom: 16px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 18px;
  border: 1px solid var(--border);
  border-radius: var(--radius-lg);
  background: rgba(255, 255, 255, 0.94);
  padding: 14px 16px;
  box-shadow: var(--shadow-md);
  backdrop-filter: blur(12px);
}

.confirm-bar div {
  display: grid;
  gap: 4px;
}

.confirm-bar strong {
  color: var(--text-primary);
}

.confirm-bar span {
  color: var(--text-secondary);
  font-size: 13px;
}

@media (max-width: 1040px) {
  .preview-layout {
    grid-template-columns: 1fr;
  }

  .params-panel {
    position: static;
  }
}

@media (max-width: 640px) {
  .stats-row {
    grid-template-columns: 1fr;
  }

  .chunk-card header,
  .confirm-bar {
    align-items: stretch;
    flex-direction: column;
  }

  .keyword-list {
    justify-content: flex-start;
  }
}
</style>
