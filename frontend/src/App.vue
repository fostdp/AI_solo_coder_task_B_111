<template>
  <div class="flex h-full">
    <aside class="w-60 bg-primary text-white flex flex-col shadow-xl">
      <div class="p-4 border-b border-blue-900">
        <h1 class="text-lg font-bold">古桥健康监测平台</h1>
        <p class="text-xs text-blue-200 text-xs mt-1">Ancient Bridge Health Monitoring</p>
      </div>
      <nav class="flex-1 p-2 space-y-1">
        <router-link v-for="m in menus" :key="m.path" :to="m.path"
          class="block px-3 py-2 rounded text-sm hover:bg-blue-900/40 transition"
          :class="{ 'bg-blue-900/60': route.path === m.path }">
          {{ m.label }}
        </router-link>
      </nav>
      <div class="p-3 text-xs text-blue-300 border-t border-blue-900">
        <p>监测桥梁：{{ bridgeCount }}座</p>
      </div>
    </aside>
    <main class="flex-1 overflow-auto bg-stone-100">
      <router-view />
    </main>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import axios from 'axios'

const route = useRoute()
const bridgeCount = ref(10)

const menus = [
  { path: '/', label: '监测总览' },
  { path: '/bridge', label: '三维监测' },
  { path: '/simulation', label: '力学仿真' },
  { path: '/damage', label: '损伤预测' },
  { path: '/alerts', label: '告警中心' }
]

onMounted(async () => {
  try {
    const res = await axios.get('/api/bridges')
    bridgeCount.value = res.data.data?.length || 10
  } catch (e) {
    console.warn('加载桥梁列表失败，使用默认值')
  }
})
</script>
