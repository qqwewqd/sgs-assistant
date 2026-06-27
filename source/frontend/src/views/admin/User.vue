<template>
  <section class="admin-section">
    <header class="admin-head">
      <div>
        <h1>玩家开户</h1>
        <p class="muted">账号密码写入本机数据文件，密码使用 BCrypt 保存</p>
      </div>
    </header>

    <form class="panel create-user" @submit.prevent="save">
      <el-input v-model.trim="form.username" placeholder="账号" />
      <el-input v-model="form.password" placeholder="密码" type="password" show-password />
      <el-select v-model="form.role">
        <el-option label="玩家" value="player" />
        <el-option label="管理员" value="admin" />
      </el-select>
      <el-button type="primary" native-type="submit">创建账号</el-button>
    </form>

    <el-table :data="rows" :empty-text="loading ? '加载中' : '暂无账号'" border>
      <el-table-column prop="id" label="ID" width="90" />
      <el-table-column prop="username" label="账号" min-width="160" />
      <el-table-column label="角色" width="110">
        <template #default="{ row }">
          <el-tag :type="row.role === 'admin' ? 'warning' : 'success'">{{ row.role }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="createdAt" label="创建时间" min-width="180" />
      <el-table-column label="操作" width="110">
        <template #default="{ row }">
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
  </section>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import {
  ElButton,
  ElInput,
  ElMessage,
  ElMessageBox,
  ElOption,
  ElPagination,
  ElSelect,
  ElTable,
  ElTableColumn,
  ElTag
} from 'element-plus'
import { createUser, deleteUser, listUsers } from '../../api/admin'

const rows = ref([])
const loading = ref(false)
const pagination = reactive({
  page: 1,
  pageSize: 20,
  total: 0
})
const form = reactive({
  username: '',
  password: '',
  role: 'player'
})

onMounted(load)

async function load() {
  loading.value = true
  try {
    const data = await listUsers({
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

function handleSizeChange() {
  pagination.page = 1
  load()
}

async function save() {
  if (!form.username || !form.password) {
    ElMessage.warning('请填写账号和密码')
    return
  }
  await createUser({ ...form })
  ElMessage.success('已创建')
  form.username = ''
  form.password = ''
  form.role = 'player'
  pagination.page = 1
  await load()
}

async function remove(row) {
  await ElMessageBox.confirm(`删除账号 ${row.username}？`, '确认删除')
  await deleteUser(row.id)
  ElMessage.success('已删除')
  await load()
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

.admin-head p {
  margin: 0;
}

.create-user {
  display: grid;
  grid-template-columns: 1fr 1fr 140px 110px;
  gap: 12px;
  align-items: center;
  padding: 12px;
}

.pagination-bar {
  display: flex;
  justify-content: flex-end;
}

@media (max-width: 900px) {
  .create-user {
    grid-template-columns: 1fr;
  }
}
</style>
