import { defineStore } from 'pinia'
import { login as loginApi, me as meApi } from '../api/auth'

const STORAGE_KEY = 'sgs-user'

export const useUserStore = defineStore('user', {
  state: () => ({
    token: '',
    id: null,
    username: '',
    role: '',
    expiresAt: ''
  }),
  getters: {
    loggedIn: (state) => Boolean(state.token),
    admin: (state) => state.role === 'admin'
  },
  actions: {
    hydrate() {
      const raw = localStorage.getItem(STORAGE_KEY)
      if (!raw) return
      Object.assign(this, JSON.parse(raw))
    },
    persist() {
      localStorage.setItem(STORAGE_KEY, JSON.stringify({
        token: this.token,
        id: this.id,
        username: this.username,
        role: this.role,
        expiresAt: this.expiresAt
      }))
    },
    async login(payload) {
      const data = await loginApi(payload)
      this.token = data.token
      this.id = data.id
      this.username = data.username
      this.role = data.role
      this.expiresAt = data.expiresAt
      this.persist()
      return data
    },
    async refreshMe() {
      const data = await meApi()
      this.id = data.id
      this.username = data.username
      this.role = data.role
      this.persist()
      return data
    },
    logout() {
      this.token = ''
      this.id = null
      this.username = ''
      this.role = ''
      this.expiresAt = ''
      localStorage.removeItem(STORAGE_KEY)
    }
  }
})
