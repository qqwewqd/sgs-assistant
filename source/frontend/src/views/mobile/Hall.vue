<template>
  <main class="page">
    <div class="mobile-shell">
      <header class="hall-head">
        <div>
          <p class="muted">欢迎，{{ user.username }}</p>
          <h1>线下面杀房间</h1>
        </div>
        <button class="warm-button secondary small" @click="logout">退出</button>
      </header>

      <section v-if="!game.room" class="panel action-panel">
        <label class="mode-picker">
          <span>身份模式</span>
          <select v-model.number="selectedModeId" class="field">
            <option v-for="mode in identityModes" :key="mode.id" :value="mode.id">
              {{ mode.name }}
            </option>
          </select>
        </label>
        <button class="big-action" :disabled="!selectedModeId" @click="create">创建新房间</button>
        <div class="join-box">
          <input v-model.trim="joinCode" class="field code-field" inputmode="numeric" maxlength="5" placeholder="房间号" />
          <button class="warm-button" @click="join">加入已有房间</button>
        </div>
      </section>

      <section v-else class="panel room-panel">
        <div class="room-code">
          <span>房间号</span>
          <strong>{{ game.room.roomCode }}</strong>
        </div>
        <div class="room-mode">
          <span>身份模式</span>
          <strong>{{ game.room.identityModeName || '标准身份局' }}</strong>
        </div>
        <div class="toolbar room-actions">
          <button class="warm-button" :disabled="startDisabled" @click="start">开始发牌</button>
          <button class="warm-button secondary" @click="leave">离开</button>
        </div>
        <p v-if="startHint" class="start-hint">{{ startHint }}</p>
      </section>

      <section v-if="game.room" class="players">
        <h2 class="section-title">玩家</h2>
        <div v-for="player in game.room.players" :key="player.userId" class="player-row panel">
          <div>
            <strong>{{ player.username }}</strong>
            <span v-if="player.owner" class="status-pill">房主</span>
          </div>
          <span :class="['status-pill', player.online ? 'green' : 'red']">
            {{ player.online ? '在线' : '离线' }}
          </span>
        </div>
      </section>
    </div>
  </main>
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { showConfirmDialog, showToast } from 'vant'
import { createRoom, getRoom, joinRoom, leaveRoom, listIdentityModes, startGame } from '../../api/room'
import { createRoomSocket } from '../../composables/useWebSocket'
import { useGameStore } from '../../store/game'
import { useUserStore } from '../../store/user'

const router = useRouter()
const user = useUserStore()
const game = useGameStore()
const joinCode = ref('')
const identityModes = ref([])
const selectedModeId = ref(null)
let socket = null
const roomMode = computed(() => identityModes.value.find((mode) => mode.id === game.room?.identityModeId))
const roomModeSupportsCount = computed(() => {
  if (!game.room?.identityModeId || !roomMode.value) return true
  return roomMode.value.playerCounts.includes(game.room.players.length)
})
const startDisabled = computed(() => !game.room?.canStart || !roomModeSupportsCount.value)
const startHint = computed(() => {
  if (!game.room || !game.room.owner) return ''
  if (game.room.players.length < 2) return '等待至少 2 名玩家加入'
  if (game.room.players.some((player) => !player.online)) return '有玩家离线，暂不能发牌'
  if (!roomModeSupportsCount.value) return '当前身份模式不支持这个人数'
  return ''
})

onMounted(async () => {
  await loadIdentityModes()
  if (!game.currentRoomCode) return
  try {
    const room = await getRoom(game.currentRoomCode)
    game.setRoom(room)
    routeByStatus(room, true)
  } catch {
    game.clearRoom()
  }
})

watch(
  () => game.currentRoomCode,
  (roomCode) => {
    socket?.close()
    socket = null
    if (roomCode) {
      socket = createRoomSocket(roomCode, user.token, loadRoom)
    }
  },
  { immediate: true }
)

onBeforeUnmount(() => socket?.close())

async function create() {
  if (!selectedModeId.value) {
    showToast('请选择身份模式')
    return
  }
  try {
    const room = await createRoom(selectedModeId.value)
    game.setRoom(room)
    showToast(`房间 ${room.roomCode} 已创建`)
  } catch (error) {
    showToast(error.message)
  }
}

async function loadIdentityModes() {
  try {
    identityModes.value = await listIdentityModes()
    if (!selectedModeId.value) {
      selectedModeId.value = identityModes.value[0]?.id || null
    }
  } catch (error) {
    showToast(error.message)
  }
}

async function join() {
  if (!/^\d{4,5}$/.test(joinCode.value)) {
    showToast('请输入 4 或 5 位房间号')
    return
  }
  try {
    const room = await joinRoom(joinCode.value)
    game.setRoom(room)
    showToast('已加入房间')
    routeByStatus(room, true)
  } catch (error) {
    showToast(error.message)
  }
}

async function loadRoom() {
  if (!game.currentRoomCode) return
  try {
    const room = await getRoom(game.currentRoomCode)
    game.setRoom(room)
    routeByStatus(room, true)
  } catch {
    game.clearRoom()
  }
}

async function start() {
  try {
    const room = await startGame(game.room.roomCode)
    game.setRoom(room)
    router.push(`/mobile/room/${room.roomCode}/select`)
  } catch (error) {
    showToast(error.message)
  }
}

async function leave() {
  try {
    await showConfirmDialog({ title: '离开房间', message: '确定离开当前房间？' })
    await leaveRoom(game.room.roomCode)
    game.clearRoom()
  } catch (error) {
    if (error?.message) showToast(error.message)
  }
}

function logout() {
  user.logout()
  game.clearRoom()
  router.replace('/login')
}

function routeByStatus(room, passive = false) {
  if (room.status === 'SELECTING') router.replace(`/mobile/room/${room.roomCode}/select`)
  if (room.status === 'PLAYING') router.replace(`/mobile/room/${room.roomCode}/play`)
  if (!passive && room.status === 'WAITING') router.replace('/mobile/hall')
}
</script>

<style scoped>
.hall-head {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  align-items: center;
  margin-bottom: 18px;
}

.hall-head h1 {
  margin: 4px 0 0;
  font-size: 28px;
  letter-spacing: 0;
}

.hall-head p {
  margin: 0;
}

.small {
  min-height: 36px;
  padding: 0 12px;
}

.action-panel {
  padding: 14px;
  display: grid;
  gap: 14px;
}

.mode-picker {
  display: grid;
  gap: 6px;
  color: var(--warm-muted);
  font-size: 13px;
  font-weight: 900;
}

.mode-picker select {
  min-height: 42px;
}

.big-action {
  width: 100%;
  min-height: 136px;
  border: 0;
  border-radius: 8px;
  background: linear-gradient(135deg, #c46a2d, #e6a13b);
  color: #fffaf2;
  font-size: 24px;
  font-weight: 900;
}

.join-box {
  display: grid;
  grid-template-columns: 1fr auto;
  gap: 10px;
}

.code-field {
  text-align: center;
  font-size: 24px;
  font-weight: 900;
  letter-spacing: 4px;
}

.room-panel {
  padding: 14px;
}

.room-code {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 10px;
  margin-bottom: 12px;
}

.room-mode {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 10px;
  margin: -4px 0 12px;
  color: var(--warm-muted);
  font-size: 13px;
}

.room-mode strong {
  min-width: 0;
  color: #6b513c;
  text-align: right;
}

.room-code span {
  color: var(--warm-muted);
}

.room-code strong {
  font-size: 34px;
  color: var(--warm-primary-strong);
  letter-spacing: 3px;
}

.room-actions {
  display: grid;
  grid-template-columns: 1fr 80px;
}

.start-hint {
  margin: 10px 0 0;
  color: var(--warm-muted);
  font-size: 13px;
  font-weight: 700;
}

.players {
  margin-top: 20px;
}

.player-row {
  min-height: 62px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 12px;
  margin-bottom: 10px;
}

.player-row strong {
  margin-right: 8px;
}
</style>
