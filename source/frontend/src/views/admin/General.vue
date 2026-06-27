<template>
  <section class="admin-section">
    <header class="admin-head">
      <div>
        <h1>武将池管理</h1>
        <p class="muted">图片大小上限 1MB，路径保存为 /upload/...</p>
      </div>
      <el-button type="primary" @click="openCreate">新增武将</el-button>
    </header>

    <div class="panel filter-bar">
      <el-input v-model="keyword" clearable placeholder="搜索武将" @input="resetAndLoad" />
      <el-switch v-model="lordOnly" active-text="只看主公将" @change="resetAndLoad" />
    </div>

    <el-table :data="rows" :empty-text="loading ? '加载中' : '暂无武将'" border>
      <el-table-column label="图片" width="92">
        <template #default="{ row }">
          <el-button size="small" @click="openPreview(row)">查看</el-button>
        </template>
      </el-table-column>
      <el-table-column prop="name" label="名称" min-width="140" />
      <el-table-column label="势力" width="90">
        <template #default="{ row }">
          <el-tag :type="row.faction ? 'success' : 'info'">{{ factionLabel(row.faction) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="imagePath" label="图片路径" min-width="260">
        <template #default="{ row }">
          <span class="path-cell">{{ row.imagePath }}</span>
        </template>
      </el-table-column>
      <el-table-column label="主公将" width="110">
        <template #default="{ row }">
          <el-tag :type="row.isLord ? 'warning' : 'info'">{{ row.isLord ? '是' : '否' }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="暗置将" width="110">
        <template #default="{ row }">
          <el-tag :type="row.startsHidden ? 'danger' : 'info'">{{ row.startsHidden ? '是' : '否' }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="170" fixed="right">
        <template #default="{ row }">
          <el-button size="small" @click="openEdit(row)">编辑</el-button>
          <el-button size="small" type="danger" @click="remove(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <div class="pagination-bar">
      <el-pagination
        v-model:current-page="pagination.page"
        v-model:page-size="pagination.pageSize"
        :page-sizes="[10, 20, 50, 100]"
        :total="pagination.total"
        background
        layout="total, sizes, prev, pager, next, jumper"
        @current-change="load"
        @size-change="handleSizeChange"
      />
    </div>

    <el-dialog v-model="dialogVisible" :title="form.id ? '编辑武将' : '新增武将'" width="460px">
      <el-form label-width="78px">
        <el-form-item label="名称">
          <el-input v-model="form.name" />
        </el-form-item>
        <el-form-item label="图片名">
          <el-input v-model.trim="form.imageName" placeholder="例如 liubei.webp" />
        </el-form-item>
        <el-form-item label="势力">
          <el-select v-model="form.faction" placeholder="不限/无" clearable>
            <el-option v-for="option in factionOptions" :key="option.value || 'none'" :label="option.label" :value="option.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="主公将">
          <el-switch v-model="form.isLord" />
        </el-form-item>
        <el-form-item label="暗置将">
          <el-switch v-model="form.startsHidden" />
        </el-form-item>
        <el-form-item label="图片">
          <el-upload
            :key="uploadKey"
            :auto-upload="false"
            :limit="1"
            :on-change="onFileChange"
            :on-remove="onFileRemove"
          >
            <el-button :loading="compressing">{{ compressing ? '压缩中' : '选择图片' }}</el-button>
          </el-upload>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :disabled="compressing" @click="save">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="previewVisible" :title="previewGeneral?.name || '武将图片'" width="420px">
      <div class="preview-box">
        <img v-if="previewGeneral" :src="assetUrl(previewGeneral.imagePath)" :alt="previewGeneral.name" />
      </div>
    </el-dialog>
  </section>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import {
  ElButton,
  ElDialog,
  ElForm,
  ElFormItem,
  ElInput,
  ElMessage,
  ElMessageBox,
  ElOption,
  ElPagination,
  ElSelect,
  ElSwitch,
  ElTable,
  ElTableColumn,
  ElTag,
  ElUpload
} from 'element-plus'
import { createGeneral, deleteGeneral, listGenerals, updateGeneral } from '../../api/admin'
import { assetUrl } from '../../utils/pathHelper'

const factionOptions = [
  { label: '不限/无', value: '' },
  { label: '魏', value: 'WEI' },
  { label: '蜀', value: 'SHU' },
  { label: '吴', value: 'WU' },
  { label: '群', value: 'QUN' },
  { label: '晋', value: 'JIN' },
  { label: '神', value: 'GOD' }
]

const rows = ref([])
const keyword = ref('')
const lordOnly = ref(false)
const loading = ref(false)
const dialogVisible = ref(false)
const previewVisible = ref(false)
const previewGeneral = ref(null)
const uploadKey = ref(0)
const compressing = ref(false)
const pagination = reactive({
  page: 1,
  pageSize: 20,
  total: 0
})
const form = reactive({
  id: null,
  name: '',
  imageName: '',
  faction: '',
  isLord: false,
  startsHidden: false,
  file: null
})
const MAX_UPLOAD_BYTES = 1024 * 1024
const TARGET_UPLOAD_BYTES = 950 * 1024
const MAX_IMAGE_DIMENSION = 1800
const MIN_IMAGE_DIMENSION = 420
const IMAGE_QUALITY_LEVELS = [0.88, 0.8, 0.72, 0.64, 0.56, 0.48, 0.42]
const IMAGE_OUTPUT_TYPES = [
  { type: 'image/webp', extension: 'webp' },
  { type: 'image/jpeg', extension: 'jpg' }
]
let compressionJob = 0

onMounted(load)

async function load() {
  loading.value = true
  try {
    const data = await listGenerals({
      keyword: keyword.value || undefined,
      lordOnly: lordOnly.value || undefined,
      page: pagination.page,
      pageSize: pagination.pageSize
    })
    rows.value = data?.records || []
    pagination.total = Number(data?.total || 0)
    if (!rows.value.length && pagination.total > 0 && pagination.page > 1) {
      pagination.page -= 1
      await load()
    }
  } finally {
    loading.value = false
  }
}

function resetAndLoad() {
  pagination.page = 1
  load()
}

function handleSizeChange() {
  pagination.page = 1
  load()
}

function openCreate() {
  Object.assign(form, { id: null, name: '', imageName: '', faction: '', isLord: false, startsHidden: false, file: null })
  compressionJob += 1
  compressing.value = false
  uploadKey.value += 1
  dialogVisible.value = true
}

function openEdit(row) {
  Object.assign(form, {
    id: row.id,
    name: row.name,
    imageName: filenameFromPath(row.imagePath),
    faction: normalizeFactionValue(row.faction),
    isLord: row.isLord,
    startsHidden: row.startsHidden,
    file: null
  })
  compressionJob += 1
  compressing.value = false
  uploadKey.value += 1
  dialogVisible.value = true
}

function openPreview(row) {
  previewGeneral.value = row
  previewVisible.value = true
}

async function onFileChange(file) {
  if (!file.raw) return
  const job = ++compressionJob
  if (!file.raw.type?.startsWith('image/')) {
    form.file = null
    uploadKey.value += 1
    ElMessage.warning('请选择图片文件')
    return
  }
  compressing.value = true
  try {
    const compressed = await compressImage(file.raw)
    if (job !== compressionJob) return
    form.file = compressed
    syncImageNameWithFile(compressed.name)
    if (compressed.size < file.raw.size) {
      ElMessage.success(`图片已压缩至 ${formatFileSize(compressed.size)}`)
    }
  } catch (error) {
    if (job !== compressionJob) return
    form.file = null
    uploadKey.value += 1
    ElMessage.error(error?.message || '图片压缩失败')
  } finally {
    if (job === compressionJob) compressing.value = false
  }
}

function onFileRemove() {
  compressionJob += 1
  compressing.value = false
  form.file = null
}

async function save() {
  if (compressing.value) {
    ElMessage.warning('图片正在压缩，请稍候')
    return
  }
  if (!form.name.trim()) {
    ElMessage.warning('请输入名称')
    return
  }
  if (!form.id && !form.file) {
    ElMessage.warning('请选择图片')
    return
  }
  const data = new FormData()
  data.append('name', form.name.trim())
  if (form.imageName.trim()) data.append('imageName', form.imageName.trim())
  data.append('faction', form.faction || '')
  data.append('isLord', String(form.isLord))
  data.append('startsHidden', String(form.startsHidden))
  if (form.file) data.append('image', form.file)
  const editing = Boolean(form.id)
  if (editing) await updateGeneral(form.id, data)
  else await createGeneral(data)
  dialogVisible.value = false
  ElMessage.success('已保存')
  if (!editing) pagination.page = 1
  await load()
}

async function remove(row) {
  await ElMessageBox.confirm(`删除 ${row.name}？`, '确认删除')
  await deleteGeneral(row.id)
  ElMessage.success('已删除')
  await load()
}

function filenameFromPath(path) {
  if (!path) return ''
  return String(path).split('/').filter(Boolean).at(-1) || ''
}

function normalizeFactionValue(value) {
  return factionOptions.some((option) => option.value === value) ? value : ''
}

function factionLabel(value) {
  return factionOptions.find((option) => option.value === value)?.label || '无'
}

function syncImageNameWithFile(filename) {
  if (!filename) return
  if (!form.imageName) {
    form.imageName = filename
    return
  }
  const fileExtension = extensionOf(filename)
  if (!fileExtension) return
  const currentExtension = extensionOf(form.imageName)
  if (!currentExtension) {
    form.imageName = `${form.imageName}.${fileExtension}`
    return
  }
  if (currentExtension !== fileExtension) {
    form.imageName = form.imageName.slice(0, form.imageName.length - currentExtension.length) + fileExtension
  }
}

function extensionOf(filename) {
  const index = filename.lastIndexOf('.')
  if (index < 0 || index === filename.length - 1) return ''
  return filename.slice(index + 1).toLowerCase()
}

async function compressImage(file) {
  if (file.size <= TARGET_UPLOAD_BYTES) return file
  const image = await loadImage(file)
  const canvas = document.createElement('canvas')
  const context = canvas.getContext('2d')
  if (!context) throw new Error('当前浏览器不支持图片压缩')

  const sourceMaxDimension = Math.max(image.naturalWidth, image.naturalHeight)
  let scale = Math.min(1, MAX_IMAGE_DIMENSION / sourceMaxDimension)
  let smallestBlob = null

  while (Math.max(image.naturalWidth * scale, image.naturalHeight * scale) >= MIN_IMAGE_DIMENSION) {
    canvas.width = Math.max(1, Math.round(image.naturalWidth * scale))
    canvas.height = Math.max(1, Math.round(image.naturalHeight * scale))

    for (const output of IMAGE_OUTPUT_TYPES) {
      drawImage(context, image, canvas.width, canvas.height, output.type)
      for (const quality of IMAGE_QUALITY_LEVELS) {
        const blob = await canvasToBlob(canvas, output.type, quality)
        if (blob.type && blob.type !== output.type) continue
        const candidate = { blob, extension: output.extension }
        if (!smallestBlob || candidate.blob.size < smallestBlob.blob.size) smallestBlob = candidate
        if (blob.size <= TARGET_UPLOAD_BYTES) {
          return blobToFile(blob, replaceExtension(file.name, output.extension))
        }
      }
    }
    scale *= 0.82
  }

  if (smallestBlob && smallestBlob.blob.size <= MAX_UPLOAD_BYTES) {
    return blobToFile(smallestBlob.blob, replaceExtension(file.name, smallestBlob.extension))
  }
  throw new Error('图片压缩后仍超过 1MB，请换一张更小的图')
}

function drawImage(context, image, width, height, outputType) {
  context.clearRect(0, 0, width, height)
  if (outputType === 'image/jpeg') {
    context.fillStyle = '#ffffff'
    context.fillRect(0, 0, width, height)
  }
  context.drawImage(image, 0, 0, width, height)
}

function loadImage(file) {
  return new Promise((resolve, reject) => {
    const url = URL.createObjectURL(file)
    const image = new Image()
    image.onload = () => {
      URL.revokeObjectURL(url)
      resolve(image)
    }
    image.onerror = () => {
      URL.revokeObjectURL(url)
      reject(new Error('图片读取失败'))
    }
    image.src = url
  })
}

function canvasToBlob(canvas, type, quality) {
  return new Promise((resolve, reject) => {
    canvas.toBlob((blob) => {
      if (!blob) {
        reject(new Error('当前浏览器不支持图片压缩'))
        return
      }
      resolve(blob)
    }, type, quality)
  })
}

function blobToFile(blob, filename) {
  return new File([blob], filename, { type: blob.type || 'image/webp', lastModified: Date.now() })
}

function replaceExtension(filename, extension) {
  const safe = filename || `general.${extension}`
  const index = safe.lastIndexOf('.')
  if (index < 0) return `${safe}.${extension}`
  return `${safe.slice(0, index)}.${extension}`
}

function formatFileSize(size) {
  return `${Math.round(size / 1024)}KB`
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
  margin: 0 0 6px;
  font-size: 28px;
  letter-spacing: 0;
}

.admin-head p {
  margin: 0;
}

.filter-bar {
  display: flex;
  gap: 14px;
  align-items: center;
  padding: 12px;
}

.filter-bar .el-input {
  max-width: 320px;
}

.pagination-bar {
  display: flex;
  justify-content: flex-end;
}

.path-cell {
  display: block;
  overflow: hidden;
  color: #6b5a47;
  font-family: Consolas, "Microsoft YaHei", sans-serif;
  font-size: 12px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.preview-box {
  display: grid;
  place-items: center;
  min-height: 360px;
  border: 1px solid var(--warm-line);
  border-radius: 6px;
  background: #fff7ed;
}

.preview-box img {
  max-width: 100%;
  max-height: 70vh;
  object-fit: contain;
  border-radius: 6px;
}
</style>
