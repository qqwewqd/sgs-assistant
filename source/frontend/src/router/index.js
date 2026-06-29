import { createRouter, createWebHistory } from 'vue-router'
import { useUserStore } from '../store/user'
import { isAuthExpired } from '../utils/request'

const Login = () => import('../views/auth/Login.vue')
const Hall = () => import('../views/mobile/Hall.vue')
const Select = () => import('../views/mobile/Select.vue')
const Play = () => import('../views/mobile/Play.vue')
const AdminLayout = () => import('../views/admin/Layout.vue')
const GeneralAdmin = () => import('../views/admin/General.vue')
const IdentityModeAdmin = () => import('../views/admin/IdentityMode.vue')
const UserAdmin = () => import('../views/admin/User.vue')
const RoomAdmin = () => import('../views/admin/Rooms.vue')

const router = createRouter({
  history: createWebHistory(),
  routes: [
    {
      path: '/',
      redirect: () => {
        const user = useUserStore()
        if (!hydrated) {
          user.hydrate()
          hydrated = true
        }
        return user.admin ? '/admin/rooms' : '/mobile/hall'
      }
    },
    { path: '/login', component: Login, meta: { public: true } },
    { path: '/mobile/hall', component: Hall },
    { path: '/mobile/room/:roomCode/select', component: Select },
    { path: '/mobile/room/:roomCode/play', component: Play },
    {
      path: '/admin',
      component: AdminLayout,
      redirect: '/admin/rooms',
      meta: { admin: true },
      children: [
        { path: 'rooms', component: RoomAdmin },
        { path: 'identity-modes', component: IdentityModeAdmin },
        { path: 'generals', component: GeneralAdmin },
        { path: 'users', component: UserAdmin }
      ]
    }
  ]
})

let hydrated = false

router.beforeEach(async (to) => {
  const user = useUserStore()
  if (!hydrated) {
    user.hydrate()
    hydrated = true
  }
  if (to.meta.public) return true
  if (!user.loggedIn) {
    return `/login?redirect=${encodeURIComponent(to.fullPath)}`
  }
  try {
    await user.refreshMe()
  } catch (error) {
    if (isAuthExpired(error)) {
      user.logout()
      return `/login?redirect=${encodeURIComponent(to.fullPath)}`
    }
  }
  if (user.admin && isPathUnder(to.path, '/mobile')) {
    return '/admin/rooms'
  }
  if (to.meta.admin && !user.admin) {
    return '/mobile/hall'
  }
  return true
})

export default router

function isPathUnder(path, base) {
  return path === base || path.startsWith(`${base}/`)
}
