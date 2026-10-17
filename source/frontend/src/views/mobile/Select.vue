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
        <div v-if="room.me.allowLordGeneral || room.me.canManualPick" class="secret-actions">
          <button v-if="room.me.allowLordGeneral" class="warm-button secondary" @click="openLordSearch">
            特殊武将检索
          </button>
          <button v-if="room.me.canManualPick" class="warm-button secondary" @click="openManualPick">
            点将
          </button>
        </div>
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
          <div class="pre-battle-actions">
            <button
              v-if="player.selectedGeneral"
              class="revealed-general"
              @click="modalImage = player.selectedGeneral"
            >
              {{ player.selectedGeneral.name }}<template v-for="general in player.secondaryGenerals || []" :key="general.id"> / {{ general.name }}</template>
            </button>
            <span v-else class="muted">{{ player.locked ? '武将暗置' : '等待锁定' }}</span>
            <button
              v-if="player.generalPool?.length"
              type="button"
              class="pool-trigger"
              @click="openTeammatePool(player)"
            >
              将框
            </button>
          </div>
          <span class="identity-badge">{{ player.identityVisible ? player.identity : '?' }}</span>
        </article>
      </section>

      <section v-if="room?.me" class="pool">
        <div class="pool-head">
          <h2 class="section-title">{{ room.me.locked ? '已锁定武将' : '我的盲选将' }}</h2>
          <span :class="['status-pill', room.me.locked ? 'green' : '']">{{ room.me.locked ? '已锁定' : '未锁定' }}</span>
          <select v-if="room.me.generalSelectCount > 1 && !room.me.locked" v-model.number="activeSelectionIndex" class="selection-slot-select">
            <option
              v-for="index in room.me.generalSelectCount"
              :key="index"
              :value="index - 1"
              :disabled="index - 1 > selectedGenerals.length"
            >
              {{ selectionSlotLabel(index - 1) }}
            </option>
          </select>
        </div>
        <div v-if="room.me.locked && selectedGeneral" class="locked-general-grid">
          <button v-for="(general, index) in selectedGenerals" :key="general.id" type="button" class="locked-general-card panel" @click="openImage(general)">
            <img :src="assetUrl(general.imagePath)" :alt="general.name" />
            <strong>{{ general.name }}</strong>
            <span v-if="room.me.generalSelectCount > 1" class="locked-general-role">{{ selectionSlotLabel(index, false) }}</span>
          </button>
        </div>
        <div v-else class="card-grid">
          <article
            v-for="(general, index) in room.me.generalPool"
            :key="general.id"
            :class="['general-card', selectedGeneralIds.includes(general.id) && 'selected']"
            @click="pick(general)"
          >
            <button
              v-if="room.me.canSwapGeneral"
              type="button"
              :class="['swap-general-button', isSwapUsed(index) && 'used']"
              :disabled="swapBusyId === general.id || isSwapUsed(index)"
              aria-label="换将"
              :title="isSwapUsed(index) ? '本将框已换过' : '换将'"
              @click.stop="swapPoolCard(general)"
            >
              <Check v-if="isSwapUsed(index)" :size="17" />
              <Refresh v-else :size="17" />
            </button>
            <button type="button" class="general-image-button" @click.stop="openImage(general)">
              <img :src="assetUrl(general.imagePath)" :alt="general.name" />
            </button>
            <div class="general-card__body">
              <strong>{{ general.name }}</strong>
              <button type="button" class="card-pick-button" @click.stop="pick(general)">
                {{ selectedGenerals[activeSelectionIndex]?.id === general.id ? '当前将位已选' : '选择' }}
              </button>
            </div>
          </article>
        </div>
      </section>

      <section v-if="selectedGeneral && !room?.me?.locked" class="panel selected-panel">
        <span class="muted">{{ room.me.generalSelectCount > 1 ? `已选择 ${selectedGenerals.length} / ${room.me.generalSelectCount} 名` : '已选择' }}</span>
        <button class="selected-general" @click="openImage(selectedGeneral)">
          {{ selectedGeneralNames }}
        </button>
      </section>

      <div v-if="room?.me && !room.me.locked" class="fixed-bottom">
        <button class="warm-button" :disabled="selectedGenerals.length < (room?.me?.generalSelectCount || 1) || room?.me?.locked" @click="lock">
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

    <van-popup v-model:show="manualPickVisible" position="bottom" round class="lord-popup">
      <div class="lord-search">
        <input v-model.trim="manualKeyword" class="field" placeholder="搜索点将武将" />
        <div class="lord-body">
          <div class="lord-list">
            <button
              v-for="general in manualCards"
              :key="general.id"
              :class="['lord-row', manualPreview?.id === general.id && 'active']"
              @click="manualPreview = general"
            >
              {{ general.name }}
            </button>
          </div>
          <div class="lord-preview">
            <button v-if="manualPreview" type="button" class="lord-preview-image" @click="openImage(manualPreview)">
              <img :src="assetUrl(manualPreview.imagePath)" :alt="manualPreview.name" />
            </button>
            <span v-else class="muted">选择一名武将</span>
          </div>
        </div>
        <button class="warm-button" :disabled="!manualPreview" @click="confirmManualPick">确认点将</button>
      </div>
    </van-popup>

    <van-popup v-model:show="teammatePoolVisible" position="bottom" round class="teammate-popup">
      <div class="teammate-popup-inner">
        <div class="teammate-popup-head">
          <strong>{{ teammatePoolPlayer?.username || '队友' }}的将框</strong>
          <span class="muted">{{ teammatePoolPlayer?.generalPool?.length || 0 }} 张</span>
        </div>
        <div class="teammate-popup-grid">
          <button
            v-for="general in teammatePoolPlayer?.generalPool || []"
            :key="general.id"
            type="button"
            :class="['teammate-general', teammatePoolPlayer?.selectedGeneral?.id === general.id && 'selected']"
            @click="openImage(general)"
          >
            <img :src="assetUrl(general.imagePath)" :alt="general.name" />
            <span>{{ general.name }}</span>
          </button>
        </div>
      </div>
    </van-popup>

    <ImageModal v-model="modalVisible" :image="modalImage" />
  </main>
</template>

<script setup>
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { Popup as VanPopup, showConfirmDialog, showToast } from 'vant'
import { Check, Refresh } from '@element-plus/icons-vue'
import ImageModal from '../../components/ImageModal.vue'
import { chooseGeneral, dissolveRoom, getRoom, lockGeneral, manualPickGenerals, safeLordGenerals, swapGeneral } from '../../api/room'
import { useRoomSocket } from '../../composables/useWebSocket'
import { useGameStore } from '../../store/game'
import { assetUrl } from '../../utils/pathHelper'

const route = useRoute()
const router = useRouter()
const game = useGameStore()
const roomCode = route.params.roomCode
const room = computed(() => game.room)
const selectedId = ref(null)
const activeSelectionIndex = ref(0)
const holdingIdentity = ref(false)
const lordSearchVisible = ref(false)
const keyword = ref('')
const lordCards = ref([])
const preview = ref(null)
const manualPickVisible = ref(false)
const manualKeyword = ref('')
const manualCards = ref([])
const manualPreview = ref(null)
const teammatePoolPlayer = ref(null)
const modalImage = ref(null)
const dissolving = ref(false)
const swapBusyId = ref(null)
let keywordTimer = 0
let manualKeywordTimer = 0
const modalVisible = computed({
  get: () => Boolean(modalImage.value),
  set: (value) => {
    if (!value) modalImage.value = null
  }
})
const teammatePoolVisible = computed({
  get: () => Boolean(teammatePoolPlayer.value),
  set: (value) => {
    if (!value) teammatePoolPlayer.value = null
  }
})

const selectedGeneral = computed(() => room.value?.me?.selectedGeneral || room.value?.me?.generalPool?.find((item) => item.id === selectedId.value))
const selectedGenerals = computed(() => [room.value?.me?.selectedGeneral, ...(room.value?.me?.secondaryGenerals || [])].filter(Boolean))
const selectedGeneralIds = computed(() => selectedGenerals.value.map((general) => general.id))
const selectedGeneralNames = computed(() => selectedGenerals.value.map((general) => general.name).join(' / '))
const preBattlePlayers = computed(() => room.value?.players?.filter((player) => player.userId !== room.value?.me?.userId) || [])
const identityShown = computed(() => room.value?.me?.identityVisibleRule || room.value?.me?.identityLeader || holdingIdentity.value)

onMounted(loadRoom)
useRoomSocket(roomCode, loadRoom)

watch(keyword, () => {
  window.clearTimeout(keywordTimer)
  keywordTimer = window.setTimeout(fetchLordCards, 180)
})

watch(manualKeyword, () => {
  window.clearTimeout(manualKeywordTimer)
  manualKeywordTimer = window.setTimeout(fetchManualCards, 180)
})

async function loadRoom() {
  try {
    const data = await getRoom(roomCode)
    game.setRoom(data)
    selectedId.value = data.me?.selectedGeneral?.id || null
    activeSelectionIndex.value = Math.min(activeSelectionIndex.value, selectedGenerals.value.length)
    if (data.status === 'WAITING') router.replace('/mobile/hall')
    if (data.status === 'PLAYING') router.replace(`/mobile/room/${roomCode}/play`)
  } catch (error) {
    handleRoomLoadError(error)
  }
}

async function pick(general) {
  if (room.value?.me?.locked) return
  try {
    const data = await chooseGeneral(roomCode, general.id, activeSelectionIndex.value)
    game.setRoom(data)
    selectedId.value = data.me?.selectedGeneral?.id || null
    activeSelectionIndex.value = Math.min(activeSelectionIndex.value, selectedGenerals.value.length - 1)
  } catch (error) {
    showToast(error.message)
  }
}

async function swapPoolCard(general) {
  if (room.value?.me?.locked || !room.value?.me?.canSwapGeneral || swapBusyId.value) return
  swapBusyId.value = general.id
  try {
    const data = await swapGeneral(roomCode, general.id)
    game.setRoom(data)
    selectedId.value = data.me?.selectedGeneral?.id || null
    showToast('已换将')
  } catch (error) {
    showToast(error.message)
  } finally {
    swapBusyId.value = null
  }
}

function isSwapUsed(index) {
  return room.value?.me?.swappedGeneralPoolIndexes?.includes(index)
}

function selectionSlotLabel(index, includeName = true) {
  const name = selectedGenerals.value[index]?.name
  const prefix = `将位 ${index + 1}`
  return includeName && name ? `${prefix}：${name}` : prefix
}

function openImage(general) {
  if (general) modalImage.value = general
}

function openTeammatePool(player) {
  teammatePoolPlayer.value = player
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

async function openManualPick() {
  manualPickVisible.value = true
  manualKeyword.value = ''
  manualPreview.value = null
  await fetchManualCards()
}

async function fetchManualCards() {
  if (!manualPickVisible.value) return
  try {
    manualCards.value = await manualPickGenerals(roomCode, manualKeyword.value)
    if (!manualCards.value.some((item) => item.id === manualPreview.value?.id)) {
      manualPreview.value = manualCards.value[0] || null
    }
  } catch (error) {
    showToast(error.message)
  }
}

async function confirmManualPick() {
  if (!manualPreview.value) return
  await pick(manualPreview.value)
  manualPickVisible.value = false
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
  grid-template-columns: minmax(0, 1fr) auto;
  align-items: stretch;
  gap: 10px;
  margin-bottom: 16px;
}

.secret-actions {
  display: flex;
  flex-wrap: wrap;
  justify-content: flex-end;
  gap: 8px;
}

.secret-actions .warm-button {
  min-width: 96px;
  min-height: 44px;
  padding: 0 12px;
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

.teammate-general {
  min-width: 0;
  border: 1px solid var(--warm-line);
  border-radius: 8px;
  padding: 4px;
  background: #fffaf2;
  color: var(--warm-text);
  cursor: zoom-in;
}

.teammate-general.selected {
  border-color: var(--warm-primary);
  box-shadow: 0 0 0 2px rgba(177, 86, 31, 0.14);
}

.teammate-general img {
  display: block;
  width: 100%;
  aspect-ratio: 3 / 4;
  object-fit: cover;
  border-radius: 6px;
  background: #f4dcc0;
}

.teammate-general span {
  display: block;
  margin-top: 3px;
  overflow: hidden;
  color: var(--warm-primary-strong);
  font-size: 11px;
  font-weight: 900;
  line-height: 1.2;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.pre-battle-row strong {
  margin-right: 6px;
}

.pre-battle-actions {
  display: flex;
  justify-content: flex-end;
  align-items: center;
  gap: 6px;
  min-width: 0;
}

.revealed-general {
  border: 0;
  background: transparent;
  color: var(--warm-primary-strong);
  font-weight: 900;
  text-align: right;
}

.pool-trigger {
  flex: 0 0 auto;
  min-height: 28px;
  border: 1px solid var(--warm-line);
  border-radius: 8px;
  padding: 0 8px;
  background: #fffaf2;
  color: var(--warm-primary-strong);
  font-size: 12px;
  font-weight: 900;
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

.general-card {
  position: relative;
}

.swap-general-button {
  position: absolute;
  top: 7px;
  right: 7px;
  z-index: 1;
  display: grid;
  place-items: center;
  width: 34px;
  height: 34px;
  border: 1px solid rgba(96, 50, 20, 0.22);
  border-radius: 999px;
  background: rgba(255, 250, 242, 0.92);
  color: var(--warm-primary-strong);
  box-shadow: 0 6px 16px rgba(72, 39, 16, 0.18);
  cursor: pointer;
}

.swap-general-button:disabled {
  cursor: not-allowed;
  opacity: 0.6;
}

.swap-general-button.used {
  background: rgba(231, 241, 224, 0.94);
  color: var(--warm-success);
}

.swap-general-button svg {
  width: 17px;
  height: 17px;
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

.locked-general-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(0, 1fr));
  gap: 10px;
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

.locked-general-role {
  display: block;
  padding: 0 12px 10px;
  color: var(--warm-muted);
  font-size: 13px;
  font-weight: 800;
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

.selection-slot-select {
  min-height: 30px;
  max-width: 170px;
  border: 1px solid var(--warm-line);
  border-radius: 7px;
  padding: 0 6px;
  background: #fffaf2;
  color: var(--warm-primary-strong);
  font-size: 12px;
  font-weight: 800;
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

.teammate-popup {
  max-height: 72vh;
  background: #fff7ea;
}

.teammate-popup-inner {
  display: grid;
  gap: 12px;
  max-height: 72vh;
  padding: 16px;
  overflow: auto;
}

.teammate-popup-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 10px;
}

.teammate-popup-head strong {
  color: var(--warm-primary-strong);
  font-size: 18px;
}

.teammate-popup-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(72px, 1fr));
  gap: 8px;
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

@media (max-width: 430px) {
  .secret-row {
    grid-template-columns: 1fr;
  }

  .secret-actions {
    justify-content: stretch;
  }

  .secret-actions .warm-button {
    flex: 1 1 0;
  }
}
</style>
