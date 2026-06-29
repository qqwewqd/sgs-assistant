<template>
  <div class="admin-page">
    <aside class="admin-nav">
      <div class="brand">
        <strong>SGS Admin</strong>
        <span>{{ user.username }}</span>
      </div>
      <router-link to="/admin/rooms">房间管理</router-link>
      <router-link to="/admin/identity-modes">身份模式</router-link>
      <router-link to="/admin/generals">武将池</router-link>
      <router-link to="/admin/users">玩家开户</router-link>
      <button @click="logout">退出登录</button>
    </aside>
    <main class="admin-main">
      <router-view />
    </main>
  </div>
</template>

<script setup>
import 'element-plus/dist/index.css'
import { useRouter } from 'vue-router'
import { useGameStore } from '../../store/game'
import { useUserStore } from '../../store/user'

const router = useRouter()
const user = useUserStore()
const game = useGameStore()

function logout() {
  user.logout()
  game.clearRoom()
  router.replace('/login')
}
</script>

<style scoped>
.admin-page {
  min-height: 100vh;
  display: grid;
  grid-template-columns: 220px 1fr;
  background: var(--warm-bg);
}

.admin-nav {
  padding: 18px;
  background: #fff0d9;
  border-right: 1px solid var(--warm-line);
}

.brand {
  display: grid;
  gap: 6px;
  margin-bottom: 22px;
}

.brand strong {
  font-size: 22px;
  color: var(--warm-primary-strong);
}

.brand span {
  color: var(--warm-muted);
}

.admin-nav a,
.admin-nav button {
  display: flex;
  align-items: center;
  width: 100%;
  min-height: 44px;
  border: 0;
  border-radius: 8px;
  padding: 0 12px;
  margin-bottom: 8px;
  background: transparent;
  color: var(--warm-text);
  text-decoration: none;
  font-weight: 800;
}

.admin-nav a.router-link-active {
  background: var(--warm-primary);
  color: #fffaf2;
}

.admin-nav button {
  cursor: pointer;
}

.admin-main {
  min-width: 0;
  padding: 24px;
}

@media (max-width: 820px) {
  .admin-page {
    grid-template-columns: 1fr;
  }

  .admin-nav {
    display: flex;
    gap: 8px;
    align-items: center;
    overflow-x: auto;
    border-right: 0;
    border-bottom: 1px solid var(--warm-line);
  }

  .brand {
    min-width: 140px;
    margin-bottom: 0;
  }

  .admin-nav a,
  .admin-nav button {
    width: auto;
    min-width: 96px;
    margin: 0;
  }
}
</style>
