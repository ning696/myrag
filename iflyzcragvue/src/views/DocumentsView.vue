<script setup lang="ts">
import { computed, ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { ChatDotRound, Delete, DocumentAdd, Files, Search, UploadFilled } from '@element-plus/icons-vue'
import * as docApi from '@/api/document'
import type { Document } from '@/types/api'

const router = useRouter()
const documents = ref<Document[]>([])
const total = ref(0)
const page = ref(1)
const size = ref(10)
const loading = ref(false)
const keyword = ref('')
const statusFilter = ref('')

const fetchDocuments = async () => {
  loading.value = true
  try {
    const res = await docApi.listDocuments(page.value, size.value)
    documents.value = res.data.records
    total.value = res.data.total
  } catch (error: any) {
    ElMessage.error('加载失败：' + error.message)
  } finally {
    loading.value = false
  }
}

const handleDelete = async (row: Document) => {
  try {
    await ElMessageBox.confirm(`确定删除“${row.filename}”吗？删除后该文档将无法用于问答。`, '删除文档', {
      confirmButtonText: '删除',
      cancelButtonText: '取消',
      type: 'warning'
    })
    await docApi.deleteDocument(row.id)
    ElMessage.success('删除成功')
    fetchDocuments()
  } catch (error: any) {
    if (error !== 'cancel' && error !== 'close') {
      ElMessage.error('删除失败：' + error.message)
    }
  }
}

const goToUpload = () => router.push('/upload')
const goToChat = () => router.push('/chat')

const filteredDocuments = computed(() => {
  const kw = keyword.value.trim().toLowerCase()
  return documents.value.filter((doc) => {
    const matchKeyword = !kw || doc.filename.toLowerCase().includes(kw) || doc.fileType.toLowerCase().includes(kw)
    const matchStatus = !statusFilter.value || doc.status === statusFilter.value
    return matchKeyword && matchStatus
  })
})

const completedCount = computed(() => documents.value.filter((doc) => doc.status === 'completed').length)
const processingCount = computed(() =>
  documents.value.filter((doc) => ['uploaded', 'processing', 'pending'].includes(doc.status)).length
)
const failedCount = computed(() => documents.value.filter((doc) => doc.status === 'failed').length)

const statusOptions = computed(() => Array.from(new Set(documents.value.map((doc) => doc.status))).filter(Boolean))

const statusText = (status: string) => {
  const map: Record<string, string> = {
    uploaded: '待确认',
    pending: '等待处理',
    processing: '处理中',
    completed: '已入库',
    failed: '失败'
  }
  return map[status] || status
}

const statusType = (status: string) => {
  if (status === 'completed') return 'success'
  if (status === 'failed') return 'danger'
  if (['uploaded', 'processing', 'pending'].includes(status)) return 'warning'
  return 'info'
}

const formatFileSize = (bytes: number) => {
  if (!bytes && bytes !== 0) return '-'
  if (bytes < 1024) return `${bytes} B`
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`
  return `${(bytes / 1024 / 1024).toFixed(1)} MB`
}

const formatDate = (value: string) => {
  if (!value) return '-'
  return new Date(value).toLocaleString('zh-CN', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit'
  })
}

onMounted(() => fetchDocuments())
</script>

<template>
  <section class="page documents-page">
    <div class="page-header">
      <div>
        <h1 class="page-title">知识库文档</h1>
        <p class="page-description">管理已上传文档、跟踪入库状态，并进入可信问答流程。</p>
      </div>
      <div class="page-actions">
        <el-button :icon="ChatDotRound" @click="goToChat">进入对话</el-button>
        <el-button type="primary" :icon="UploadFilled" @click="goToUpload">上传文档</el-button>
      </div>
    </div>

    <div class="metrics-grid">
      <div class="metric-card">
        <span>当前页文档</span>
        <strong>{{ documents.length }}</strong>
      </div>
      <div class="metric-card success">
        <span>已入库</span>
        <strong>{{ completedCount }}</strong>
      </div>
      <div class="metric-card warning">
        <span>处理中</span>
        <strong>{{ processingCount }}</strong>
      </div>
      <div class="metric-card danger">
        <span>失败</span>
        <strong>{{ failedCount }}</strong>
      </div>
    </div>

    <div class="panel table-panel">
      <div class="toolbar">
        <div class="toolbar-title">
          <el-icon><Files /></el-icon>
          <span>文档清单</span>
        </div>
        <div class="filters">
          <el-input v-model="keyword" :prefix-icon="Search" placeholder="搜索文件名或类型" clearable />
          <el-select v-model="statusFilter" placeholder="全部状态" clearable>
            <el-option v-for="status in statusOptions" :key="status" :label="statusText(status)" :value="status" />
          </el-select>
        </div>
      </div>

      <el-table v-if="documents.length || loading" :data="filteredDocuments" v-loading="loading">
        <el-table-column prop="filename" label="文件名" min-width="240" show-overflow-tooltip>
          <template #default="{ row }">
            <div class="file-cell">
              <span class="file-icon">{{ row.fileType?.slice(0, 1)?.toUpperCase() || 'D' }}</span>
              <div>
                <strong>{{ row.filename }}</strong>
                <small>{{ formatFileSize(row.fileSize) }}</small>
              </div>
            </div>
          </template>
        </el-table-column>
        <el-table-column prop="fileType" label="类型" width="110" />
        <el-table-column prop="chunkCount" label="切片数" width="110" align="right" />
        <el-table-column prop="uploadTime" label="上传时间" min-width="170">
          <template #default="{ row }">{{ formatDate(row.uploadTime) }}</template>
        </el-table-column>
        <el-table-column prop="status" label="状态" width="120">
          <template #default="{ row }">
            <el-tag :type="statusType(row.status)">{{ statusText(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="118" fixed="right">
          <template #default="{ row }">
            <el-button link type="danger" :icon="Delete" @click="handleDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>

      <div v-else class="empty-state">
        <div class="empty-icon">
          <el-icon><DocumentAdd /></el-icon>
        </div>
        <h3>还没有入库文档</h3>
        <p>上传 PDF、TXT 或 Markdown 后，可先检查切片再确认入库。</p>
        <el-button type="primary" :icon="UploadFilled" @click="goToUpload">上传第一份文档</el-button>
      </div>

      <el-pagination
        v-if="documents.length"
        v-model:current-page="page"
        v-model:page-size="size"
        :total="total"
        layout="total, prev, pager, next"
        @current-change="fetchDocuments"
      />
    </div>
  </section>
</template>

<style scoped>
.documents-page {
  display: grid;
  gap: 18px;
}

.metrics-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 12px;
}

.metric-card {
  display: grid;
  gap: 10px;
  border: 1px solid var(--border-subtle);
  border-radius: var(--radius-lg);
  background: var(--surface);
  padding: 16px;
  box-shadow: var(--shadow-sm);
}

.metric-card span {
  color: var(--text-secondary);
  font-size: 13px;
}

.metric-card strong {
  color: var(--text-primary);
  font-size: 28px;
  line-height: 1;
}

.metric-card.success strong {
  color: var(--success);
}

.metric-card.warning strong {
  color: var(--warning);
}

.metric-card.danger strong {
  color: var(--danger);
}

.table-panel {
  overflow: hidden;
}

.toolbar-title {
  display: flex;
  align-items: center;
  gap: 8px;
  color: var(--text-primary);
  font-weight: 650;
}

.filters {
  display: flex;
  width: min(100%, 520px);
  gap: 10px;
}

.filters .el-input {
  flex: 1;
}

.filters .el-select {
  width: 160px;
}

.file-cell {
  display: flex;
  min-width: 0;
  align-items: center;
  gap: 10px;
}

.file-icon {
  display: grid;
  width: 34px;
  height: 34px;
  flex: 0 0 auto;
  place-items: center;
  border-radius: var(--radius-md);
  background: var(--surface-subtle);
  color: var(--text-secondary);
  font-size: 12px;
  font-weight: 750;
}

.file-cell div {
  display: grid;
  min-width: 0;
  gap: 2px;
}

.file-cell strong {
  overflow: hidden;
  color: var(--text-primary);
  font-weight: 650;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.file-cell small {
  color: var(--text-tertiary);
}

@media (max-width: 900px) {
  .metrics-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .filters {
    width: 100%;
  }
}

@media (max-width: 560px) {
  .metrics-grid {
    grid-template-columns: 1fr;
  }

  .filters {
    flex-direction: column;
  }

  .filters .el-select {
    width: 100%;
  }
}
</style>
