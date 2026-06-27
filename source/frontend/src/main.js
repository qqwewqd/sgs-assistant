import { createApp } from 'vue'
import { createPinia } from 'pinia'
import 'vant/lib/index.css'
import App from './App.vue'
import router from './router'
import { prepareBestNode } from './utils/networkNode'
import './assets/styles/theme.css'

prepareBestNode().catch(() => {})

createApp(App)
  .use(createPinia())
  .use(router)
  .mount('#app')
