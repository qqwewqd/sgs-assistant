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
          <div class="battle-vitals">
            <div class="mini-hp">
              <template v-if="showHpIcons(player)">
                <img
                  v-for="index in hpIconCount(player)"
                  :key="`hp-${player.userId}-${index}`"
                  :src="index <= Number(player.currentHp || 0) ? hpFullIcon : hpEmptyIcon"
                  alt=""
                />
              </template>
              <span>{{ vitalsText(player, 'hp') }}</span>
            </div>
            <div v-if="showArmor(player)" class="mini-armor">
              <img :src="armorIcon" alt="" />
              <span>{{ player.currentArmor }}</span>
            </div>
          </div>
          <div class="identity-chip">
            {{ player.identityVisible ? player.identity : '?' }}
          </div>

          <div v-if="hasCardStatus(player)" class="card-status-row">
            <span v-if="player.chained" class="status-chip">横置</span>
            <span v-if="player.turnedOver" class="status-chip">翻面</span>
            <span v-for="marker in player.markers" :key="`${player.userId}-${marker.name}`" class="card-marker-chip">
              <span>{{ marker.name }} x{{ marker.count }}</span>
              <button type="button" :disabled="markerBusy" @click="adjustMarker(player, marker, -1)">-</button>
              <button type="button" :disabled="markerBusy" @click="adjustMarker(player, marker, 1)">+</button>
              <button type="button" class="danger" :disabled="markerBusy" @click="removeMarker(player, marker)">×</button>
            </span>
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

        <div v-if="needsVitalsSetup" class="vitals-setup panel">
          <strong>设置本局血甲</strong>
          <p>该武将没有预设血量和护甲，请先填写本局数值。</p>
          <div class="vitals-form">
            <label>
              <span>初始血量</span>
              <input v-model.number="vitalsForm.currentHp" class="field" type="number" min="0" max="99" />
            </label>
            <label>
              <span>血量上限</span>
              <input v-model.number="vitalsForm.maxHp" class="field" type="number" min="1" max="99" />
            </label>
            <label>
              <span>初始护甲</span>
              <input v-model.number="vitalsForm.currentArmor" class="field" type="number" min="0" max="99" />
            </label>
          </div>
          <button class="warm-button" :disabled="vitalsBusy" @click="submitVitalsForm">
            {{ vitalsBusy ? '保存中' : '确认血甲' }}
          </button>
        </div>

        <div v-else class="my-vitals panel">
          <div class="hp-control-row">
            <div class="my-hp-icons">
              <template v-if="showHpIcons(room.me)">
                <img
                  v-for="index in hpIconCount(room.me)"
                  :key="`my-hp-${index}`"
                  :src="index <= Number(room.me.currentHp || 0) ? hpFullIcon : hpEmptyIcon"
                  alt=""
                />
              </template>
              <span v-else class="hp-count-only">{{ room.me.currentHp }}/{{ room.me.maxHp }}</span>
            </div>
            <div class="hp-button-stack">
              <div class="vital-control hp">
                <button type="button" :disabled="vitalsBusy" @click="adjustVitals('hp', -1)">-</button>
                <strong>{{ vitalsText(room.me, 'hp') }}</strong>
                <button type="button" :disabled="vitalsBusy" @click="adjustVitals('hp', 1)">+</button>
              </div>
              <div class="vital-control max-hp">
                <button type="button" :disabled="vitalsBusy" @click="adjustVitals('maxHp', -1)">-</button>
                <strong>上限 {{ room.me.maxHp }}</strong>
                <button type="button" :disabled="vitalsBusy" @click="adjustVitals('maxHp', 1)">+</button>
              </div>
            </div>
          </div>
          <div v-if="showArmor(room.me)" class="armor-control-row">
            <span class="armor-badge">
              <img :src="armorIcon" alt="" />
              <em>{{ room.me.currentArmor }}</em>
            </span>
            <div class="vital-control armor">
              <button type="button" :disabled="vitalsBusy" @click="adjustVitals('armor', -1)">-</button>
              <strong>护甲 {{ room.me.currentArmor }}</strong>
              <button type="button" :disabled="vitalsBusy" @click="adjustVitals('armor', 1)">+</button>
            </div>
          </div>
          <button v-else type="button" class="add-armor-button" :disabled="vitalsBusy" @click="adjustVitals('armor', 1)">
            + 护甲
          </button>
        </div>

        <div class="marker-panel panel">
          <div class="status-split">
            <div class="status-column">
              <div class="status-title-row">
                <strong>状态</strong>
              </div>
              <div class="status-controls">
                <button
                  type="button"
                  :class="['status-toggle', room.me.chained && 'active']"
                  :disabled="statusBusy"
                  @click="toggleOwnStatus('chained')"
                >
                  横置
                </button>
                <button
                  type="button"
                  :class="['status-toggle', room.me.turnedOver && 'active']"
                  :disabled="statusBusy"
                  @click="toggleOwnStatus('turnedOver')"
                >
                  翻面
                </button>
              </div>
            </div>
            <div class="status-column marker-column">
              <div class="marker-title-row">
                <strong>标记</strong>
                <button type="button" class="marker-open-small" :disabled="markerBusy" @click="openMarkerPanel(room.me)">添加</button>
              </div>
              <div class="marker-controls">
                <div v-if="room.me.markers?.length" class="marker-list">
                  <div v-for="marker in room.me.markers" :key="`me-${marker.name}`" class="marker-chip">
                    <span>{{ marker.name }} x{{ marker.count }}</span>
                    <button type="button" :disabled="markerBusy" @click="adjustMarker(room.me, marker, -1)">-</button>
                    <button type="button" :disabled="markerBusy" @click="adjustMarker(room.me, marker, 1)">+</button>
                    <button type="button" class="danger" :disabled="markerBusy" @click="removeMarker(room.me, marker)">×</button>
                  </div>
                </div>
                <p v-else class="marker-empty">暂无</p>
              </div>
            </div>
          </div>
        </div>

        <div v-if="markerPanelOpen" class="marker-editor panel">
          <select v-model="markerForm.targetUserId" class="field">
            <option :value="MARKER_TARGET_ALL">全部其他玩家</option>
            <option v-for="player in allPlayers" :key="player.userId" :value="player.userId">
              {{ player.username }}
            </option>
          </select>
          <input v-model.trim="markerForm.name" class="field" maxlength="12" placeholder="标记名称" />
          <input v-model.number="markerForm.count" class="field" type="number" min="0" max="999" placeholder="数量" />
          <button class="warm-button" :disabled="markerBusy" @click="submitMarker">
            {{ markerBusy ? '保存中' : '保存标记' }}
          </button>
          <button type="button" class="marker-cancel" :disabled="markerBusy" @click="markerPanelOpen = false">取消</button>
        </div>

        <div class="random-panel panel">
          <div class="random-row">
            <strong>随机玩家</strong>
            <input v-model.number="randomPlayerCount" class="field" type="number" min="1" :max="alivePlayers.length || 1" />
            <button type="button" class="random-button" @click="drawRandomPlayers">抽取</button>
          </div>
          <div class="random-row range">
            <strong>随机数字</strong>
            <input v-model.number="randomRangeStart" class="field" type="number" />
            <span>-</span>
            <input v-model.number="randomRangeEnd" class="field" type="number" />
            <input v-model.number="randomRangeCount" class="field" type="number" min="1" />
            <button type="button" class="random-button" @click="drawRandomNumbers">抽取</button>
          </div>
          <p v-if="randomResult" class="random-result">{{ randomResult }}</p>
        </div>

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
import { computed, onMounted, reactive, ref } from 'vue'
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
  revealGeneral,
  updateMarker,
  updateStatus,
  updateVitals
} from '../../api/room'
import { useRoomSocket } from '../../composables/useWebSocket'
import { useGameStore } from '../../store/game'
import { assetUrl } from '../../utils/pathHelper'
import hpFullIcon from '../../assets/vitals/勾玉.png'
import hpEmptyIcon from '../../assets/vitals/勾玉空.png'
import armorIcon from '../../assets/vitals/护甲.png'

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
const HP_ICON_LIMIT = 8
const MARKER_TARGET_ALL = 'ALL_OTHERS'
const room = computed(() => game.room)
const holdingIdentity = ref(false)
const extraFaction = ref('')
const extraBusy = ref(false)
const vitalsBusy = ref(false)
const markerBusy = ref(false)
const statusBusy = ref(false)
const markerPanelOpen = ref(false)
const modalImage = ref(null)
const dissolving = ref(false)
const randomPlayerCount = ref(null)
const randomRangeStart = ref(null)
const randomRangeEnd = ref(null)
const randomRangeCount = ref(null)
const randomResult = ref('')
const vitalsForm = reactive({
  currentHp: null,
  maxHp: null,
  currentArmor: null
})
const markerForm = reactive({
  targetUserId: null,
  name: '',
  count: 1
})
const modalVisible = computed({
  get: () => Boolean(modalImage.value),
  set: (value) => {
    if (!value) modalImage.value = null
  }
})

const otherPlayers = computed(() => room.value?.players?.filter((player) => player.userId !== room.value?.me?.userId) || [])
const allPlayers = computed(() => room.value?.players || [])
const alivePlayers = computed(() => allPlayers.value.filter((player) => !player.dead))
const myExtraGenerals = computed(() => room.value?.me?.extraGenerals || [])
const isLord = computed(() => room.value?.me?.identity === '主公')
const identityShown = computed(() => isLord.value || holdingIdentity.value)
const canRevealGeneral = computed(() => Boolean(room.value?.me?.selectedGeneral?.startsHidden && !room.value?.me?.generalRevealed))
const needsVitalsSetup = computed(() => Boolean(room.value?.me?.selectedGeneral && !hasVitals(room.value.me)))

onMounted(loadRoom)
useRoomSocket(roomCode, loadRoom)

async function loadRoom() {
  try {
    const data = await getRoom(roomCode)
    game.setRoom(data)
    syncVitalsForm(data.me)
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

async function submitVitalsForm() {
  if (vitalsBusy.value) return
  const payload = normalizeVitalsPayload(vitalsForm)
  if (!payload) return
  vitalsBusy.value = true
  try {
    const data = await updateVitals(roomCode, payload)
    game.setRoom(data)
    syncVitalsForm(data.me)
    showToast('血甲已设置')
  } catch (error) {
    showToast(error.message)
  } finally {
    vitalsBusy.value = false
  }
}

async function adjustVitals(type, delta) {
  if (vitalsBusy.value || !hasVitals(room.value?.me)) return
  const me = room.value.me
  const payload = {
    currentHp: me.currentHp,
    maxHp: me.maxHp,
    currentArmor: me.currentArmor
  }
  if (type === 'hp') payload.currentHp = clamp(payload.currentHp + delta, 0, payload.maxHp)
  else if (type === 'maxHp') {
    payload.maxHp = clamp(payload.maxHp + delta, 1, 99)
    payload.currentHp = Math.min(payload.currentHp, payload.maxHp)
  }
  else payload.currentArmor = clamp(payload.currentArmor + delta, 0, 99)
  vitalsBusy.value = true
  try {
    const data = await updateVitals(roomCode, payload)
    game.setRoom(data)
    syncVitalsForm(data.me)
  } catch (error) {
    showToast(error.message)
  } finally {
    vitalsBusy.value = false
  }
}

function openMarkerPanel(target) {
  markerForm.targetUserId = target?.userId ?? room.value?.me?.userId ?? null
  markerForm.name = ''
  markerForm.count = 1
  markerPanelOpen.value = true
}

async function submitMarker() {
  const payload = normalizeMarkerPayload(markerForm)
  if (!payload) return
  if (payload.targetUserId === MARKER_TARGET_ALL) {
    await saveMarkerForAllOthers(payload)
  } else {
    await saveMarker(payload)
  }
  markerPanelOpen.value = false
}

async function adjustMarker(player, marker, delta) {
  const nextCount = clamp(Number(marker.count || 0) + delta, 0, 999)
  await saveMarker({
    targetUserId: player.userId,
    markerName: marker.name,
    markerCount: nextCount
  })
}

async function removeMarker(player, marker) {
  await saveMarker({
    targetUserId: player.userId,
    markerName: marker.name,
    markerCount: 0
  })
}

async function saveMarker(payload) {
  if (markerBusy.value) return
  markerBusy.value = true
  try {
    const data = await updateMarker(roomCode, payload)
    game.setRoom(data)
  } catch (error) {
    showToast(error.message)
  } finally {
    markerBusy.value = false
  }
}

async function saveMarkerForAllOthers(payload) {
  const targets = allPlayers.value.filter((player) => player.userId !== room.value?.me?.userId)
  if (!targets.length) {
    showToast('没有其他玩家')
    return
  }
  if (markerBusy.value) return
  markerBusy.value = true
  try {
    let latestRoom = null
    for (const player of targets) {
      latestRoom = await updateMarker(roomCode, {
        targetUserId: player.userId,
        markerName: payload.markerName,
        markerCount: payload.markerCount
      })
    }
    if (latestRoom) {
      game.setRoom(latestRoom)
    }
  } catch (error) {
    showToast(error.message)
  } finally {
    markerBusy.value = false
  }
}

async function toggleOwnStatus(type) {
  if (statusBusy.value || !room.value?.me) return
  const payload = {}
  payload[type] = !room.value.me[type]
  statusBusy.value = true
  try {
    const data = await updateStatus(roomCode, payload)
    game.setRoom(data)
  } catch (error) {
    showToast(error.message)
  } finally {
    statusBusy.value = false
  }
}

function drawRandomPlayers() {
  const count = toInteger(randomPlayerCount.value)
  if (!count || count < 1) {
    showToast('请填写抽取人数')
    return
  }
  if (!alivePlayers.value.length) {
    showToast('没有存活玩家')
    return
  }
  if (count > alivePlayers.value.length) {
    showToast('抽取人数不能超过存活玩家数')
    return
  }
  const picked = pickRandomItems(alivePlayers.value, count).map((player) => player.username)
  randomResult.value = `随机玩家：${picked.join('、')}`
}

function drawRandomNumbers() {
  const start = toInteger(randomRangeStart.value)
  const end = toInteger(randomRangeEnd.value)
  const count = toInteger(randomRangeCount.value)
  if (start === null || end === null || count === null || count < 1) {
    showToast('请完整填写数字范围和数量')
    return
  }
  const min = Math.min(start, end)
  const max = Math.max(start, end)
  const numbers = Array.from({ length: max - min + 1 }, (_, index) => min + index)
  if (count > numbers.length) {
    showToast('抽取数量不能超过范围数量')
    return
  }
  const picked = pickRandomItems(numbers, count).sort((left, right) => left - right)
  randomResult.value = `随机数字：${picked.join('、')}`
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

function hasVitals(player) {
  return player?.currentHp !== null
    && player?.currentHp !== undefined
    && player?.maxHp !== null
    && player?.maxHp !== undefined
    && player?.currentArmor !== null
    && player?.currentArmor !== undefined
}

function vitalsText(player, type) {
  if (!hasVitals(player)) return type === 'hp' ? '血 ?/?' : '甲 ?/?'
  if (type === 'hp') return `血 ${player.currentHp}/${player.maxHp}`
  return `甲 ${player.currentArmor}`
}

function syncVitalsForm(me) {
  if (hasVitals(me)) {
    Object.assign(vitalsForm, {
      currentHp: me.currentHp,
      maxHp: me.maxHp,
      currentArmor: me.currentArmor
    })
    return
  }
  const general = me?.selectedGeneral
  Object.assign(vitalsForm, {
    currentHp: general?.initialHp ?? null,
    maxHp: general?.maxHp ?? null,
    currentArmor: general?.initialArmor ?? null
  })
}

function normalizeVitalsPayload(source) {
  const payload = {
    currentHp: toInteger(source.currentHp),
    maxHp: toInteger(source.maxHp),
    currentArmor: toInteger(source.currentArmor)
  }
  if ([payload.currentHp, payload.maxHp, payload.currentArmor].some((value) => value === null)) {
    showToast('请完整填写血量和护甲')
    return null
  }
  if (payload.maxHp < 1 || payload.maxHp > 99) {
    showToast('血量上限需在 1-99 之间')
    return null
  }
  if (payload.currentHp < 0 || payload.currentHp > payload.maxHp) {
    showToast('当前血量不能超过上限')
    return null
  }
  if (payload.currentArmor < 0 || payload.currentArmor > 99) {
    showToast('当前护甲需在 0-99 之间')
    return null
  }
  return payload
}

function normalizeMarkerPayload(source) {
  const markerName = String(source.name || '').trim()
  const markerCount = toInteger(source.count)
  const targetUserId = source.targetUserId === MARKER_TARGET_ALL ? MARKER_TARGET_ALL : toInteger(source.targetUserId)
  if (!source.targetUserId) {
    showToast('请选择玩家')
    return null
  }
  if (targetUserId === null) {
    showToast('请选择玩家')
    return null
  }
  if (!markerName) {
    showToast('请填写标记名称')
    return null
  }
  if (markerName.length > 12) {
    showToast('标记名称最多 12 个字')
    return null
  }
  if (markerCount === null || markerCount < 0 || markerCount > 999) {
    showToast('标记数量需在 0-999 之间')
    return null
  }
  return {
    targetUserId,
    markerName,
    markerCount
  }
}

function hpIconCount(player) {
  const maxHp = Number(player?.maxHp || 0)
  return Math.min(Math.max(maxHp, 1), HP_ICON_LIMIT)
}

function showHpIcons(player) {
  const maxHp = Number(player?.maxHp || 0)
  return hasVitals(player) && maxHp <= HP_ICON_LIMIT
}

function showArmor(player) {
  return hasVitals(player) && Number(player.currentArmor || 0) > 0
}

function hasCardStatus(player) {
  return Boolean(player?.chained || player?.turnedOver || player?.markers?.length)
}

function pickRandomItems(items, count) {
  const pool = [...items]
  for (let index = pool.length - 1; index > 0; index -= 1) {
    const swapIndex = Math.floor(Math.random() * (index + 1))
    ;[pool[index], pool[swapIndex]] = [pool[swapIndex], pool[index]]
  }
  return pool.slice(0, count)
}

function toInteger(value) {
  if (value === null || value === undefined || value === '') return null
  const number = Number(value)
  return Number.isInteger(number) ? number : null
}

function clamp(value, min, max) {
  return Math.min(max, Math.max(min, value))
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
  grid-template-columns: minmax(0, 1fr) 82px 54px;
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

.battle-vitals {
  display: grid;
  gap: 5px;
  color: #6b513c;
  font-size: 12px;
  font-weight: 900;
  text-align: center;
}

.mini-hp,
.mini-armor {
  display: flex;
  align-items: center;
  justify-content: center;
}

.mini-hp {
  flex-wrap: wrap;
  gap: 1px;
}

.mini-hp img {
  width: 14px;
  height: 14px;
  object-fit: contain;
}

.mini-hp span {
  flex: 0 0 100%;
  font-size: 11px;
}

.mini-armor {
  gap: 2px;
  min-height: 24px;
}

.mini-armor img {
  width: 24px;
  height: 24px;
  object-fit: contain;
}

.mini-armor span {
  min-width: 18px;
  color: #32415d;
  font-size: 14px;
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

.card-status-row {
  grid-column: 1 / -1;
  display: flex;
  flex-wrap: wrap;
  gap: 5px;
  min-width: 0;
}

.status-chip {
  display: inline-grid;
  place-items: center;
  min-height: 24px;
  max-width: 100%;
  border: 1px solid #d5bd88;
  border-radius: 8px;
  padding: 2px 8px;
  background: #fff7e8;
  color: #654425;
  font-size: 12px;
  font-weight: 900;
  line-height: 1;
}

.status-chip.marker {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.card-marker-chip {
  display: inline-grid;
  grid-template-columns: minmax(0, auto) 22px 22px 22px;
  gap: 3px;
  align-items: center;
  max-width: 100%;
  min-height: 26px;
  border: 1px solid #d5bd88;
  border-radius: 8px;
  padding: 2px 3px 2px 7px;
  background: #fff7e8;
  color: #654425;
  font-size: 12px;
  font-weight: 900;
}

.card-marker-chip span {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.card-marker-chip button {
  width: 22px;
  height: 22px;
  border: 1px solid #e0c28f;
  border-radius: 6px;
  background: #ffe6bd;
  color: #7b4f24;
  font-size: 12px;
  font-weight: 900;
}

.card-marker-chip button.danger {
  border-color: #e5b3a9;
  background: #f4d2c9;
  color: #923622;
}

.card-marker-chip button:disabled {
  opacity: 0.55;
}

.marker-inline-row {
  grid-column: 1 / -1;
  display: flex;
  align-items: center;
  gap: 6px;
  min-width: 0;
}

.marker-open-small {
  flex: 0 0 auto;
  min-height: 28px;
  border: 1px solid #d8ba86;
  border-radius: 8px;
  padding: 0 8px;
  background: #fff4df;
  color: #7b4f24;
  font-size: 12px;
  font-weight: 900;
}

.marker-open-small:disabled {
  opacity: 0.55;
}

.status-toggle {
  flex: 0 0 auto;
  min-height: 28px;
  border: 1px solid #d8ba86;
  border-radius: 8px;
  padding: 0 8px;
  background: #fffaf1;
  color: var(--warm-muted);
  font-size: 12px;
  font-weight: 900;
}

.status-toggle.active {
  border-color: #d39142;
  background: #ffe1ad;
  color: var(--warm-primary-strong);
}

.status-toggle:disabled {
  opacity: 0.55;
}

.marker-list {
  min-width: 0;
  display: flex;
  flex-wrap: wrap;
  gap: 4px;
}

.marker-chip {
  display: inline-grid;
  grid-template-columns: minmax(0, auto) 24px 24px 24px;
  gap: 3px;
  align-items: center;
  max-width: 100%;
  min-height: 28px;
  border: 1px solid #d5bd88;
  border-radius: 8px;
  padding: 2px 3px 2px 7px;
  background: #fff7e8;
  color: #654425;
  font-size: 12px;
  font-weight: 900;
}

.marker-chip span {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.marker-chip button {
  width: 24px;
  height: 24px;
  border: 1px solid #e0c28f;
  border-radius: 6px;
  background: #ffe6bd;
  color: #7b4f24;
  font-size: 13px;
  font-weight: 900;
}

.marker-chip button.danger {
  width: 24px;
  border-color: #e5b3a9;
  background: #f4d2c9;
  color: #923622;
}

.marker-panel,
.marker-editor {
  grid-column: 1 / -1;
  padding: 10px;
}

.status-split {
  display: grid;
  grid-template-columns: minmax(96px, 0.8fr) minmax(0, 1.2fr);
  gap: 10px;
}

.status-column {
  min-width: 0;
  display: grid;
  align-content: start;
  gap: 7px;
}

.status-title-row,
.marker-title-row {
  display: flex;
  align-items: center;
  min-height: 34px;
  gap: 8px;
}

.status-title-row {
  justify-content: flex-start;
}

.marker-title-row {
  justify-content: space-between;
}

.status-title-row strong,
.marker-title-row strong {
  color: var(--warm-primary-strong);
  font-size: 15px;
  line-height: 1.2;
}

.status-controls,
.marker-controls {
  min-width: 0;
  display: flex;
  flex-wrap: wrap;
  gap: 5px;
  align-items: center;
}

.marker-column {
  border-left: 1px solid #ead8ba;
  padding-left: 10px;
}

.marker-empty {
  margin: 0;
  min-height: 28px;
  display: grid;
  place-items: center;
  color: var(--warm-muted);
  font-size: 12px;
  font-weight: 800;
}

.marker-editor {
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(0, 1fr);
  gap: 8px;
}

.marker-editor .field {
  min-width: 0;
}

.marker-editor .warm-button,
.marker-cancel {
  min-height: 40px;
}

.marker-cancel {
  border: 1px solid #d8ba86;
  border-radius: 8px;
  background: #fffaf1;
  color: var(--warm-muted);
  font-weight: 900;
}

.random-panel {
  grid-column: 1 / -1;
  display: grid;
  gap: 8px;
  padding: 10px;
}

.random-row {
  display: grid;
  grid-template-columns: 74px minmax(0, 1fr) 72px;
  gap: 6px;
  align-items: center;
}

.random-row.range {
  grid-template-columns: 74px minmax(0, 1fr) 12px minmax(0, 1fr) minmax(0, 1fr) 72px;
}

.random-row strong {
  color: var(--warm-primary-strong);
  font-size: 14px;
  white-space: nowrap;
}

.random-row span {
  color: var(--warm-muted);
  text-align: center;
  font-weight: 900;
}

.random-row .field {
  min-width: 0;
  min-height: 38px;
}

.random-button {
  min-height: 38px;
  border: 1px solid #d8ba86;
  border-radius: 8px;
  background: #ffe4b8;
  color: var(--warm-primary-strong);
  font-size: 13px;
  font-weight: 900;
}

.random-result {
  margin: 0;
  border: 1px solid #d9c29a;
  border-radius: 8px;
  padding: 8px 10px;
  background: #fff7e8;
  color: #654425;
  font-size: 14px;
  font-weight: 900;
  line-height: 1.4;
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

.vitals-setup,
.my-vitals {
  grid-column: 1 / -1;
  padding: 12px;
}

.vitals-setup {
  display: grid;
  gap: 10px;
}

.vitals-setup strong {
  color: var(--warm-primary-strong);
  font-size: 18px;
}

.vitals-setup p {
  margin: 0;
  color: var(--warm-muted);
  font-size: 13px;
  font-weight: 700;
}

.vitals-form {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 8px;
}

.vitals-form label {
  display: grid;
  gap: 4px;
  color: var(--warm-muted);
  font-size: 12px;
  font-weight: 900;
}

.vitals-form input {
  min-width: 0;
}

.my-vitals {
  display: grid;
  grid-template-columns: 1fr;
  gap: 10px;
}

.hp-control-row,
.armor-control-row {
  display: grid;
  grid-template-columns: minmax(96px, 122px) minmax(0, 1fr);
  gap: 8px;
  align-items: center;
}

.vital-control {
  display: grid;
  grid-template-columns: minmax(82px, 1fr) 38px minmax(0, 88px) 38px;
  gap: 8px;
  align-items: center;
}

.hp-control-row .vital-control {
  gap: 6px;
}

.hp-button-stack {
  min-width: 0;
  display: grid;
  gap: 8px;
}

.hp-button-stack .vital-control {
  grid-template-columns: 42px minmax(68px, 1fr) 42px;
}

.hp-button-stack .max-hp strong {
  color: #6b513c;
}

.armor-control-row .vital-control {
  grid-template-columns: 42px minmax(68px, 1fr) 42px;
  gap: 6px;
}

.vital-control button {
  min-height: 38px;
  border: 1px solid #e1bf86;
  border-radius: 8px;
  background: #ffe4b8;
  color: var(--warm-primary-strong);
  font-size: 18px;
  font-weight: 900;
}

.vital-control button:disabled {
  opacity: 0.55;
}

.vital-control strong {
  min-width: 0;
  color: #5d4636;
  font-size: 14px;
  text-align: center;
  white-space: nowrap;
}

.my-hp-icons {
  min-width: 96px;
  display: flex;
  flex-wrap: wrap;
  gap: 3px;
  align-items: center;
  align-content: center;
}

.my-hp-icons img {
  width: 24px;
  height: 24px;
  object-fit: contain;
}

.hp-count-only {
  display: grid;
  min-width: 54px;
  min-height: 30px;
  place-items: center;
  border: 1px solid #e2bf86;
  border-radius: 8px;
  background: #fff4df;
  color: #7b4f24;
  font-size: 14px;
  font-weight: 900;
}

.armor-badge {
  display: inline-grid;
  grid-template-columns: 44px auto;
  gap: 4px;
  align-items: center;
  justify-content: start;
  min-width: 96px;
}

.armor-badge img {
  width: 44px;
  height: 44px;
  object-fit: contain;
}

.armor-badge em {
  color: #32415d;
  font-size: 22px;
  font-style: normal;
  font-weight: 900;
}

.add-armor-button {
  min-height: 40px;
  border: 1px solid #c5cfe2;
  border-radius: 8px;
  background: #edf2fb;
  color: #32415d;
  font-weight: 900;
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
