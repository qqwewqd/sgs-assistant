<template>
  <section class="admin-section">
    <header class="admin-head">
      <div>
        <h1>房间管理</h1>
        <p class="muted">查看 Redis 中的运行房间，处理离线或异常对局</p>
      </div>
      <el-button type="primary" :loading="loading" @click="load">刷新</el-button>
    </header>

    <div class="summary-row">
      <div class="summary-item panel">
        <span>总房间</span>
        <strong>{{ pagination.total }}</strong>
      </div>
      <div class="summary-item panel">
        <span>本页游戏中</span>
        <strong>{{ playingCount }}</strong>
      </div>
      <div class="summary-item panel">
        <span>本页存在离线</span>
        <strong>{{ offlineRoomCount }}</strong>
      </div>
    </div>

    <el-table :data="rows" :empty-text="loading ? '加载中' : '暂无房间'" border>
      <el-table-column type="expand">
        <template #default="{ row }">
          <div class="player-list">
            <div v-for="player in row.players" :key="player.userId" class="player-line">
              <strong>{{ player.username }}</strong>
              <el-tag v-if="player.owner" size="small" type="warning">房主</el-tag>
              <el-tag size="small" :type="player.online ? 'success' : 'danger'">
                {{ player.online ? '在线' : '离线' }}
              </el-tag>
              <el-tag v-if="player.identity" size="small">{{ player.identity }}</el-tag>
              <span class="muted">{{ player.selectedGeneralName || '未选将' }}</span>
              <span v-if="player.locked" class="muted">已锁定</span>
              <span v-if="player.dead" class="muted">已阵亡</span>
            </div>
          </div>
        </template>
      </el-table-column>
      <el-table-column prop="roomCode" label="房间号" width="120" />
      <el-table-column prop="identityModeName" label="身份模式" min-width="140" />
      <el-table-column label="阶段" width="110">
        <template #default="{ row }">
          <el-tag :type="statusType(row.status)">{{ statusText(row.status) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="ownerUsername" label="房主" min-width="140" />
      <el-table-column label="人数" width="120">
        <template #default="{ row }">{{ row.onlineCount }}/{{ row.playerCount }} 在线</template>
      </el-table-column>
      <el-table-column label="锁定" width="100">
        <template #default="{ row }">{{ row.lockedCount }}/{{ row.playerCount }}</template>
      </el-table-column>
      <el-table-column label="更新时间" min-width="180">
        <template #default="{ row }">{{ formatTime(row.updatedAt) }}</template>
      </el-table-column>
      <el-table-column label="操作" width="120" fixed="right">
        <template #default="{ row }">
          <el-button size="small" type="danger" @click="dissolve(row)">解散</el-button>
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
import { computed, onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import {
  ElButton,
  ElMessage,
  ElMessageBox,
  ElPagination,
  ElTable,
  ElTableColumn,
  ElTag
} from 'element-plus'
import { dissolveRoom, listRooms } from '../../api/admin'

const rows = ref([])
const loading = ref(false)
const pagination = reactive({
  page: 1,
  pageSize: 20,
  total: 0
})
let timer = 0

const playingCount = computed(() => rows.value.filter((room) => room.status === 'PLAYING').length)
const offlineRoomCount = computed(() => rows.value.filter((room) => room.offlineCount > 0).length)

onMounted(() => {
  load()
  timer = window.setInterval(load, 8000)
})

onBeforeUnmount(() => window.clearInterval(timer))

async function load() {
  loading.value = true
  try {
    const data = await listRooms({
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

async function dissolve(row) {
  await ElMessageBox.confirm(`强制解散房间 ${row.roomCode}？在线玩家会被带回大厅。`, '确认解散')
  await dissolveRoom(row.roomCode)
  ElMessage.success('房间已解散')
  await load()
}

function statusText(status) {
  const map = {
    WAITING: '等待中',
    SELECTING: '选将中',
    PLAYING: '游戏中'
  }
  return map[status] || status
}

function statusType(status) {
  const map = {
    WAITING: 'info',
    SELECTING: 'warning',
    PLAYING: 'success'
  }
  return map[status] || 'info'
}

function formatTime(value) {
  if (!value) return '-'
  return new Intl.DateTimeFormat('zh-CN', {
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
    second: '2-digit',
    hour12: false
  }).format(new Date(value))
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

.summary-row {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 12px;
}

.summary-item {
  min-height: 92px;
  padding: 14px;
  display: grid;
  align-content: center;
  gap: 8px;
}

.summary-item span {
  color: var(--warm-muted);
  font-weight: 800;
}

.summary-item strong {
  color: var(--warm-primary-strong);
  font-size: 30px;
  line-height: 1;
}

.player-list {
  display: grid;
  gap: 8px;
  padding: 8px 44px 8px 12px;
  background: #fff8ed;
}

.player-line {
  display: flex;
  min-height: 34px;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

.player-line strong {
  min-width: 88px;
}

.pagination-bar {
  display: flex;
  justify-content: flex-end;
}

@media (max-width: 900px) {
  .summary-row {
    grid-template-columns: 1fr;
  }
}
</style>
