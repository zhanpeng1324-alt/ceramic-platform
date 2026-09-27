<script setup lang="ts">
import { RouterLink } from 'vue-router'
import { useCartStore } from '@/stores/cart'

const cartStore = useCartStore()
</script>

<template>
  <nav class="navbar">
    <div class="nav-container">
      <div class="nav-brand">
        <RouterLink to="/" class="brand-link">
          陶瓷定制与销售平台
        </RouterLink>
      </div>
      
      <ul class="nav-menu">
        <li class="nav-item">
          <RouterLink to="/" class="nav-link">首页</RouterLink>
        </li>
        <li class="nav-item">
          <RouterLink to="/products" class="nav-link">产品</RouterLink>
        </li>
        <li class="nav-item">
          <RouterLink to="/customize" class="nav-link">定制</RouterLink>
        </li>
        <li class="nav-item">
          <RouterLink to="/about" class="nav-link">关于</RouterLink>
        </li>
      </ul>
      
      <div class="nav-actions">
        <RouterLink to="/cart" class="cart-link">
          🛒 购物车
          <span v-if="cartStore.totalCount > 0" class="cart-count">
            {{ cartStore.totalCount }}
          </span>
        </RouterLink>
      </div>
    </div>
  </nav>
</template>

<style scoped>
.navbar {
  background: white;
  box-shadow: 0 2px 10px rgba(0,0,0,0.1);
  position: sticky;
  top: 0;
  z-index: 100;
}

.nav-container {
  max-width: 1200px;
  margin: 0 auto;
  padding: 0 1rem;
  display: flex;
  justify-content: space-between;
  align-items: center;
  height: 60px;
}

.nav-brand {
  font-size: 1.2rem;
  font-weight: bold;
}

.brand-link {
  text-decoration: none;
  color: #2c3e50;
}

.nav-menu {
  display: flex;
  list-style: none;
  margin: 0;
  padding: 0;
  gap: 2rem;
}

.nav-link {
  text-decoration: none;
  color: #333;
  transition: color 0.2s;
}

.nav-link.router-link-active,
.nav-link:hover {
  color: #42b883;
}

.cart-link {
  text-decoration: none;
  color: #333;
  position: relative;
}

.cart-count {
  position: absolute;
  top: -8px;
  right: -12px;
  background: #e74c3c;
  color: white;
  border-radius: 50%;
  width: 18px;
  height: 18px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 0.7rem;
  font-weight: bold;
}

@media (max-width: 768px) {
  .nav-menu {
    gap: 1rem;
  }
  
  .nav-container {
    padding: 0 0.5rem;
  }
}
</style>