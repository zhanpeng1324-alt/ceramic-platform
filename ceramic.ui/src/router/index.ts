import { createRouter, createWebHistory } from 'vue-router'
import HomeView from '../views/home/HomeView.vue'
import { useUserStore } from '@/stores/user'

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes: [
    {
      path: '/',
      name: 'home',
      component: HomeView
    },
    {
      path: '/about',
      name: 'about',
      component: () => import('../views/AboutView.vue')
    },
    {
      path: '/products',
      name: 'products',
      component: () => import('../views/product/ProductsView.vue')
    },
    {
      path: '/products/:id',
      name: 'product-detail',
      component: () => import('../views/product/ProductDetailView.vue'),
      props: true
    },
    {
      path: '/customize',
      name: 'customize',
      component: () => import('../views/customize/CustomizeView.vue')
    },
    {
      path: '/my-customizations',
      name: 'my-customizations',
      component: () => import('../views/customize/MyCustomizationsView.vue'),
      meta: { requiresLogin: true }
    },
    {
      path: '/seckill',
      name: 'seckill',
      component: () => import('../views/seckill/SeckillView.vue')
    },
    {
      path: '/cart',
      name: 'cart',
      component: () => import('../views/CartView.vue'),
      meta: { requiresLogin: true }
    },
    {
      path: '/checkout',
      name: 'checkout',
      component: () => import('../views/CheckoutView.vue'),
      meta: { requiresLogin: true }
    },
    {
      path: '/login',
      name: 'login',
      component: () => import('../views/user/LoginView.vue')
    },
    {
      path: '/register',
      name: 'register',
      component: () => import('../views/user/RegisterView.vue')
    },
    {
      path: '/profile',
      name: 'profile',
      component: () => import('../views/user/UserProfile.vue'),
      meta: { requiresLogin: true }
    },
    {
      path: '/orders',
      name: 'orders',
      component: () => import('../views/OrderManagement.vue'),
      meta: { requiresLogin: true }
    },
    {
      path: '/orders/:id',
      name: 'order-detail',
      component: () => import('../views/OrderDetailView.vue'),
      meta: { requiresLogin: true },
      props: true
    },
    {
      path: '/notifications',
      name: 'notifications',
      component: () => import('../views/NotificationsView.vue'),
      meta: { requiresLogin: true }
    },
    {
      path: '/aftersales',
      name: 'aftersales',
      component: () => import('../views/user/AfterSalesView.vue'),
      meta: { requiresLogin: true }
    },
    {
      path: '/chat',
      name: 'chat',
      component: () => import('../views/ChatView.vue'),
      meta: { requiresLogin: true }
    },
    {
      path: '/service/login',
      name: 'service-login',
      redirect: '/login'
    },
    {
      path: '/service/dashboard',
      name: 'service-dashboard',
      component: () => import('../views/service/ServiceDashboard.vue'),
      meta: { requiresService: true }
    },
    {
      path: '/service/chat',
      name: 'service-chat',
      component: () => import('../views/service/ServiceChatView.vue'),
      meta: { requiresService: true }
    },
    {
      path: '/service/chat/:id',
      name: 'service-chat-detail',
      component: () => import('../views/service/ServiceChatView.vue'),
      meta: { requiresService: true }
    },
    {
      path: '/service/tickets',
      name: 'service-tickets',
      component: () => import('../views/service/ServiceTicketsView.vue'),
      meta: { requiresService: true }
    },
    {
      path: '/service/returns',
      name: 'service-returns',
      component: () => import('../views/service/ServiceReturnsView.vue'),
      meta: { requiresService: true }
    },
    {
      path: '/service/orders',
      name: 'service-orders',
      component: () => import('../views/service/ServiceOrdersView.vue'),
      meta: { requiresService: true }
    },
    {
      path: '/service/users',
      name: 'service-users',
      component: () => import('../views/service/ServiceUsersView.vue'),
      meta: { requiresService: true }
    },
    {
      path: '/admin',
      name: 'admin',
      component: () => import('../views/admin/AdminDashboard.vue'),
      meta: { requiresAdmin: true }
    },
    {
      path: '/admin/users',
      name: 'admin-users',
      component: () => import('../views/admin/UserManagement.vue'),
      meta: { requiresAdmin: true }
    },
    {
      path: '/admin/products',
      name: 'admin-products',
      component: () => import('../views/admin/ProductManagement.vue'),
      meta: { requiresAdmin: true }
    },
    {
      path: '/admin/seckill',
      name: 'admin-seckill',
      component: () => import('../views/admin/AdminSeckillView.vue'),
      meta: { requiresAdmin: true }
    },
    {
      path: '/admin/restock',
      name: 'admin-restock',
      component: () => import('../views/admin/RestockView.vue'),
      meta: { requiresAdmin: true }
    },
    {
      path: '/admin/orders',
      name: 'admin-orders',
      component: () => import('../views/admin/OrderManagement.vue'),
      meta: { requiresAdmin: true }
    },
    {
      path: '/admin/reviews',
      name: 'admin-reviews',
      component: () => import('../views/admin/ReviewManagement.vue'),
      meta: { requiresAdmin: true }
    },
    {
      path: '/admin/support',
      name: 'admin-support',
      component: () => import('../views/admin/SupportManagement.vue'),
      meta: { requiresAdmin: true }
    },
    {
      path: '/admin/customizations',
      name: 'admin-customizations',
      component: () => import('../views/admin/CustomizationManagement.vue'),
      meta: { requiresAdmin: true }
    },
    {
      path: '/admin/returns',
      name: 'admin-returns',
      component: () => import('../views/admin/AdminReturnsView.vue'),
      meta: { requiresAdmin: true }
    },
    {
      path: '/admin/settings',
      name: 'admin-settings',
      component: () => import('../views/admin/ShopSettings.vue'),
      meta: { requiresAdmin: true }
    },
  ]
})

router.beforeEach((to, from) => {
  const userStore = useUserStore()

  const isCustomerRoute = to.path.startsWith('/products') ||
                          to.path.startsWith('/customize') ||
                          to.path.startsWith('/my-customizations') ||
                          to.path.startsWith('/cart') ||
                          to.path.startsWith('/checkout') ||
                          to.path.startsWith('/orders') ||
                          to.path.startsWith('/aftersales') ||
                          to.path.startsWith('/chat') ||
                          to.path.startsWith('/profile') ||
                          to.path.startsWith('/notifications') ||
                          to.path.startsWith('/seckill') ||
                          to.path === '/'

  const isServiceRoute = to.path.startsWith('/service/') && to.path !== '/service/login'
  const isAdminRoute = to.path.startsWith('/admin')

  if (isCustomerRoute) {
    if (userStore.isLoggedIn && userStore.isService) {
      return '/service/dashboard'
    }
    if (userStore.isLoggedIn && userStore.isAdmin) {
      return '/admin'
    }
  }

  if (isServiceRoute) {
    if (!userStore.isLoggedIn) {
      return '/service/login'
    }
    if (!userStore.isService && !userStore.isAdmin) {
      return '/'
    }
  }

  if (isAdminRoute) {
    if (!userStore.isLoggedIn) {
      return '/service/login'
    }
    if (!userStore.isAdmin) {
      if (userStore.isService) {
        return '/service/dashboard'
      } else {
        return '/'
      }
    }
  }

  if (to.meta.requiresLogin) {
    if (!userStore.isLoggedIn) {
      return '/login'
    }
  }

  if (to.path === '/login' && userStore.isLoggedIn) {
    if (userStore.isCustomer) {
      return '/'
    } else if (userStore.isService) {
      return '/service/dashboard'
    } else if (userStore.isAdmin) {
      return '/admin'
    }
  }

  return true
})

export default router