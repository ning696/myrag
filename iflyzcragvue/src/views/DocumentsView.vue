<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import * as docApi from '@/api/document'
import type { Document } from '@/types/api'

const router = useRouter()
const documents = ref<Document[]>([])
const total = ref(0)
const page = ref(1)
const size = ref(10)
const loading = ref(false)

const fetchDocuments = async () => {
  loading.value = true
  try {
    const res = await docApi.listDocuments(page.value, size.value)
    documents.value = res.data.records
    total.value = res.data.total
  } catch (error: any) {
    ElMessage.error('加载失败: ' + error.message)
  } finally {
    loading.value = false
  }
}

const handleDelete = async (id: number) => {
  try {
    await docApi.deleteDocument(id)
    ElMessage.success('删除成功')
    fetchDocuments()
  } catch (error: any) {
    ElMessage.error('删除失败: ' + error.message)
  }
}

const goToUpload = () => router.push('/upload')
const goToChat = () => router.push('/chat')

onMounted(() => fetchDocuments())
</script>

<template>
  <el-container>
    <el-header>
      <h2>文档列表</h2>
      <div>
        <el-button type="primary" @click="goToUpload">上传文档</el-button>
        <el-button @click="goToChat">对话</el-button>
      </div>
    </el-header>
    <el-main>
      <el-table :data="documents" v-loading="loading" stripe>
        <el-table-column prop="filename" label="文件名" />
        <el-table-column prop="fileType" label="类型" width="80" />
        <el-table-column prop="chunkCount" label="切片数" width="100" />
        <el-table-column prop="status" label="状态" width="120">
          <template #default="{ row }">
            <el-tag :type="row.status === 'completed' ? 'success' : row.status === 'failed' ? 'danger' : ''">
              {{ row.status }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="150">
          <template #default="{ row }">
            <el-button link type="danger" @click="handleDelete(row.id)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-pagination
        v-model:current-page="page"
        v-model:page-size="size"
        :total="total"
        layout="total, prev, pager, next"
        @current-change="fetchDocuments"
      />
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
  padding: 20px;
}
</style>
