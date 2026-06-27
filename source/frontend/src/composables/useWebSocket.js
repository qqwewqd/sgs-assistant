import { onBeforeUnmount } from 'vue'
import { useUserStore } from '../store/user'
import { markNodeUnhealthy, webSocketBaseUrl } from '../utils/networkNode'

export function createRoomSocket(roomCode, token, onRoomUpdated) {
  let socket = null
  let pingTimer = null
  let reconnectTimer = null
  let closed = false

  async function connect() {
    if (!roomCode || !token || closed) return
    const base = await webSocketBaseUrl()
    if (closed) return
    socket = new WebSocket(`${base}/ws/game?roomCode=${encodeURIComponent(roomCode)}&token=${encodeURIComponent(token)}`)
    socket.onmessage = (event) => {
      const payload = JSON.parse(event.data)
      if (payload.type === 'ROOM_UPDATED') {
        onRoomUpdated?.()
      }
    }
    socket.onopen = () => {
      pingTimer = window.setInterval(() => {
        if (socket?.readyState === WebSocket.OPEN) socket.send('PING')
      }, 25000)
    }
    socket.onclose = () => {
      window.clearInterval(pingTimer)
      if (!closed) {
        markNodeUnhealthy(base.replace(/^ws:/, 'http:').replace(/^wss:/, 'https:'))
        reconnectTimer = window.setTimeout(connect, 1800)
      }
    }
  }

  function close() {
    closed = true
    window.clearInterval(pingTimer)
    window.clearTimeout(reconnectTimer)
    socket?.close()
  }

  connect()
  return { close }
}

export function useRoomSocket(roomCode, onRoomUpdated) {
  const user = useUserStore()
  const connection = createRoomSocket(roomCode, user.token, onRoomUpdated)
  onBeforeUnmount(connection.close)
  return connection
}
