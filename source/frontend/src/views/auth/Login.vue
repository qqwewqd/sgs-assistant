<template>
  <main class="login-page page">
    <section class="login-panel panel">
      <div>
        <p class="eyebrow">SGS Assistant</p>
        <h1>三国杀线下助手</h1>
      </div>

      <form class="login-form" @submit.prevent="submit">
        <input v-model.trim="form.username" class="field" autocomplete="username" placeholder="账号" />
        <input v-model="form.password" class="field" autocomplete="current-password" placeholder="密码" type="password" />
        <button class="warm-button" type="submit" :disabled="loading">{{ loading ? '登录中' : '登录' }}</button>
      </form>

      <details v-if="bootstrapAvailable" class="bootstrap">
        <summary>初始化管理员</summary>
        <form class="login-form" @submit.prevent="submitBootstrap">
          <input v-model.trim="bootstrap.key" class="field" placeholder="初始化密钥" />
          <input v-model.trim="bootstrap.username" class="field" placeholder="管理员账号" />
          <input v-model="bootstrap.password" class="field" placeholder="管理员密码" type="password" />
          <button class="warm-button secondary" type="submit" :disabled="bootstrapLoading">
            {{ bootstrapLoading ? '提交中' : '创建管理员' }}
          </button>
        </form>
      </details>
    </section>
  </main>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { showToast } from 'vant'
import { bootstrapAdmin, bootstrapStatus } from '../../api/auth'
import { useUserStore } from '../../store/user'
import { isAuthExpired } from '../../utils/request'

const route = useRoute()
const router = useRouter()
const user = useUserStore()
const loading = ref(false)
const bootstrapLoading = ref(false)
const bootstrapAvailable = ref(false)

const form = reactive({
  username: '',
  password: ''
})

const bootstrap = reactive({
  key: '',
  username: '',
  password: ''
})

onMounted(async () => {
  await loadBootstrapStatus()
  user.hydrate()
  if (!user.loggedIn) return
  try {
    await user.refreshMe()
    await redirectByRole()
  } catch (error) {
    if (isAuthExpired(error)) {
      user.logout()
      return
    }
    await redirectByRole()
  }
})

async function loadBootstrapStatus() {
  try {
    const status = await bootstrapStatus()
    bootstrapAvailable.value = Boolean(status?.available)
  } catch {
    bootstrapAvailable.value = false
  }
}

async function submit() {
  if (!form.username || !form.password) {
    showToast('请输入账号和密码')
    return
  }
  loading.value = true
  try {
    await user.login(form)
    await redirectByRole()
  } catch (error) {
    showToast(error.message)
  } finally {
    loading.value = false
  }
}

async function submitBootstrap() {
  if (!bootstrap.key || !bootstrap.username || !bootstrap.password) {
    showToast('请填写完整')
    return
  }
  bootstrapLoading.value = true
  try {
    await bootstrapAdmin(bootstrap)
    showToast('管理员已创建')
    bootstrapAvailable.value = false
    form.username = bootstrap.username
    form.password = bootstrap.password
  } catch (error) {
    showToast(error.message)
  } finally {
    bootstrapLoading.value = false
  }
}

async function redirectByRole() {
  await router.replace(resolvePostLoginPath())
}

function resolvePostLoginPath() {
  const redirect = normalizeRedirect(route.query.redirect)
  const fallback = user.admin ? '/admin/rooms' : '/mobile/hall'
  if (!redirect) return fallback
  if (user.admin) return isPathUnder(redirect, '/admin') ? redirect : fallback
  return isPathUnder(redirect, '/mobile') ? redirect : fallback
}

function normalizeRedirect(value) {
  const redirect = Array.isArray(value) ? value[0] : value
  if (typeof redirect !== 'string') return ''
  if (!redirect.startsWith('/') || redirect.startsWith('//')) return ''
  return redirect
}

function isPathUnder(path, base) {
  return path === base || path.startsWith(`${base}/`)
}
</script>

<style scoped>
.login-page {
  min-height: 100vh;
  display: grid;
  place-items: center;
  padding: 22px;
}

.login-panel {
  width: min(420px, 100%);
  padding: 26px;
}

.eyebrow {
  margin: 0 0 8px;
  color: var(--warm-primary);
  font-weight: 900;
}

h1 {
  margin: 0;
  font-size: 32px;
  letter-spacing: 0;
}

.login-form {
  display: grid;
  gap: 12px;
  margin-top: 22px;
}

.bootstrap {
  margin-top: 20px;
  color: var(--warm-muted);
}

.bootstrap summary {
  cursor: pointer;
  font-weight: 800;
}
</style>
