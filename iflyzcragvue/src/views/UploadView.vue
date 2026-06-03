<script setup lang="ts">
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Back, CircleCheck, Document, UploadFilled } from '@element-plus/icons-vue'
import { useDocumentStore } from '@/stores/document'
import * as docApi from '@/api/document'

const router = useRouter()
const docStore = useDocumentStore()
const fileList = ref<any[]>([])
const uploading = ref(false)
const currentFileName = ref('')

const handleUpload = async (options: any) => {
  const file = options.file
  currentFileName.value = file.name
  uploading.value = true
  try {
    const res = await docApi.uploadDocument(file)
    ElMessage.success('上传成功，正在进入切片预览')
    docStore.setPreview(res.data.documentId, res.data.chunks, res.data.params)
    router.push(`/documents/${res.data.documentId}/preview`)
  } catch (error: any) {
    ElMessage.error('上传失败：' + error.message)
  } finally {
    uploading.value = false
  }
}

const beforeUpload = (file: File) => {
  const validTypes = ['application/pdf', 'text/plain', 'text/markdown']
  const validExts = ['.pdf', '.txt', '.md', '.markdown']
  const ext = file.name.substring(file.name.lastIndexOf('.')).toLowerCase()
  if (!validTypes.includes(file.type) && !validExts.includes(ext)) {
    ElMessage.error('仅支持 PDF、TXT、Markdown 文件')
    return false
  }
  if (file.size > 50 * 1024 * 1024) {
    ElMessage.error('文件大小不能超过 50MB')
    return false
  }
  return true
}
</script>

<template>
  <section class="page upload-page">
    <div class="page-header">
      <div>
        <h1 class="page-title">上传文档</h1>
        <p class="page-description">上传后会先生成切片预览，确认切片质量后再写入知识库。</p>
      </div>
      <div class="page-actions">
        <el-button :icon="Back" @click="$router.push('/documents')">返回知识库</el-button>
      </div>
    </div>

    <div class="upload-layout">
      <div class="panel upload-panel">
        <div class="upload-heading">
          <div class="upload-icon">
            <el-icon><UploadFilled /></el-icon>
          </div>
          <div>
            <h2>选择要入库的文档</h2>
            <p>支持 PDF、TXT、Markdown，单文件不超过 50MB。</p>
          </div>
        </div>

        <el-upload
          drag
          :auto-upload="true"
          :http-request="handleUpload"
          :before-upload="beforeUpload"
          :file-list="fileList"
          :limit="1"
          :disabled="uploading"
          accept=".pdf,.txt,.md,.markdown"
          class="knowledge-upload"
        >
          <el-icon class="el-icon--upload"><upload-filled /></el-icon>
          <div class="el-upload__text">拖拽文件到此处，或<em>点击选择文件</em></div>
          <template #tip>
            <div class="el-upload__tip">建议先上传结构清晰的文档，标题和段落会帮助生成更好的切片。</div>
          </template>
        </el-upload>

        <div v-if="uploading" class="uploading-state">
          <el-progress :percentage="68" :indeterminate="true" />
          <span>正在解析：{{ currentFileName }}</span>
        </div>
      </div>

      <aside class="panel guide-panel">
        <h2>入库流程</h2>
        <ol class="flow-list">
          <li>
            <el-icon><Document /></el-icon>
            <div>
              <strong>上传并解析</strong>
              <span>读取文档内容，识别类型与基础元数据。</span>
            </div>
          </li>
          <li>
            <el-icon><UploadFilled /></el-icon>
            <div>
              <strong>切片预览</strong>
              <span>检查 chunk 内容、关键词和标题，必要时重新切片。</span>
            </div>
          </li>
          <li>
            <el-icon><CircleCheck /></el-icon>
            <div>
              <strong>确认入库</strong>
              <span>向量化后写入知识库，供后续问答检索。</span>
            </div>
          </li>
        </ol>

        <div class="tips">
          <strong>质量建议</strong>
          <p>优先上传标题层级明确、段落边界清晰的文档。过长表格和扫描版 PDF 可能影响切片质量。</p>
        </div>
      </aside>
    </div>
  </section>
</template>

<style scoped>
.upload-layout {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 340px;
  gap: 18px;
}

.upload-panel,
.guide-panel {
  padding: 22px;
}

.upload-heading {
  display: flex;
  align-items: center;
  gap: 14px;
  margin-bottom: 22px;
}

.upload-icon {
  display: grid;
  width: 48px;
  height: 48px;
  place-items: center;
  border-radius: var(--radius-lg);
  background: var(--brand-soft);
  color: var(--brand);
  font-size: 22px;
}

.upload-heading h2,
.guide-panel h2 {
  color: var(--text-primary);
  font-size: 18px;
  font-weight: 700;
}

.upload-heading p {
  margin-top: 4px;
  color: var(--text-secondary);
  font-size: 14px;
  line-height: 1.6;
}

.knowledge-upload :deep(.el-upload) {
  width: 100%;
}

.knowledge-upload :deep(.el-upload-dragger) {
  width: 100%;
  min-height: 260px;
  border-color: var(--border);
  border-radius: var(--radius-lg);
  background: var(--surface-muted);
}

.knowledge-upload :deep(.el-upload-dragger:hover) {
  border-color: var(--brand);
}

.uploading-state {
  display: grid;
  gap: 8px;
  margin-top: 18px;
  color: var(--text-secondary);
  font-size: 13px;
}

.flow-list {
  display: grid;
  gap: 16px;
  margin: 18px 0 0;
  padding: 0;
  list-style: none;
}

.flow-list li {
  display: flex;
  gap: 12px;
}

.flow-list .el-icon {
  display: grid;
  width: 30px;
  height: 30px;
  flex: 0 0 auto;
  place-items: center;
  border-radius: 50%;
  background: var(--surface-subtle);
  color: var(--brand);
}

.flow-list div {
  display: grid;
  gap: 4px;
}

.flow-list strong {
  color: var(--text-primary);
  font-size: 14px;
}

.flow-list span {
  color: var(--text-secondary);
  font-size: 13px;
  line-height: 1.6;
}

.tips {
  margin-top: 24px;
  border-radius: var(--radius-lg);
  background: var(--warning-soft);
  padding: 14px;
  color: var(--warning);
}

.tips strong {
  font-size: 14px;
}

.tips p {
  margin-top: 6px;
  font-size: 13px;
  line-height: 1.7;
}

@media (max-width: 980px) {
  .upload-layout {
    grid-template-columns: 1fr;
  }
}
</style>
