import { defineStore } from 'pinia'
import { ref } from 'vue'
import type { ChunkPreview, ChunkParams } from '@/types/api'

export const useDocumentStore = defineStore('document', () => {
  const activeDocumentId = ref<number | null>(null)
  const previewChunks = ref<ChunkPreview[]>([])
  const previewParams = ref<ChunkParams>({
    size: 800,
    overlap: 80,
    strategy: 'RECURSIVE'
  })

  const setPreview = (documentId: number, chunks: ChunkPreview[], params: ChunkParams) => {
    activeDocumentId.value = documentId
    previewChunks.value = chunks
    previewParams.value = params
  }

  const clearPreview = () => {
    activeDocumentId.value = null
    previewChunks.value = []
  }

  return { activeDocumentId, previewChunks, previewParams, setPreview, clearPreview }
})
