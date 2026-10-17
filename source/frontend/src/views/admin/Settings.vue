<template>
  <section class="admin-section">
    <header class="admin-head">
      <div>
        <h1>系统设置</h1>
        <p class="muted">控制临时规则开关，修改后立即生效。</p>
      </div>
    </header>

    <div class="settings-panel">
      <div>
        <strong>非主公点将</strong>
        <p class="muted">开启后，选将阶段的非主身份玩家可以搜索未被占用的武将并选择。</p>
      </div>
      <el-switch
        class="settings-switch"
        v-model="form.manualPickEnabled"
        :loading="loading || saving"
        active-text="开启"
        inactive-text="关闭"
        @change="save"
      />
    </div>

    <div class="settings-panel">
      <div>
        <strong>主公立储</strong>
        <p class="muted">开启后，主身份玩家可在对局中给一名玩家挂上全场可见的储君标记，并参与阵亡结算。</p>
      </div>
      <el-switch
        class="settings-switch"
        v-model="form.crownPrinceEnabled"
        :loading="loading || saving"
        active-text="开启"
        inactive-text="关闭"
        @change="save"
      />
    </div>

    <div class="settings-panel">
      <div>
        <strong>选将换将</strong>
        <p class="muted">开启后，选将阶段未锁定的玩家可以把盲选池中的武将换成共享池里的随机武将。</p>
      </div>
      <el-switch
        class="settings-switch"
        v-model="form.generalSwapEnabled"
        :loading="loading || saving"
        active-text="开启"
        inactive-text="关闭"
        @change="save"
      />
    </div>

    <div class="settings-panel faction-settings-panel">
      <div>
        <strong>势力管理</strong>
        <p class="muted">武将编辑和对局抽将仅显示这里配置的势力；删除不会修改已有武将的数据。</p>
      </div>
      <div class="faction-settings-controls">
        <div class="faction-tags">
          <el-tag v-for="faction in form.factions" :key="faction" closable :disable-transitions="false" @close="removeFaction(faction)">
            {{ factionLabel(faction) }}
          </el-tag>
          <span v-if="!form.factions.length" class="muted">尚未配置势力</span>
        </div>
        <div class="faction-add-row">
          <el-input v-model.trim="newFaction" maxlength="20" placeholder="输入势力名称" :disabled="loading || saving" @keyup.enter="addFaction" />
          <el-button type="primary" :loading="saving" :disabled="loading" @click="addFaction">添加</el-button>
        </div>
      </div>
    </div>
  </section>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElButton, ElInput, ElMessage, ElSwitch, ElTag } from 'element-plus'
import { getSettings, updateSettings } from '../../api/admin'

const loading = ref(false)
const saving = ref(false)
const form = reactive({
  manualPickEnabled: false,
  crownPrinceEnabled: false,
  generalSwapEnabled: false,
  factions: []
})
const newFaction = ref('')
const legacyFactionLabels = {
  WEI: '魏',
  SHU: '蜀',
  WU: '吴',
  QUN: '群',
  JIN: '晋',
  GOD: '神'
}

onMounted(load)

async function load() {
  loading.value = true
  try {
    const settings = await getSettings()
    form.manualPickEnabled = Boolean(settings.manualPickEnabled)
    form.crownPrinceEnabled = Boolean(settings.crownPrinceEnabled)
    form.generalSwapEnabled = Boolean(settings.generalSwapEnabled)
    form.factions = Array.isArray(settings.factions) ? settings.factions : []
  } finally {
    loading.value = false
  }
}

async function save() {
  saving.value = true
  try {
    const settings = await updateSettings({
      manualPickEnabled: form.manualPickEnabled,
      crownPrinceEnabled: form.crownPrinceEnabled,
      generalSwapEnabled: form.generalSwapEnabled
    })
    form.manualPickEnabled = Boolean(settings.manualPickEnabled)
    form.crownPrinceEnabled = Boolean(settings.crownPrinceEnabled)
    form.generalSwapEnabled = Boolean(settings.generalSwapEnabled)
    form.factions = Array.isArray(settings.factions) ? settings.factions : []
    ElMessage.success('已保存')
  } catch (error) {
    await load()
    ElMessage.error(error.message)
  } finally {
    saving.value = false
  }
}

async function addFaction() {
  const faction = newFaction.value.trim()
  if (!faction) {
    ElMessage.warning('请输入势力名称')
    return
  }
  if (form.factions.some((item) => normalizeFaction(item) === normalizeFaction(faction))) {
    ElMessage.warning('该势力已存在')
    return
  }
  await saveFactions([...form.factions, faction])
  newFaction.value = ''
}

async function removeFaction(faction) {
  await saveFactions(form.factions.filter((item) => item !== faction))
}

async function saveFactions(factions) {
  saving.value = true
  try {
    const settings = await updateSettings({ factions })
    form.factions = Array.isArray(settings.factions) ? settings.factions : []
    ElMessage.success('势力设置已保存')
  } catch (error) {
    await load()
    ElMessage.error(error.message)
  } finally {
    saving.value = false
  }
}

function factionLabel(value) {
  return legacyFactionLabels[value] || value
}

function normalizeFaction(value) {
  const text = String(value).trim().toUpperCase()
  return Object.entries(legacyFactionLabels).find(([, label]) => label === text)?.[0] || text
}
</script>

<style scoped>
.admin-section {
  display: grid;
  gap: 16px;
}

.admin-head h1 {
  margin: 0 0 6px;
  font-size: 28px;
  letter-spacing: 0;
}

.settings-panel {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 18px;
  max-width: 720px;
  padding: 18px;
  border: 1px solid var(--warm-line);
  border-radius: 8px;
  background: #fffaf2;
}

.settings-panel > div {
  min-width: 0;
}

.settings-switch {
  flex: 0 0 auto;
  min-width: 136px;
  justify-content: flex-end;
}

.settings-switch :deep(.el-switch__label),
.settings-switch :deep(.el-switch__label span) {
  white-space: nowrap;
  word-break: keep-all;
}

.settings-panel strong {
  color: var(--warm-primary-strong);
  font-size: 18px;
}

.settings-panel p {
  margin: 6px 0 0;
}

.faction-settings-panel {
  align-items: flex-start;
}

.faction-settings-controls {
  display: grid;
  flex: 0 1 360px;
  gap: 10px;
  width: 100%;
}

.faction-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  min-height: 32px;
  align-items: center;
}

.faction-add-row {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  gap: 8px;
}

@media (max-width: 640px) {
  .settings-panel {
    align-items: flex-start;
    flex-direction: column;
  }

  .settings-switch {
    justify-content: flex-start;
  }

  .faction-settings-controls {
    flex-basis: auto;
  }
}
</style>
