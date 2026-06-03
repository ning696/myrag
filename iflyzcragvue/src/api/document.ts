import http from './http'
import type { Document, UploadResponse, ChunkParams, IngestProgress } from '@/types/api'

export const uploadDocument = (file: File) => {
  const formData = new FormData()
  formData.append('file', file)
  return http.post<any, { data: UploadResponse }>('/api/documents/upload', formData, {
    headers: { 'Content-Type': 'multipart/form-data' }
  })
}

export const rechunk = (id: number, params: ChunkParams) =>
  http.post<any, { data: UploadResponse }>(`/api/documents/${id}/rechunk`, params)

export const confirmIngest = (id: number) =>
  http.post<any, { data: void }>(`/api/documents/${id}/confirm-ingest`)

export const getIngestProgress = (id: number) =>
  http.get<any, { data: IngestProgress }>(`/api/documents/${id}/ingest-progress`)

export const listDocuments = (page: number, size: number) =>
  http.get<any, { data: { records: Document[], total: number } }>('/api/documents', {
    params: { page, size }
  })

export const deleteDocument = (id: number) =>
  http.delete<any, { data: void }>(`/api/documents/${id}`)
