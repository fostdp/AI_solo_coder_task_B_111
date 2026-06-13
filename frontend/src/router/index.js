import { createRouter, createWebHashHistory } from 'vue-router'

const routes = [
  { path: '/', component: () => import('../views/Dashboard.vue') },
  { path: '/bridge', component: () => import('../views/BridgeMonitor.vue') },
  { path: '/simulation', component: () => import('../views/Simulation.vue') },
  { path: '/damage', component: () => import('../views/DamagePrediction.vue') },
  { path: '/alerts', component: () => import('../views/Alerts.vue') }
]

export default createRouter({
  history: createWebHashHistory(),
  routes
})
