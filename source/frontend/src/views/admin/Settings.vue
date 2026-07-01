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
  </section>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElSwitch } from 'element-plus'
import { getSettings, updateSettings } from '../../api/admin'

const loading = ref(false)
const saving = ref(false)
const form = reactive({
  manualPickEnabled: false,
  crownPrinceEnabled: false
})

onMounted(load)

async function load() {
  loading.value = true
  try {
    const settings = await getSettings()
    form.manualPickEnabled = Boolean(settings.manualPickEnabled)
    form.crownPrinceEnabled = Boolean(settings.crownPrinceEnabled)
  } finally {
    loading.value = false
  }
}

async function save() {
  saving.value = true
  try {
    const settings = await updateSettings({
      manualPickEnabled: form.manualPickEnabled,
      crownPrinceEnabled: form.crownPrinceEnabled
    })
    form.manualPickEnabled = Boolean(settings.manualPickEnabled)
    form.crownPrinceEnabled = Boolean(settings.crownPrinceEnabled)
    ElMessage.success('已保存')
  } catch (error) {
    await load()
    ElMessage.error(error.message)
  } finally {
    saving.value = false
  }
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

@media (max-width: 640px) {
  .settings-panel {
    align-items: flex-start;
    flex-direction: column;
  }

  .settings-switch {
    justify-content: flex-start;
  }
}
</style>
