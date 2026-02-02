import { createRouter, createWebHistory } from 'vue-router'
import { useUserStore } from '../stores/user.js'

const routes = [
  {
    path: '/',
    name: 'Welcome',
    component: () => import('@/views/Welcome.vue'),
    meta: { requiresAuth: false }
  },
  {
    path: '/welcome',
    redirect: '/'
  },
  {
    path: '/register',
    name: 'Register',
    component: () => import('@/views/Register.vue'),
    meta: { requiresAuth: false }
  },
  {
    path: '/login',
    name: 'Login',
    component: () => import('@/views/Login.vue'),
    meta: { requiresAuth: false }
  },
  {
    path: '/onboarding/profile',
    name: 'ProfileSetup',
    component: () => import('@/views/ProfileSetup.vue'),
    meta: { requiresAuth: true }
  },
  {
    path: '/profile-setup',
    redirect: '/onboarding/profile'
  },
  {
    path: '/users/:userId/profile',
    name: 'UserProfile',
    component: () => import('@/views/UserProfile.vue'),
    meta: { requiresAuth: true }
  },
  {
    path: '/plan/intent',
    name: 'PlanIntent',
    component: () => import('@/views/PlanIntent.vue'),
    meta: { requiresAuth: true, requiresProfile: true }
  },
  {
    path: '/plan/destinations',
    name: 'Destinations',
    component: () => import('@/views/Destinations.vue'),
    meta: { requiresAuth: true, requiresProfile: true }
  },
  {
    path: '/itinerary/progress/:tripId',
    name: 'ItineraryProgress',
    component: () => import('@/views/ItineraryProgress.vue'),
    meta: { requiresAuth: true }
  },
  {
    path: '/trips/:tripId',
    name: 'TripDetail',
    component: () => import('@/views/TripDetail.vue'),
    meta: { requiresAuth: true }
  },
  {
    path: '/itinerary/overview',
    name: 'ItineraryOverview',
    component: () => import('@/views/ItineraryOverview.vue'),
    meta: { requiresAuth: true }
  },
  {
    path: '/itinerary/day/:tripId/:dayNumber',
    name: 'ItineraryDayView',
    component: () => import('@/views/ItineraryDayView.vue'),
    meta: { requiresAuth: true }
  },
  {
    path: '/itinerary/edit/:tripId',
    name: 'ItineraryEditor',
    component: () => import('@/views/ItineraryEditor.vue'),
    meta: { requiresAuth: true }
  },
  {
    path: '/trips/history',
    name: 'PastTrips',
    component: () => import('@/views/PastTrips.vue'),
    meta: { requiresAuth: true }
  }
]

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes
})

// Navigation guard
router.beforeEach((to, from, next) => {
  const userStore = useUserStore()
  const requiresAuth = to.matched.some(record => record.meta.requiresAuth)

  if (requiresAuth && !userStore.isLoggedIn) {
    // 需要认证但用户未登录，跳转到登录页
    next({ name: 'Login', query: { redirect: to.fullPath } })
  } else if ((to.name === 'Login' || to.name === 'Register') && userStore.isLoggedIn) {
    // 已登录用户访问登录/注册页，跳转到合适的页面
    if (userStore.hasProfile) {
      next({ name: 'PlanIntent' }) // 已完成 Profile，跳转到规划页面
    } else {
      next({ name: 'ProfileSetup' }) // 未完成 Profile，跳转到设置页面
    }
  } else {
    next()
  }
})

export default router
