<script setup lang="ts">
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { useDocumentStore } from '@/stores/document'
import * as docApi from '@/api/document'

const router = useRouter()
const docStore = useDocumentStore()
const fileList = ref<any[]>([])
const uploading = ref(false)

const handleUpload = async (options: any) => {
  const file = options.file
  uploading.value = true
  try {
    const res = await docApi.uploadDocument(file)
    ElMessage.success('上传成功，正在切块预览')
    docStore.setPreview(res.data.documentId, res.data.chunks, res.data.params)
    router.push(`/documents/${res.data.documentId}/preview`)
  } catch (error: any) {
    ElMessage.error('上传失败: ' + error.message)
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
  <el-container>
    <el-header>
      <h2>上传文档</h2>
      <el-button @click="$router.back()">返回</el-button>
    </el-header>
    <el-main>
      <el-upload
        drag
        :auto-upload="true"
        :http-request="handleUpload"
        :before-upload="beforeUpload"
        :file-list="fileList"
        :limit="1"
        accept=".pdf,.txt,.md,.markdown"
      >
        <el-icon class="el-icon--upload"><upload-filled /></el-icon>
        <div class="el-upload__text">拖拽文件到此处或<em>点击上传</em></div>
        <template #tip>
          <div class="el-upload__tip">支持 PDF、TXT、Markdown，大小不超过 50MB</div>
        </template>
      </el-upload>
    </el-main>
  </el-container>
</template>

<style scoped>
.el-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.el-main {
  padding: 50px;
  display: flex;
  justify-content: center;
}
</style>
