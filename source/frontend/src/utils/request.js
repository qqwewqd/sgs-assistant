import axios from 'axios'
import { apiBaseUrl, markNodeUnhealthy } from './networkNode'

const request = axios.create({
  baseURL: import.meta.env.VITE_API_BASE || '/api',
  timeout: 30000
})

request.interceptors.request.use(async (config) => {
  config.baseURL = await apiBaseUrl()
  const raw = localStorage.getItem('sgs-user')
  if (raw) {
    const user = JSON.parse(raw)
    if (user?.token) {
      config.headers.Authorization = `Bearer ${user.token}`
    }
  }
  return config
})

request.interceptors.response.use(
  (response) => {
    const result = response.data
    if (Number(result?.code) === 200) {
      return result.data
    }
    if (Number(result?.code) === 401) {
      const error = new Error(result?.message || '暂未登录或Token已过期')
      error.authExpired = true
      error.code = 401
      localStorage.removeItem('sgs-user')
      window.location.href = '/login'
      return Promise.reject(error)
    }
    const resultError = new Error(result?.message || '请求失败')
    resultError.code = Number(result?.code)
    return Promise.reject(resultError)
  },
  (error) => {
    if (!error.response || error.code === 'ECONNABORTED') {
      markNodeUnhealthy(error.config?.baseURL?.replace(/\/api\/?$/, ''))
    }
    if (error.response?.status === 401 || Number(error.response?.data?.code) === 401) {
      const authError = new Error(error.response?.data?.message || '暂未登录或Token已过期')
      authError.authExpired = true
      authError.code = 401
      localStorage.removeItem('sgs-user')
      window.location.href = '/login'
      return Promise.reject(authError)
    }
    const message = error.code === 'ECONNABORTED' ? '网络超时，请稍后重试' : error.response?.data?.message || error.message || '网络异常'
    const requestError = new Error(message)
    requestError.code = Number(error.response?.data?.code) || error.code
    requestError.status = error.response?.status
    requestError.timeout = error.code === 'ECONNABORTED'
    return Promise.reject(requestError)
  }
)

export default request

export function isAuthExpired(error) {
  return Boolean(error?.authExpired) || Number(error?.code) === 401 || Number(error?.status) === 401
}
