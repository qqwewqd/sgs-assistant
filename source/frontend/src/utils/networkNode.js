export async function prepareBestNode() {
  return ''
}

export async function apiBaseUrl() {
  const fixedBase = import.meta.env.VITE_API_BASE
  if (isAbsoluteHttpUrl(fixedBase)) return stripTrailingSlash(fixedBase)
  return normalizePathBase(fixedBase || '/api')
}

export function assetBaseUrl() {
  const fixedBase = import.meta.env.VITE_ASSET_BASE
  if (isAbsoluteHttpUrl(fixedBase)) return stripTrailingSlash(fixedBase)
  if (fixedBase) return `${currentOrigin()}${normalizePathBase(fixedBase)}`
  return currentOrigin()
}

export async function webSocketBaseUrl() {
  const fixedBase = import.meta.env.VITE_WS_BASE
  if (isAbsoluteWebSocketUrl(fixedBase)) return stripTrailingSlash(fixedBase)
  if (isAbsoluteHttpUrl(fixedBase)) {
    return stripTrailingSlash(fixedBase).replace(/^http:/, 'ws:').replace(/^https:/, 'wss:')
  }
  return currentOrigin().replace(/^http:/, 'ws:').replace(/^https:/, 'wss:')
}

export function markNodeUnhealthy() {
  // The LAN edition has only one local node, so there is nothing to fail over to.
}

function currentOrigin() {
  return typeof window === 'undefined' ? '' : window.location.origin
}

function normalizePathBase(value) {
  const path = String(value || '/').trim()
  const withSlash = path.startsWith('/') ? path : `/${path}`
  return stripTrailingSlash(withSlash)
}

function stripTrailingSlash(value) {
  return String(value || '').replace(/\/+$/, '')
}

function isAbsoluteHttpUrl(value) {
  return /^https?:\/\//.test(String(value || ''))
}

function isAbsoluteWebSocketUrl(value) {
  return /^wss?:\/\//.test(String(value || ''))
}
