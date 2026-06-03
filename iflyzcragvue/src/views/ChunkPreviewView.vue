<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
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

const handleRechunk = async () => {
  rechunking.value = true
  try {
    const res = await docApi.rechunk(documentId.value, params.value)
    chunks.value = res.data.chunks
    docStore.setPreview(documentId.value, res.data.chunks, res.data.params)
    ElMessage.success('重新切块完成')
  } catch (error: any) {
    ElMessage.error('重切失败: ' + error.message)
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
    ElMessage.error('入库失败: ' + error.message)
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
        progress.value = Math.round((res.data.processed / res.data.total) * 100)
      }
    } catch (error) {
      clearInterval(timer)
    }
  }, 2000)
}

onMounted(() => {
  if (!chunks.value.length) {
    ElMessage.error('预览数据已过期')
    router.push('/documents')
  }
})
</script>

<template>
  <el-container>
    <el-header>
      <h2>切片预览</h2>
      <el-button @click="$router.push('/documents')">返回</el-button>
    </el-header>
    <el-aside width="300px">
      <el-card>
        <template #header>切块参数</template>
        <el-form label-width="80px">
          <el-form-item label="大小">
            <el-slider v-model="params.size" :min="100" :max="2000" :step="50" show-input />
          </el-form-item>
          <el-form-item label="重叠">
            <el-slider v-model="params.overlap" :min="0" :max="300" :step="10" show-input />
          </el-form-item>
          <el-form-item label="策略">
            <el-select v-model="params.strategy">
              <el-option label="递归切块" value="RECURSIVE" />
              <el-option label="按标题切块" value="BY_HEADING" />
            </el-select>
          </el-form-item>
          <el-button type="primary" :loading="rechunking" @click="handleRechunk" style="width: 100%">
            重新切分
          </el-button>
        </el-form>
      </el-card>
    </el-aside>
    <el-main>
      <el-card v-if="ingesting">
        <el-progress :percentage="progress" />
        <p style="text-align: center; margin-top: 10px">正在入库，请稍候...</p>
      </el-card>
      <template v-else>
        <el-space direction="vertical" fill style="width: 100%">
          <el-card v-for="chunk in chunks" :key="chunk.chunkIndex">
            <template #header>
              <div style="display: flex; justify-content: space-between">
                <span>块 {{ chunk.chunkIndex + 1 }} - {{ chunk.title }}</span>
                <el-tag v-for="kw in chunk.keywords" :key="kw" size="small" style="margin-left: 5px">{{ kw }}</el-tag>
              </div>
            </template>
            <div style="white-space: pre-wrap; max-height: 200px; overflow-y: auto">{{ chunk.content }}</div>
          </el-card>
        </el-space>
        <el-button type="success" size="large" @click="handleConfirmIngest" style="width: 100%; margin-top: 20px">
          确认入库
        </el-button>
      </template>
    </el-main>
  </el-container>
</template>

<style scoped>
.el-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.el-aside {
  padding: 20px;
  background: #f5f5f5;
}
.el-main {
  padding: 20px;
}
</style>
