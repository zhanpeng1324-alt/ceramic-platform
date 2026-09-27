import { createApp } from 'vue'
import { createPinia } from 'pinia'
import App from './App.vue'
import router from './router'
import { useUserStore } from '@/stores/user'
import '@/assets/theme.css'

const app = createApp(App)

app.use(createPinia())
app.use(router)

const userStore = useUserStore()
userStore.initUser()

app.mount('#app')