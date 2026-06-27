<template>
  <main class="page">
    <div class="mobile-shell play-shell">
      <header class="play-head">
        <div>
          <span class="muted">房间 {{ roomCode }}</span>
          <h1>全场战局</h1>
        </div>
      </header>

      <section class="battle-board">
        <article
          v-for="player in otherPlayers"
          :key="player.userId"
          :class="['battle-row panel', player.dead && 'dead']"
        >
          <button
            type="button"
            class="battle-main"
            :disabled="!player.selectedGeneral"
            @click="openImage(player.selectedGeneral)"
          >
            <strong>{{ player.username }}</strong>
            <p>{{ player.selectedGeneral?.name || (player.generalVisible ? '未选择' : '武将暗置') }}</p>
          </button>
          <div class="identity-chip">
            {{ player.identityVisible ? player.identity : '?' }}
          </div>

          <div v-if="player.extraGenerals?.length" class="battle-extra-list">
            <button
              v-for="(general, index) in player.extraGenerals"
              :key="`${player.userId}-${index}-${general.id || 'hidden'}`"
              type="button"
              :class="['battle-extra', general.revealed && 'revealed']"
              :disabled="!general.id"
              @click="openImage(general)"
            >
              <span>{{ general.name || '暗置武将' }}</span>
            </button>
          </div>
        </article>
      </section>

      <section v-if="room?.me" class="me-zone">
        <div
          :class="['identity-card', identityShown && 'revealed']"
          @pointerdown="holdIdentity"
          @pointerup="releaseIdentity"
          @pointercancel="releaseIdentity"
          @pointerleave="releaseIdentity"
        >
          {{ identityShown ? room.me.identity : '按住看身份' }}
        </div>

        <button class="my-general panel" @click="openImage(room.me.selectedGeneral)">
          <img v-if="room.me.selectedGeneral" :src="assetUrl(room.me.selectedGeneral.imagePath)" :alt="room.me.selectedGeneral.name" />
          <span>{{ room.me.selectedGeneral?.name || '我的武将' }}</span>
        </button>

        <div class="extra-draw panel">
          <select v-model="extraFaction" class="field faction-select" aria-label="抽将势力">
            <option v-for="option in factionOptions" :key="option.value || 'all'" :value="option.value">
              {{ option.label }}
            </option>
          </select>
          <button class="warm-button draw-button" :disabled="extraBusy" @click="drawExtra">
            {{ extraBusy ? '抽取中' : '+ 抽将' }}
          </button>
        </div>

        <div v-if="myExtraGenerals.length" class="my-extra-grid">
          <article
            v-for="(general, index) in myExtraGenerals"
            :key="`${general.id}-${index}`"
            :class="['extra-card panel', general.revealed && 'revealed']"
          >
            <button type="button" class="extra-card-image" @click="openImage(general)">
              <img v-if="general.imagePath" :src="assetUrl(general.imagePath)" :alt="general.name" />
            </button>
            <div class="extra-card-body">
              <strong>{{ general.name }}</strong>
              <span :class="['status-pill', general.revealed && 'green']">
                {{ general.revealed ? '已明置' : '暗置' }}
              </span>
            </div>
            <div class="extra-actions">
              <button
                v-if="!general.revealed"
                type="button"
                class="extra-action"
                :disabled="extraBusy"
                @click="revealExtra(index)"
              >
                明置
              </button>
              <button
                type="button"
                class="extra-action danger"
                :disabled="extraBusy"
                @click="removeExtra(index)"
              >
                -
              </button>
            </div>
          </article>
        </div>

        <button v-if="canRevealGeneral" class="warm-button reveal-button" @click="revealMyGeneral">
          明置武将
        </button>

        <button class="warm-button danger death-button" :disabled="room.me.dead" @click="markMeDead">
          {{ room.me.dead ? '已阵亡' : '标记阵亡' }}
        </button>
      </section>

      <div v-if="room?.owner" class="fixed-bottom owner-actions">
        <button class="warm-button" @click="restart">再来一局</button>
        <button class="warm-button danger" @click="dissolve">解散房间</button>
      </div>
    </div>

    <ImageModal v-model="modalVisible" :image="modalImage" />
  </main>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { showConfirmDialog, showToast } from 'vant'
import ImageModal from '../../components/ImageModal.vue'
import {
  drawExtraGeneral,
  dissolveRoom,
  getRoom,
  markDead,
  removeExtraGeneral,
  restartRoom,
  revealExtraGeneral,
  revealGeneral
} from '../../api/room'
import { useRoomSocket } from '../../composables/useWebSocket'
import { useGameStore } from '../../store/game'
import { assetUrl } from '../../utils/pathHelper'

const factionOptions = [
  { label: '不限势力', value: '' },
  { label: '魏', value: 'WEI' },
  { label: '蜀', value: 'SHU' },
  { label: '吴', value: 'WU' },
  { label: '群', value: 'QUN' },
  { label: '晋', value: 'JIN' },
  { label: '神', value: 'GOD' }
]

const route = useRoute()
const router = useRouter()
const game = useGameStore()
const roomCode = route.params.roomCode
const room = computed(() => game.room)
const holdingIdentity = ref(false)
const extraFaction = ref('')
const extraBusy = ref(false)
const modalImage = ref(null)
const dissolving = ref(false)
const modalVisible = computed({
  get: () => Boolean(modalImage.value),
  set: (value) => {
    if (!value) modalImage.value = null
  }
})

const otherPlayers = computed(() => room.value?.players?.filter((player) => player.userId !== room.value?.me?.userId) || [])
const myExtraGenerals = computed(() => room.value?.me?.extraGenerals || [])
const isLord = computed(() => room.value?.me?.identity === '主公')
const identityShown = computed(() => isLord.value || holdingIdentity.value)
const canRevealGeneral = computed(() => Boolean(room.value?.me?.selectedGeneral?.startsHidden && !room.value?.me?.generalRevealed))

onMounted(loadRoom)
useRoomSocket(roomCode, loadRoom)

async function loadRoom() {
  try {
    const data = await getRoom(roomCode)
    game.setRoom(data)
    if (data.status === 'WAITING') router.replace('/mobile/hall')
    if (data.status === 'SELECTING') router.replace(`/mobile/room/${roomCode}/select`)
  } catch (error) {
    handleRoomLoadError(error)
  }
}

function openImage(general) {
  if (general?.imagePath) {
    modalImage.value = general
  }
}

function holdIdentity() {
  if (!isLord.value) holdingIdentity.value = true
}

function releaseIdentity() {
  if (!isLord.value) holdingIdentity.value = false
}

async function drawExtra() {
  if (extraBusy.value) return
  extraBusy.value = true
  try {
    const data = await drawExtraGeneral(roomCode, extraFaction.value)
    game.setRoom(data)
    showToast('已抽取武将')
  } catch (error) {
    showToast(error.message)
  } finally {
    extraBusy.value = false
  }
}

async function revealExtra(index) {
  if (extraBusy.value) return
  extraBusy.value = true
  try {
    const data = await revealExtraGeneral(roomCode, index)
    game.setRoom(data)
    showToast('已明置武将')
  } catch (error) {
    showToast(error.message)
  } finally {
    extraBusy.value = false
  }
}

async function removeExtra(index) {
  if (extraBusy.value) return
  extraBusy.value = true
  try {
    const data = await removeExtraGeneral(roomCode, index)
    game.setRoom(data)
    showToast('已移除')
  } catch (error) {
    showToast(error.message)
  } finally {
    extraBusy.value = false
  }
}

async function markMeDead() {
  try {
    await showConfirmDialog({ title: '标记阵亡', message: '确认后身份将全场翻开' })
    const data = await markDead(roomCode, room.value.me.userId)
    game.setRoom(data)
  } catch (error) {
    if (error?.message) showToast(error.message)
  }
}

async function revealMyGeneral() {
  try {
    const data = await revealGeneral(roomCode)
    game.setRoom(data)
    showToast('已明置武将')
  } catch (error) {
    showToast(error.message)
  }
}

async function restart() {
  try {
    await showConfirmDialog({ title: '再来一局', message: '全场将回到房间大厅' })
    const data = await restartRoom(roomCode)
    game.setRoom(data)
    router.replace('/mobile/hall')
  } catch (error) {
    if (error?.message) showToast(error.message)
  }
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
.play-shell {
  padding-bottom: 112px;
}

.play-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
  margin-bottom: 12px;
}

.play-head h1 {
  margin: 4px 0 0;
  font-size: 27px;
  letter-spacing: 0;
}

.owner-actions {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 104px;
  gap: 8px;
}

.battle-board {
  display: grid;
  gap: 10px;
  min-height: 34vh;
}

.battle-row {
  min-height: 74px;
  display: grid;
  grid-template-columns: 1fr 54px;
  gap: 10px;
  align-items: center;
  padding: 12px;
}

.battle-row.dead {
  opacity: 0.62;
  filter: grayscale(0.45);
}

.battle-main {
  min-width: 0;
  border: 0;
  padding: 0;
  background: transparent;
  color: var(--warm-text);
  text-align: left;
}

.battle-main:disabled {
  cursor: default;
}

.battle-main strong {
  display: block;
  font-size: 18px;
}

.battle-main p {
  margin: 5px 0 0;
  color: var(--warm-muted);
  font-weight: 700;
}

.identity-chip {
  display: grid;
  place-items: center;
  min-height: 54px;
  border-radius: 8px;
  background: #ffe5bb;
  color: var(--warm-primary-strong);
  font-weight: 900;
}

.battle-extra-list {
  grid-column: 1 / -1;
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.battle-extra {
  min-height: 30px;
  max-width: 100%;
  border: 1px solid #e7c899;
  border-radius: 8px;
  padding: 0 9px;
  background: #fff2de;
  color: var(--warm-muted);
  font-size: 13px;
  font-weight: 800;
}

.battle-extra.revealed {
  border-color: #cfe1bf;
  background: #ecf6e6;
  color: #2f6b38;
}

.battle-extra:disabled {
  opacity: 1;
}

.me-zone {
  display: grid;
  grid-template-columns: 1fr 116px;
  gap: 10px;
  margin-top: 16px;
}

.my-general {
  overflow: hidden;
  border: 1px solid var(--warm-line);
  border-radius: 8px;
  padding: 0;
  color: var(--warm-text);
  font-weight: 900;
  min-height: 104px;
}

.my-general img {
  display: block;
  width: 100%;
  height: 78px;
  object-fit: cover;
}

.my-general span {
  display: block;
  padding: 6px;
  line-height: 1.15;
}

.extra-draw {
  grid-column: 1 / -1;
  display: grid;
  grid-template-columns: minmax(0, 1fr) 104px;
  gap: 8px;
  padding: 10px;
}

.faction-select {
  min-height: 44px;
}

.draw-button {
  min-height: 44px;
  padding: 0 10px;
}

.my-extra-grid {
  grid-column: 1 / -1;
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 10px;
}

.extra-card {
  overflow: hidden;
  background: #fffaf2;
}

.extra-card.revealed {
  border-color: #bdd8aa;
}

.extra-card-image {
  display: block;
  width: 100%;
  border: 0;
  padding: 0;
  background: #fff0d7;
  cursor: zoom-in;
}

.extra-card-image img {
  display: block;
  width: 100%;
  aspect-ratio: 3 / 3.5;
  object-fit: cover;
}

.extra-card-body {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  gap: 6px;
  align-items: center;
  padding: 8px;
}

.extra-card-body strong {
  min-width: 0;
  overflow: hidden;
  font-size: 15px;
  line-height: 1.2;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.extra-actions {
  display: grid;
  grid-template-columns: 1fr 42px;
  gap: 6px;
  padding: 0 8px 8px;
}

.extra-actions:has(.extra-action:only-child) {
  grid-template-columns: 1fr;
}

.extra-action {
  min-height: 34px;
  border: 1px solid #e4c491;
  border-radius: 8px;
  background: #ffe8c4;
  color: var(--warm-primary-strong);
  font-size: 13px;
  font-weight: 900;
}

.extra-action.danger {
  border-color: #e5b3a9;
  background: #f4d2c9;
  color: #923622;
}

.extra-action:disabled {
  opacity: 0.55;
}

.death-button {
  grid-column: 1 / -1;
  width: 100%;
}

.reveal-button {
  grid-column: 1 / -1;
  width: 100%;
}
</style>
