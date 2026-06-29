<template>
  <main class="page">
    <div class="mobile-shell">
      <header class="select-head">
        <div>
          <span class="muted">房间 {{ roomCode }}</span>
          <h1>局前选将</h1>
        </div>
        <button v-if="room?.owner" class="warm-button danger head-button" @click="dissolve">解散房间</button>
      </header>

      <section v-if="room?.me" class="secret-row">
        <div
          :class="['identity-card', identityShown && 'revealed']"
          @pointerdown="holdIdentity"
          @pointerup="releaseIdentity"
          @pointercancel="releaseIdentity"
          @pointerleave="releaseIdentity"
        >
          {{ identityShown ? room.me.identity : '按住看身份' }}
        </div>
        <button v-if="room.me.allowLordGeneral" class="warm-button secondary" @click="openLordSearch">
          特殊武将检索
        </button>
      </section>

      <section v-if="preBattlePlayers.length" class="pre-battle">
        <h2 class="section-title">局前战局</h2>
        <article
          v-for="player in preBattlePlayers"
          :key="player.userId"
          class="pre-battle-row panel"
        >
          <div>
            <strong>{{ player.username }}</strong>
            <span v-if="player.owner" class="status-pill">房主</span>
          </div>
          <button
            v-if="player.selectedGeneral"
            class="revealed-general"
            @click="modalImage = player.selectedGeneral"
          >
            {{ player.selectedGeneral.name }}
          </button>
          <span v-else class="muted">{{ player.locked ? '武将暗置' : '等待锁定' }}</span>
          <span class="identity-badge">{{ player.identityVisible ? player.identity : '?' }}</span>
        </article>
      </section>

      <section v-if="room?.me" class="pool">
        <div class="pool-head">
          <h2 class="section-title">{{ room.me.locked ? '已锁定武将' : '我的盲选将' }}</h2>
          <span :class="['status-pill', room.me.locked ? 'green' : '']">{{ room.me.locked ? '已锁定' : '未锁定' }}</span>
        </div>
        <button
          v-if="room.me.locked && selectedGeneral"
          type="button"
          class="locked-general-card panel"
          @click="openImage(selectedGeneral)"
        >
          <img :src="assetUrl(selectedGeneral.imagePath)" :alt="selectedGeneral.name" />
          <strong>{{ selectedGeneral.name }}</strong>
        </button>
        <div v-else class="card-grid">
          <article
            v-for="general in room.me.generalPool"
            :key="general.id"
            :class="['general-card', selectedId === general.id && 'selected']"
            @click="pick(general)"
          >
            <button type="button" class="general-image-button" @click.stop="openImage(general)">
              <img :src="assetUrl(general.imagePath)" :alt="general.name" />
            </button>
            <div class="general-card__body">
              <strong>{{ general.name }}</strong>
              <button type="button" class="card-pick-button" @click.stop="pick(general)">
                {{ selectedId === general.id ? '已选择' : '选择' }}
              </button>
            </div>
          </article>
        </div>
      </section>

      <section v-if="selectedGeneral && !room?.me?.locked" class="panel selected-panel">
        <span class="muted">已选择</span>
        <button class="selected-general" @click="openImage(selectedGeneral)">
          {{ selectedGeneral.name }}
        </button>
      </section>

      <div v-if="room?.me && !room.me.locked" class="fixed-bottom">
        <button class="warm-button" :disabled="!selectedId || room?.me?.locked" @click="lock">
          锁定出阵
        </button>
      </div>
    </div>

    <van-popup v-model:show="lordSearchVisible" position="bottom" round class="lord-popup">
      <div class="lord-search">
        <input v-model.trim="keyword" class="field" placeholder="搜索武将" />
        <div class="lord-body">
          <div class="lord-list">
            <button
              v-for="general in lordCards"
              :key="general.id"
              :class="['lord-row', preview?.id === general.id && 'active']"
              @click="preview = general"
            >
              {{ general.name }}
            </button>
          </div>
          <div class="lord-preview">
            <button v-if="preview" type="button" class="lord-preview-image" @click="openImage(preview)">
              <img :src="assetUrl(preview.imagePath)" :alt="preview.name" />
            </button>
            <span v-else class="muted">选择一名武将</span>
          </div>
        </div>
        <button class="warm-button" :disabled="!preview" @click="confirmLordPick">确认挑定</button>
      </div>
    </van-popup>

    <ImageModal v-model="modalVisible" :image="modalImage" />
  </main>
</template>

<script setup>
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { Popup as VanPopup, showConfirmDialog, showToast } from 'vant'
import ImageModal from '../../components/ImageModal.vue'
import { chooseGeneral, dissolveRoom, getRoom, lockGeneral, safeLordGenerals } from '../../api/room'
import { useRoomSocket } from '../../composables/useWebSocket'
import { useGameStore } from '../../store/game'
import { assetUrl } from '../../utils/pathHelper'

const route = useRoute()
const router = useRouter()
const game = useGameStore()
const roomCode = route.params.roomCode
const room = computed(() => game.room)
const selectedId = ref(null)
const holdingIdentity = ref(false)
const lordSearchVisible = ref(false)
const keyword = ref('')
const lordCards = ref([])
const preview = ref(null)
const modalImage = ref(null)
const dissolving = ref(false)
let keywordTimer = 0
const modalVisible = computed({
  get: () => Boolean(modalImage.value),
  set: (value) => {
    if (!value) modalImage.value = null
  }
})

const selectedGeneral = computed(() => room.value?.me?.selectedGeneral || room.value?.me?.generalPool?.find((item) => item.id === selectedId.value))
const preBattlePlayers = computed(() => room.value?.players?.filter((player) => player.userId !== room.value?.me?.userId) || [])
const identityShown = computed(() => room.value?.me?.identityVisibleRule || room.value?.me?.identityLeader || holdingIdentity.value)

onMounted(loadRoom)
useRoomSocket(roomCode, loadRoom)

watch(keyword, () => {
  window.clearTimeout(keywordTimer)
  keywordTimer = window.setTimeout(fetchLordCards, 180)
})

async function loadRoom() {
  try {
    const data = await getRoom(roomCode)
    game.setRoom(data)
    selectedId.value = data.me?.selectedGeneral?.id || selectedId.value
    if (data.status === 'WAITING') router.replace('/mobile/hall')
    if (data.status === 'PLAYING') router.replace(`/mobile/room/${roomCode}/play`)
  } catch (error) {
    handleRoomLoadError(error)
  }
}

async function pick(general) {
  if (room.value?.me?.locked) return
  try {
    const data = await chooseGeneral(roomCode, general.id)
    game.setRoom(data)
    selectedId.value = general.id
  } catch (error) {
    showToast(error.message)
  }
}

function openImage(general) {
  if (general) modalImage.value = general
}

function holdIdentity() {
  if (!room.value?.me?.identityVisibleRule && !room.value?.me?.identityLeader) holdingIdentity.value = true
}

function releaseIdentity() {
  if (!room.value?.me?.identityVisibleRule && !room.value?.me?.identityLeader) holdingIdentity.value = false
}

async function lock() {
  try {
    const data = await lockGeneral(roomCode)
    game.setRoom(data)
    if (data.status === 'PLAYING') router.replace(`/mobile/room/${roomCode}/play`)
    else showToast('已锁定')
  } catch (error) {
    showToast(error.message)
  }
}

async function openLordSearch() {
  lordSearchVisible.value = true
  keyword.value = ''
  preview.value = null
  await fetchLordCards()
}

async function fetchLordCards() {
  if (!lordSearchVisible.value) return
  try {
    lordCards.value = await safeLordGenerals(roomCode, keyword.value)
    if (!lordCards.value.some((item) => item.id === preview.value?.id)) {
      preview.value = lordCards.value[0] || null
    }
  } catch (error) {
    showToast(error.message)
  }
}

async function confirmLordPick() {
  if (!preview.value) return
  await pick(preview.value)
  lordSearchVisible.value = false
}

async function dissolve() {
  try {
    await showConfirmDialog({ title: '解散房间', message: '确定解散当前房间？所有玩家都会回到大厅。' })
    dissolving.value = true
    await dissolveRoom(roomCode)
    game.clearRoom()
    showToast('房间已解散')
    router.replace('/mobile/hall')
  } catch (error) {
    dissolving.value = false
    if (error?.message) showToast(error.message)
  }
}

function handleRoomLoadError(error) {
  if (dissolving.value) return
  if (isRoomGone(error)) {
    game.clearRoom()
    showToast('房间已解散')
    router.replace('/mobile/hall')
    return
  }
  showToast(error.message)
}

function isRoomGone(error) {
  return Number(error?.code) === 1001 || error?.message === '房间不存在'
}
</script>

<style scoped>
.select-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
  margin-bottom: 14px;
}

.select-head h1 {
  margin: 3px 0 0;
  font-size: 26px;
  letter-spacing: 0;
}

.head-button {
  flex: 0 0 auto;
  min-height: 36px;
  padding: 0 12px;
}

.secret-row {
  display: grid;
  grid-template-columns: 1fr 142px;
  gap: 10px;
  margin-bottom: 16px;
}

.pool {
  margin-top: 10px;
}

.pre-battle {
  margin: 4px 0 16px;
}

.pre-battle-row {
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(86px, auto) 42px;
  gap: 8px;
  align-items: center;
  min-height: 58px;
  padding: 10px;
  margin-bottom: 8px;
}

.pre-battle-row strong {
  margin-right: 6px;
}

.revealed-general {
  border: 0;
  background: transparent;
  color: var(--warm-primary-strong);
  font-weight: 900;
  text-align: right;
}

.identity-badge {
  display: grid;
  place-items: center;
  min-height: 34px;
  border-radius: 8px;
  background: #ffe6bf;
  color: var(--warm-primary-strong);
  font-weight: 900;
}

.pool-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
}

.locked-general-card {
  display: block;
  width: 100%;
  overflow: hidden;
  padding: 0;
  border: 1px solid var(--warm-line);
  background: #fffaf2;
  color: var(--warm-text);
  cursor: zoom-in;
}

.locked-general-card img {
  display: block;
  width: 100%;
  max-height: min(62vh, 580px);
  aspect-ratio: 3 / 4.2;
  object-fit: contain;
  background: #fff0d7;
}

.locked-general-card strong {
  display: block;
  padding: 12px;
  color: var(--warm-primary-strong);
  font-size: 22px;
  line-height: 1.2;
  text-align: center;
}

.general-image-button {
  display: block;
  width: 100%;
  border: 0;
  padding: 0;
  background: transparent;
  cursor: zoom-in;
}

.general-card__body {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  gap: 6px;
  align-items: center;
  padding: 8px;
}

.general-card__body strong {
  min-width: 0;
  padding: 0;
}

.card-pick-button {
  border: 1px solid #e5c292;
  border-radius: 8px;
  min-height: 32px;
  padding: 0 9px;
  background: #ffe7c1;
  color: var(--warm-primary-strong);
  font-size: 13px;
  font-weight: 900;
}

.general-card.selected .card-pick-button {
  border-color: var(--warm-primary);
  background: var(--warm-primary);
  color: #fffaf3;
}

.selected-panel {
  margin-top: 14px;
  padding: 12px;
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.selected-general {
  border: 0;
  background: transparent;
  color: var(--warm-primary-strong);
  font-size: 20px;
  font-weight: 900;
}

.lord-popup {
  min-height: 78vh;
  background: var(--warm-bg);
}

.lord-search {
  display: grid;
  gap: 12px;
  padding: 16px;
}

.lord-body {
  display: grid;
  grid-template-columns: minmax(118px, 38%) 1fr;
  gap: 12px;
  min-height: 52vh;
}

.lord-list {
  overflow: auto;
  border: 1px solid var(--warm-line);
  border-radius: 8px;
  background: #fffaf2;
}

.lord-row {
  display: block;
  width: 100%;
  min-height: 44px;
  border: 0;
  border-bottom: 1px solid #efd8b9;
  background: transparent;
  color: var(--warm-text);
  text-align: left;
  padding: 0 12px;
  font-weight: 800;
}

.lord-row.active {
  background: #ffe5bb;
  color: var(--warm-primary-strong);
}

.lord-preview {
  display: grid;
  place-items: center;
  overflow: hidden;
  border-radius: 8px;
  background: #fff2de;
  border: 1px solid var(--warm-line);
}

.lord-preview img {
  width: 100%;
  height: 100%;
  object-fit: contain;
}

.lord-preview-image {
  width: 100%;
  height: 100%;
  border: 0;
  padding: 0;
  background: transparent;
  cursor: zoom-in;
}
</style>
