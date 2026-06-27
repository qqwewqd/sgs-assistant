import { assetBaseUrl } from './networkNode'

export function assetUrl(path) {
  if (!path) return ''
  if (/^https?:\/\//.test(path)) return path
  const base = assetBaseUrl()
  return `${base}${path.startsWith('/') ? path : `/${path}`}`
}
