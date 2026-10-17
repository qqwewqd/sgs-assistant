<template>
  <section class="admin-section">
    <header class="admin-head">
      <div>
        <h1>数据管理</h1>
        <p class="muted">用于分享局域网版的势力、身份模式、武将资料及图片；不包含账号、房间和对局记录。</p>
      </div>
    </header>

    <article class="package-panel">
      <div>
        <strong>导出当前数据</strong>
        <p class="muted">生成 ZIP 数据包。可自行上传到网盘，并在共享大厅发布下载链接。</p>
        <p v-if="lastExportHash" class="export-hash">SHA-256：<code>{{ lastExportHash }}</code><button type="button" @click="copyHash">复制</button></p>
      </div>
      <el-button type="primary" :loading="exporting" @click="exportPackage">
        {{ exporting ? '正在生成…' : '导出 ZIP 数据包' }}
      </el-button>
    </article>

    <article class="package-panel import-panel">
      <div>
        <strong>导入 ZIP 数据包</strong>
        <p class="muted">同名武将和身份模式会更新，其他内容会新增；系统设置将以数据包中的设置为准。</p>
      </div>
      <div class="import-controls">
        <input ref="fileInput" type="file" accept=".zip,application/zip" class="file-input" @change="selectFile" />
        <span class="selected-file">{{ selectedFile?.name || '未选择文件' }}</span>
        <el-button :disabled="!selectedFile || importing" :loading="importing" type="warning" @click="importPackage(false)">
          {{ importing ? '正在导入…' : '验证并导入' }}
        </el-button>
        <el-button :disabled="!selectedFile || importing" plain @click="importPackage(true)">本地备份导入</el-button>
      </div>
    </article>

    <el-alert title="导入前建议先导出当前数据作为备份" type="warning" :closable="false" show-icon />
    <el-alert title="从共享大厅导入" description="选择 ZIP 后会自动计算 SHA-256 并向共享大厅核验；不匹配的文件会被拒绝。自己的未发布备份请使用“本地备份导入”。" type="info" :closable="false" show-icon />
  </section>
</template>

<script setup>
import { ref } from 'vue'
import { ElAlert, ElButton, ElMessage, ElMessageBox } from 'element-plus'
import { exportDataPackage, importDataPackage } from '../../api/admin'

const exporting = ref(false)
const importing = ref(false)
const selectedFile = ref(null)
const fileInput = ref(null)
const lastExportHash = ref('')

function selectFile(event) { selectedFile.value = event.target.files?.[0] || null }

async function exportPackage() {
  exporting.value = true
  try {
    const response = await exportDataPackage()
    if (!String(response.headers['content-type'] || '').includes('application/zip')) {
      const payload = JSON.parse(await response.data.text())
      throw new Error(payload.message || '导出失败')
    }
    lastExportHash.value = await sha256(response.data)
    const url = URL.createObjectURL(response.data)
    const anchor = document.createElement('a')
    anchor.href = url
    anchor.download = filenameFromDisposition(response.headers['content-disposition']) || `sgs-data-package-${dateStamp()}.zip`
    document.body.appendChild(anchor)
    anchor.click()
    anchor.remove()
    URL.revokeObjectURL(url)
    ElMessage.success(lastExportHash.value ? '数据包已导出，SHA-256 已生成' : '数据包已导出')
  } catch (error) {
    ElMessage.error(error.message || '导出失败')
  } finally { exporting.value = false }
}

async function importPackage(allowUnverified) {
  if (!selectedFile.value) return
  try {
    await ElMessageBox.confirm(allowUnverified ? '此方式会跳过共享大厅的审核指纹验证，仅适用于你自己导出的本地备份。是否继续？' : '将自动核验共享大厅审核 SHA-256；导入会更新同名武将、同名身份模式以及系统设置。是否继续？', allowUnverified ? '确认本地备份导入' : '确认验证并导入', { type: 'warning', confirmButtonText: '确认导入', cancelButtonText: '取消' })
  } catch { return }
  importing.value = true
  try {
    const result = await importDataPackage(selectedFile.value, allowUnverified)
    ElMessage.success(`${result.sharedPackageVerified ? '已通过共享大厅审核校验；' : ''}导入完成：新增武将 ${result.createdGenerals} 名，更新 ${result.updatedGenerals} 名；新增模式 ${result.createdIdentityModes} 个，更新 ${result.updatedIdentityModes} 个`)
    selectedFile.value = null
    if (fileInput.value) fileInput.value.value = ''
  } catch (error) {
    ElMessage.error(error.message || '导入失败')
  } finally { importing.value = false }
}

function filenameFromDisposition(value) {
  const match = String(value || '').match(/filename=([^;]+)/i)
  return match?.[1]?.replace(/^"|"$/g, '') || ''
}
function dateStamp() { return new Date().toISOString().slice(0, 10) }

async function sha256(blob) {
  if (!globalThis.crypto?.subtle) return ''
  const digest = await globalThis.crypto.subtle.digest('SHA-256', await blob.arrayBuffer())
  return Array.from(new Uint8Array(digest), (value) => value.toString(16).padStart(2, '0')).join('')
}

async function copyHash() {
  try {
    await navigator.clipboard.writeText(lastExportHash.value)
    ElMessage.success('SHA-256 已复制')
  } catch {
    ElMessage.warning('复制失败，请手动复制')
  }
}
</script>

<style scoped>
.admin-section { display: grid; gap: 16px; }
.admin-head h1 { margin: 0 0 6px; font-size: 28px; }
.admin-head p { margin: 0; }
.package-panel { display: flex; align-items: center; justify-content: space-between; gap: 18px; max-width: 760px; padding: 18px; border: 1px solid var(--warm-line); border-radius: 8px; background: #fffaf2; }
.package-panel strong { color: var(--warm-primary-strong); font-size: 18px; }
.package-panel p { margin: 6px 0 0; }
.import-panel { align-items: flex-start; }
.import-controls { display: grid; grid-template-columns: minmax(0, 1fr) auto; gap: 8px; min-width: min(100%, 330px); }
.file-input, .selected-file { min-width: 0; }
.file-input { grid-column: 1 / -1; }
.selected-file { overflow: hidden; color: var(--warm-muted); font-size: 13px; line-height: 32px; text-overflow: ellipsis; white-space: nowrap; }
.export-hash { display: flex; flex-wrap: wrap; align-items: center; gap: 6px; margin-top: 10px !important; font-size: 12px; }
.export-hash code { overflow-wrap: anywhere; }
.export-hash button { border: 0; border-radius: 5px; padding: 3px 7px; background: #ffe4b8; color: var(--warm-primary-strong); font-weight: 800; }
@media (max-width: 640px) { .package-panel { align-items: flex-start; flex-direction: column; } .import-controls { width: 100%; } }
</style>
