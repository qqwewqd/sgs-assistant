import axios from 'axios'
import { apiBaseUrl, markNodeUnhealthy } from './networkNode'

const request = axios.create({
  baseURL: import.meta.env.VITE_API_BASE || '/api',
  timeout: 30000
})

request.interceptors.request.use(async (config) => {
  const raw = localStorage.getItem('sgs-user')
  config.baseURL = raw ? currentOriginApiBase() : await apiBaseUrl()
  if (raw) {
    const user = JSON.parse(raw)
    if (user?.token) {
      config.headers.Authorization = `Bearer ${user.token}`
    }
  }
  return config
})

request.interceptors.response.use(
  async (response) => {
    if (response.config.responseType === 'blob') {
      const contentType = String(response.headers['content-type'] || '')
      if (contentType.includes('application/json')) {
        const result = JSON.parse(await response.data.text())
        if (Number(result?.code) === 401) {
          // A download endpoint may return a JSON error while the administrator is
          // still working in the console. Do not erase the saved session here.
          const error = new Error(result?.message || '导出失败，请重新登录后重试')
          error.code = 401
          return Promise.reject(error)
        }
        const resultError = new Error(result?.message || '请求失败')
        resultError.code = Number(result?.code)
        return Promise.reject(resultError)
      }
      return response
    }
    const result = response.data
    if (Number(result?.code) === 200) {
      return result.data
    }
    if (Number(result?.code) === 401) {
      return Promise.reject(handleAuthenticationFailure(result?.message))
    }
    const resultError = new Error(result?.message || '请求失败')
    resultError.code = Number(result?.code)
    return Promise.reject(resultError)
  },
  async (error) => {
    if (!error.response || error.code === 'ECONNABORTED') {
      markNodeUnhealthy(error.config?.baseURL?.replace(/\/api\/?$/, ''))
    }
    const payload = await readErrorPayload(error.response?.data)
    if (error.response?.status === 401 || Number(payload?.code) === 401) {
      return Promise.reject(handleAuthenticationFailure(payload?.message))
    }
    const message = error.code === 'ECONNABORTED' ? '网络超时，请稍后重试' : payload?.message || error.message || '网络异常'
    const requestError = new Error(message)
    requestError.code = Number(payload?.code) || error.code
    requestError.status = error.response?.status
    requestError.timeout = error.code === 'ECONNABORTED'
    return Promise.reject(requestError)
  }
)

export default request

export function isAuthExpired(error) {
  return Boolean(error?.authExpired) || Number(error?.code) === 401 || Number(error?.status) === 401
}

function handleAuthenticationFailure(message) {
  const error = new Error(message || '暂未登录或Token已过期')
  error.authExpired = true
  error.code = 401
  localStorage.removeItem('sgs-user')
  window.location.href = '/login'
  return error
}

function currentOriginApiBase() {
  const fixedBase = import.meta.env.VITE_API_BASE
  if (/^https?:\/\//.test(String(fixedBase || ''))) return fixedBase.replace(/\/+$/, '')
  const path = String(fixedBase || '/api').replace(/^\/?/, '/')
  return `${window.location.origin}${path}`.replace(/\/+$/, '')
}

async function readErrorPayload(data) {
  if (data instanceof Blob) {
    try {
      return JSON.parse(await data.text())
    } catch {
      return null
    }
  }
  return data && typeof data === 'object' ? data : null
}
