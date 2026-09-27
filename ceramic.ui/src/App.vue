<script setup lang="ts">
import { computed } from 'vue'
import { RouterView, useRoute } from 'vue-router'
import TheNavbar from '@/components/common/TheNavbar.vue'
import { useUserStore } from '@/stores/user'

const route = useRoute()
const userStore = useUserStore()

const hideNavbar = computed(() => {
  if (['/login', '/register'].includes(route.path)) {
    return true
  }
  if (route.path.startsWith('/service/')) {
    return true
  }
  if (route.path.startsWith('/admin')) {
    return true
  }
  return false
})
</script>

<template>
  <div id="app">
    <TheNavbar v-if="!hideNavbar" />
    
    <RouterView />
  </div>
</template>

<style>
* {
  margin: 0;
  padding: 0;
  box-sizing: border-box;
}

body {
  font-family: "PingFang SC", "Microsoft YaHei", Avenir, Helvetica, Arial, sans-serif;
  -webkit-font-smoothing: antialiased;
  -moz-osx-font-smoothing: grayscale;
  color: var(--c-text);
  /* 暖米白底 + 极淡冰裂纹釉面叠加，铺一层陶瓷底纹 */
  background-color: var(--c-bg);
  background-image: url("data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' width='120' height='120' viewBox='0 0 120 120'%3E%3Cg fill='none' stroke='%231f5f4c' stroke-width='0.6' stroke-opacity='0.05'%3E%3Cpath d='M0 22 L34 30 L60 12 L88 26 L120 18'/%3E%3Cpath d='M0 64 L28 54 L52 74 L84 60 L120 72'/%3E%3Cpath d='M0 102 L30 96 L58 110 L92 98 L120 106'/%3E%3Cpath d='M34 30 L28 54 L52 74 L58 110'/%3E%3Cpath d='M60 12 L52 74 L92 98'/%3E%3Cpath d='M88 26 L84 60 L92 98'/%3E%3C/g%3E%3C/svg%3E");
}

#app {
  min-height: 100vh;
}

/* 门面标题统一走系统宋体栈，正文保持无衬线 */
.home h1,
.home h2,
.home h3 {
  font-family: var(--font-serif);
}
</style>