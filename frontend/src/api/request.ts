import axios, { type AxiosError } from 'axios'
import { ElMessage } from 'element-plus'
import type { Result } from '@/types/models'

const http = axios.create({
  baseURL: '/api',
  timeout: 300000,
  headers: {
    'Content-Type': 'application/json',
  },
})

http.interceptors.response.use(
  (response) => {
    const body = response.data as Result<unknown>
    if (body.code !== 0) {
      ElMessage.error(body.message || '请求失败')
      return Promise.reject(new Error(body.message))
    }
    return response
  },
  (error: AxiosError<Result<unknown>>) => {
    const msg =
      error.response?.data?.message || error.message || '网络异常'
    ElMessage.error(msg)
    return Promise.reject(error)
  },
)

export async function postJson<T>(url: string, data?: unknown): Promise<T> {
  const res = await http.post<Result<T>>(url, data ?? {})
  return res.data.data
}

export default http
