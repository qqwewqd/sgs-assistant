<template>
  <section class="shared-hall">
    <header class="admin-head">
      <div>
        <h1>共享大厅</h1>
        <p class="muted">浏览服务器已审核通过的数据包。审核仅能在服务器端进行。</p>
      </div>
      <div class="header-actions"><el-button :loading="loading" @click="load">刷新</el-button><el-button type="primary" @click="openPublish">发布</el-button></div>
    </header>

    <div class="panel filter-bar">
      <el-input v-model.trim="keyword" clearable maxlength="80" placeholder="搜索标题或发布者" @keyup.enter="load" />
      <el-button @click="load">搜索</el-button>
    </div>

    <div v-loading="loading" class="package-grid">
      <article v-for="item in rows" :key="item.id" class="package-card panel">
        <div class="card-head"><strong>{{ item.title }}</strong><el-tag type="success">已审核</el-tag></div>
        <p class="package-meta">发布者：{{ item.authorUsername }}<template v-if="item.fileSize"> · {{ formatSize(item.fileSize) }}</template></p>
        <p v-if="item.description" class="description">{{ item.description }}</p>
        <div class="hash"><span>审核 SHA-256</span><code>{{ item.sha256 }}</code></div>
        <p v-if="item.contact" class="package-meta">联系：{{ item.contact }}</p>
        <div class="actions"><a class="el-button el-button--primary is-plain" :href="item.downloadUrl" target="_blank" rel="noopener noreferrer">前往网盘</a></div>
      </article>
      <el-empty v-if="!loading && !rows.length" description="暂无已审核分享" />
    </div>

    <el-dialog v-model="publishVisible" title="发布到共享大厅" width="min(94vw, 620px)">
      <el-form label-width="96px">
        <el-form-item label="发布者昵称"><el-input v-model.trim="form.authorNickname" maxlength="50" placeholder="审核和大厅中显示的昵称" /></el-form-item>
        <el-form-item label="分享标题"><el-input v-model.trim="form.title" maxlength="80" /></el-form-item>
        <el-form-item label="简介"><el-input v-model.trim="form.description" type="textarea" :rows="4" maxlength="2000" show-word-limit /></el-form-item>
        <el-form-item label="网盘链接"><el-input v-model.trim="form.downloadUrl" maxlength="1000" placeholder="http 或 https 链接" /></el-form-item>
        <el-form-item label="联系方式"><el-input v-model.trim="form.contact" maxlength="255" /></el-form-item>
      </el-form>
      <template #footer><el-button @click="publishVisible = false">取消</el-button><el-button type="primary" :loading="publishing" @click="publish">提交审核</el-button></template>
    </el-dialog>
  </section>
</template>

<script setup>
import { reactive, ref } from 'vue'
import { ElButton, ElDialog, ElEmpty, ElForm, ElFormItem, ElInput, ElMessage, ElTag } from 'element-plus'
import { listSharedHallPackages, submitSharedHallPackage } from '../../api/sharedHall'

const keyword = ref('')
const rows = ref([])
const loading = ref(false)
const publishVisible = ref(false)
const publishing = ref(false)
const form = reactive({ authorNickname: localStorage.getItem('sgs-shared-hall-nickname') || '', title: '', description: '', downloadUrl: '', contact: '' })

load()

async function load() {
  loading.value = true
  try {
    rows.value = (await listSharedHallPackages({ keyword: keyword.value || undefined, pageSize: 100 })).records || []
  } catch (error) {
    ElMessage.error(error.message || '读取共享大厅失败')
  } finally {
    loading.value = false
  }
}

function formatSize(value) {
  const bytes = Number(value)
  return `${(bytes / 1024 / 1024).toFixed(bytes < 10 * 1024 * 1024 ? 1 : 0)} MB`
}

function openPublish() {
  Object.assign(form, { title: '', description: '', downloadUrl: '', contact: '' })
  publishVisible.value = true
}

async function publish() {
  publishing.value = true
  try {
    await submitSharedHallPackage({ ...form })
    localStorage.setItem('sgs-shared-hall-nickname', form.authorNickname)
    publishVisible.value = false
    ElMessage.success('已提交服务器审核')
  } catch (error) {
    ElMessage.error(error.message || '提交失败')
  } finally {
    publishing.value = false
  }
}
</script>

<style scoped>
.shared-hall { display: grid; gap: 16px; }
.admin-head, .header-actions { display: flex; align-items: flex-start; justify-content: space-between; gap: 16px; }.header-actions { align-items: center; }
.admin-head h1 { margin: 0 0 6px; font-size: 28px; }.admin-head p { margin: 0; }
.filter-bar { display: grid; grid-template-columns: minmax(220px, 1fr) auto; gap: 10px; padding: 14px; }
.package-grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(300px, 1fr)); gap: 14px; }
.package-card { display: flex; min-width: 0; min-height: 210px; flex-direction: column; gap: 10px; padding: 16px; }
.card-head { display: flex; align-items: center; justify-content: space-between; gap: 10px; }.card-head strong { overflow: hidden; color: var(--warm-primary-strong); font-size: 18px; text-overflow: ellipsis; white-space: nowrap; }
.package-meta, .description { margin: 0; }.package-meta { color: var(--warm-muted); font-size: 13px; }.description { min-height: 42px; white-space: pre-wrap; line-height: 1.5; }
.hash { display: grid; gap: 4px; color: var(--warm-muted); font-size: 12px; }.hash code { overflow-wrap: anywhere; color: #6b513c; font-family: Consolas, monospace; font-size: 11px; }
.actions { margin-top: auto; }.actions a { text-decoration: none; }
</style>
