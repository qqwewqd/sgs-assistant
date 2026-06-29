<template>
  <section class="admin-section">
    <header class="admin-head">
      <div>
        <h1>身份模式</h1>
      </div>
      <el-button type="primary" @click="openCreate">新建模式</el-button>
    </header>

    <el-table :data="rows" :empty-text="loading ? '加载中' : '暂无身份模式'" border>
      <el-table-column prop="name" label="模式" min-width="160" />
      <el-table-column label="状态" width="100">
        <template #default="{ row }">
          <el-tag :type="row.enabled ? 'success' : 'info'">{{ row.enabled ? '启用' : '停用' }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="人数" width="130">
        <template #default="{ row }">
          <el-select
            v-if="row.playerCounts.length"
            v-model="overviewCounts[row.id]"
            class="table-count-select"
            size="small"
          >
            <el-option v-for="count in row.playerCounts" :key="count" :label="`${count} 人`" :value="count" />
          </el-select>
          <span v-else>-</span>
        </template>
      </el-table-column>
      <el-table-column label="规则" min-width="320">
        <template #default="{ row }">
          <span class="rule-summary">{{ countSummary(row, overviewCount(row)) }}</span>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="170" fixed="right">
        <template #default="{ row }">
          <el-button size="small" @click="openEdit(row)">编辑</el-button>
          <el-button size="small" type="danger" :disabled="row.builtin" @click="remove(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-dialog v-model="dialogVisible" :title="form.id ? '编辑身份模式' : '新建身份模式'" width="min(92vw, 900px)">
      <el-form label-width="92px">
        <el-form-item label="模式名称">
          <el-input v-model.trim="form.name" maxlength="50" />
        </el-form-item>
        <el-form-item label="启用">
          <el-switch v-model="form.enabled" />
        </el-form-item>
      </el-form>

      <div class="rule-toolbar">
        <el-select v-model="activePlayerCount" class="count-select">
          <el-option v-for="count in playerCountOptions" :key="count" :label="`${count} 人`" :value="count" />
        </el-select>
        <span class="rule-total">身份数量 {{ selectedRuleQuantity }} / {{ activePlayerCount }}</span>
        <el-button @click="addRule">添加规则</el-button>
      </div>

      <el-table
        :data="selectedRules"
        class="rule-table"
        border
        max-height="440"
        :empty-text="`${activePlayerCount} 人局暂无规则`"
      >
        <el-table-column label="身份" width="238">
          <template #default="{ row }">
            <el-input v-model.trim="row.identityName" class="identity-input" maxlength="20" size="small" />
          </template>
        </el-table-column>
        <el-table-column label="数量" width="72">
          <template #default="{ row }">
            <el-input-number v-model="row.quantity" class="compact-number" :min="1" :max="10" :controls="false" size="small" />
          </template>
        </el-table-column>
        <el-table-column label="选将明置" width="88" align="center">
          <template #default="{ row }">
            <el-switch v-model="row.isLeader" />
          </template>
        </el-table-column>
        <el-table-column label="身份公开" width="88" align="center">
          <template #default="{ row }">
            <el-switch v-model="row.identityVisible" />
          </template>
        </el-table-column>
        <el-table-column label="可选主公池" width="96" align="center">
          <template #default="{ row }">
            <el-switch v-model="row.allowLordGeneral" />
          </template>
        </el-table-column>
        <el-table-column label="初始血增益" width="98">
          <template #default="{ row }">
            <el-input-number v-model="row.initialHpBonus" class="compact-number" :min="-20" :max="20" :controls="false" size="small" />
          </template>
        </el-table-column>
        <el-table-column label="血上限增益" width="106">
          <template #default="{ row }">
            <el-input-number v-model="row.maxHpBonus" class="compact-number" :min="-20" :max="20" :controls="false" size="small" />
          </template>
        </el-table-column>
        <el-table-column label="操作" width="82">
          <template #default="{ row }">
            <el-button size="small" type="danger" @click="removeRule(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>

      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="save">保存</el-button>
      </template>
    </el-dialog>
  </section>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import {
  ElButton,
  ElDialog,
  ElForm,
  ElFormItem,
  ElInput,
  ElInputNumber,
  ElMessage,
  ElMessageBox,
  ElOption,
  ElSelect,
  ElSwitch,
  ElTable,
  ElTableColumn,
  ElTag
} from 'element-plus'
import { createIdentityMode, deleteIdentityMode, listIdentityModes, updateIdentityMode } from '../../api/admin'

const rows = ref([])
const loading = ref(false)
const saving = ref(false)
const dialogVisible = ref(false)
const activePlayerCount = ref(7)
const playerCountOptions = Array.from({ length: 9 }, (_, index) => index + 2)
const overviewCounts = reactive({})
const form = reactive(emptyForm())
const selectedRules = computed(() => form.rules
  .filter((rule) => rule.playerCount === activePlayerCount.value)
  .sort((left, right) => left.sortOrder - right.sortOrder))
const selectedRuleQuantity = computed(() => selectedRules.value.reduce((total, rule) => total + Number(rule.quantity || 0), 0))

onMounted(load)

async function load() {
  loading.value = true
  try {
    rows.value = await listIdentityModes()
    syncOverviewCounts()
  } finally {
    loading.value = false
  }
}

function openCreate() {
  Object.assign(form, emptyForm())
  activePlayerCount.value = 7
  dialogVisible.value = true
}

function openEdit(row) {
  const rules = row.rules.map(copyRule)
  Object.assign(form, {
    id: row.id,
    name: row.name,
    enabled: row.enabled,
    rules
  })
  activePlayerCount.value = row.playerCounts[0] || 7
  dialogVisible.value = true
}

async function save() {
  if (!form.name.trim()) {
    ElMessage.warning('请填写模式名称')
    return
  }
  if (!form.rules.length) {
    ElMessage.warning('请至少添加一条规则')
    return
  }
  saving.value = true
  try {
    const sortedRules = [...form.rules].sort((left, right) => left.playerCount - right.playerCount || left.sortOrder - right.sortOrder)
    const payload = {
      name: form.name.trim(),
      enabled: form.enabled,
      rules: sortedRules.map((rule, index) => ({ ...rule, sortOrder: index + 1 }))
    }
    if (form.id) await updateIdentityMode(form.id, payload)
    else await createIdentityMode(payload)
    ElMessage.success('已保存')
    dialogVisible.value = false
    await load()
  } catch (error) {
    ElMessage.error(error.message)
  } finally {
    saving.value = false
  }
}

async function remove(row) {
  await ElMessageBox.confirm(`删除身份模式 ${row.name}？`, '确认删除')
  await deleteIdentityMode(row.id)
  ElMessage.success('已删除')
  await load()
}

function addRule() {
  form.rules.push({
    playerCount: activePlayerCount.value,
    identityName: '',
    quantity: 1,
    isLeader: false,
    identityVisible: false,
    allowLordGeneral: false,
    initialHpBonus: 0,
    maxHpBonus: 0,
    sortOrder: nextSortOrder(activePlayerCount.value)
  })
}

function removeRule(rule) {
  const index = form.rules.indexOf(rule)
  if (index >= 0) form.rules.splice(index, 1)
}

function nextSortOrder(playerCount) {
  const orders = form.rules
    .filter((rule) => rule.playerCount === playerCount)
    .map((rule) => Number(rule.sortOrder || 0))
  return (Math.max(0, ...orders) || 0) + 1
}

function overviewCount(row) {
  return overviewCounts[row.id] || row.playerCounts[0] || null
}

function countSummary(row, count) {
  if (!count) return '-'
  const rules = row.rules.filter((rule) => rule.playerCount === count)
  if (!rules.length) return '暂无规则'
  return rules.map((rule) => `${rule.identityName}x${rule.quantity}`).join(' ')
}

function syncOverviewCounts() {
  const existingIds = new Set(rows.value.map((row) => String(row.id)))
  Object.keys(overviewCounts).forEach((id) => {
    if (!existingIds.has(id)) delete overviewCounts[id]
  })
  rows.value.forEach((row) => {
    if (!row.playerCounts.includes(overviewCounts[row.id])) {
      overviewCounts[row.id] = row.playerCounts[0] || null
    }
  })
}

function copyRule(rule) {
  return {
    playerCount: rule.playerCount,
    identityName: rule.identityName,
    quantity: rule.quantity,
    isLeader: Boolean(rule.isLeader),
    identityVisible: Boolean(rule.identityVisible),
    allowLordGeneral: Boolean(rule.allowLordGeneral),
    initialHpBonus: Number(rule.initialHpBonus || 0),
    maxHpBonus: Number(rule.maxHpBonus || 0),
    sortOrder: Number(rule.sortOrder || 0)
  }
}

function emptyForm() {
  return {
    id: null,
    name: '',
    enabled: true,
    rules: []
  }
}
</script>

<style scoped>
.admin-section {
  display: grid;
  gap: 16px;
}

.admin-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 14px;
}

.admin-head h1 {
  margin: 0;
  font-size: 28px;
  letter-spacing: 0;
}

.rule-summary {
  color: #6b513c;
  font-size: 13px;
  font-weight: 800;
  line-height: 1.5;
}

.rule-toolbar {
  display: flex;
  align-items: center;
  gap: 10px;
  margin: 4px 0 12px;
}

.count-select {
  width: 120px;
}

.table-count-select {
  width: 96px;
}

.rule-total {
  color: #6b513c;
  font-size: 13px;
  font-weight: 900;
}

.rule-table {
  width: 100%;
}

.rule-table :deep(.el-table__cell) {
  padding: 8px 0;
}

.rule-table :deep(.cell) {
  padding: 0 10px;
}

.identity-input,
.compact-number {
  width: 100%;
}

.compact-number :deep(.el-input__inner) {
  text-align: center;
}
</style>
