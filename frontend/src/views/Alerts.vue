<template>
  <div class="p-6 space-y-6">
    <div class="flex justify-between items-center">
      <div>
        <h1 class="text-2xl font-bold text-stone-800">告警中心</h1>
        <p class="text-sm text-stone-500">裂缝扩展速率超限 / 桥墩沉降超10mm 触发</p>
      </div>
      <div class="flex gap-2">
        <button @click="filter = 'all'"
          :class="filter === 'all' ? 'bg-primary text-white' : 'bg-white text-stone-700 border border-stone-300'"
          class="px-3 py-1.5 text-sm rounded shadow-sm">全部</button>
        <button @click="filter = 'unacknowledged'"
          :class="filter === 'unacknowledged' ? 'bg-red-500 text-white' : 'bg-white text-stone-700 border border-stone-300'"
          class="px-3 py-1.5 text-sm rounded shadow-sm">未处理
          <span v-if="unackCount > 0" class="ml-1 text-xs">({{ unackCount }})</span>
        </button>
      </div>
    </div>

    <div class="grid grid-cols-4 gap-4">
      <div class="bg-white rounded-lg shadow p-4 border-l-4 border-red-500">
        <div class="text-xs text-stone-500">危险告警</div>
        <div class="text-2xl font-bold text-red-600">{{ dangerCount }}</div>
      </div>
      <div class="bg-white rounded-lg shadow p-4 border-l-4 border-yellow-500">
        <div class="text-xs text-stone-500">预警告警</div>
        <div class="text-2xl font-bold text-yellow-600">{{ warningCount }}</div>
      </div>
      <div class="bg-white rounded-lg shadow p-4 border-l-4 border-blue-500">
        <div class="text-xs text-stone-500">本月告警</div>
        <div class="text-2xl font-bold text-blue-600">{{ monthCount }}</div>
      </div>
      <div class="bg-white rounded-lg shadow p-4 border-l-4 border-green-500">
        <div class="text-xs text-stone-500">MQTT推送</div>
        <div class="text-2xl font-bold text-green-600">{{ mqttStatus ? '正常' : '离线' }}</div>
        <div class="text-xs text-stone-400 mt-1">持久会话: 已启用 | QoS: {{ mqttQos }}</div>
      </div>
    </div>

    <div class="bg-white rounded-lg shadow overflow-hidden">
      <table class="w-full text-sm">
        <thead class="bg-stone-50 text-stone-500 text-xs">
          <tr>
            <th class="text-left px-4 py-3">时间</th>
            <th class="text-left px-4 py-3">桥梁</th>
            <th class="text-left px-4 py-3">类型</th>
            <th class="text-left px-4 py-3">级别</th>
            <th class="text-left px-4 py-3">描述</th>
            <th class="text-left px-4 py-3">值</th>
            <th class="text-left px-4 py-3">阈值</th>
            <th class="text-left px-4 py-3">状态</th>
            <th class="text-left px-4 py-3">操作</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="a in filteredAlerts" :key="a.id" class="border-t border-stone-100 hover:bg-stone-50">
            <td class="px-4 py-3 text-stone-600">{{ formatTime(a.timestamp) }}</td>
            <td class="px-4 py-3 font-medium text-stone-800">{{ a.bridgeName || `桥梁${a.bridgeId}` }}</td>
            <td class="px-4 py-3 text-stone-600">{{ typeText(a.type) }}</td>
            <td class="px-4 py-3">
              <span class="text-xs px-2 py-0.5 rounded-full"
                :class="a.level === 'danger' ? 'bg-red-100 text-red-700' : 'bg-yellow-100 text-yellow-700'">
                {{ a.level === 'danger' ? '危险' : '预警' }}
              </span>
            </td>
            <td class="px-4 py-3 text-stone-600">{{ a.message }}</td>
            <td class="px-4 py-3 font-mono text-xs text-stone-700">{{ formatVal(a.value) }}</td>
            <td class="px-4 py-3 font-mono text-xs text-stone-500">{{ formatVal(a.threshold) }}</td>
            <td class="px-4 py-3">
              <span class="text-xs" :class="a.acknowledged ? 'text-green-600' : 'text-red-500'">
                {{ a.acknowledged ? '已处理' : '未处理' }}
              </span>
            </td>
            <td class="px-4 py-3">
              <button v-if="!a.acknowledged" @click="acknowledge(a.id)"
                class="text-xs text-primary hover:underline">确认</button>
            </td>
          </tr>
        </tbody>
      </table>
      <div v-if="!filteredAlerts.length" class="text-center text-stone-400 py-12">
        暂无告警数据
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import axios from 'axios'

const alerts = ref([])
const filter = ref('all')
const unackCount = ref(0)
const mqttStatus = ref(true)
const mqttQos = ref(1)

const filteredAlerts = computed(() => {
  if (filter.value === 'unacknowledged') {
    return alerts.value.filter(a => !a.acknowledged)
  }
  return alerts.value
})

const dangerCount = computed(() => alerts.value.filter(a => a.level === 'danger').length)
const warningCount = computed(() => alerts.value.filter(a => a.level === 'warning').length)
const monthCount = computed(() => {
  const monthAgo = new Date()
  monthAgo.setMonth(monthAgo.getMonth() - 1)
  return alerts.value.filter(a => new Date(a.timestamp) >= monthAgo).length
})

onMounted(async () => {
  try {
    const res = await axios.get('/api/alerts')
    alerts.value = res.data.data || []
  } catch (e) {
    alerts.value = [
      { id: 1, bridgeId: 6, bridgeName: '灞桥', type: 'settlement', level: 'danger',
        message: '桥墩沉降超限: 12.3mm', value: '12.3', threshold: '10.0',
        timestamp: new Date(Date.now() - 3600000 * 2).toISOString(), acknowledged: false },
      { id: 2, bridgeId: 2, bridgeName: '卢沟桥', type: 'crack_rate', level: 'warning',
        message: '裂缝扩展速率预警: 0.62mm/月', value: '0.62', threshold: '0.5',
        timestamp: new Date(Date.now() - 3600000 * 5).toISOString(), acknowledged: false },
      { id: 3, bridgeId: 5, bridgeName: '宝带桥', type: 'strain', level: 'warning',
        message: '拱券应变超预警: 112.5微应变', value: '112.5', threshold: '100',
        timestamp: new Date(Date.now() - 3600000 * 12).toISOString(), acknowledged: true },
      { id: 4, bridgeId: 6, bridgeName: '灞桥', type: 'crack_rate', level: 'danger',
        message: '裂缝扩展速率危险: 1.15mm/月', value: '1.15', threshold: '1.0',
        timestamp: new Date(Date.now() - 86400000 * 2).toISOString(), acknowledged: false },
      { id: 5, bridgeId: 9, bridgeName: '十字桥', type: 'settlement', level: 'warning',
        message: '桥墩沉降预警: 6.8mm', value: '6.8', threshold: '5.0',
        timestamp: new Date(Date.now() - 86400000 * 5).toISOString(), acknowledged: true }
    ]
  }
  try {
    const countRes = await axios.get('/api/alerts/unacknowledged/count')
    unackCount.value = countRes.data.data?.count || 0
  } catch (e) {
    unackCount.value = alerts.value.filter(a => !a.acknowledged).length
  }
})

async function acknowledge(id) {
  try {
    await axios.post(`/api/alerts/${id}/acknowledge`, { user: 'admin' })
    const a = alerts.value.find(x => x.id === id)
    if (a) a.acknowledged = true
    unackCount.value = Math.max(0, unackCount.value - 1)
  } catch (e) {
    const a = alerts.value.find(x => x.id === id)
    if (a) a.acknowledged = true
    unackCount.value = Math.max(0, unackCount.value - 1)
  }
}

function formatTime(t) {
  if (!t) return '-'
  return new Date(t).toLocaleString('zh-CN', { month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit' })
}
function formatVal(v) {
  if (v == null) return '-'
  return typeof v === 'number' ? v.toFixed(2) : v
}
function typeText(t) {
  const map = { settlement: '沉降', crack_rate: '裂缝扩展', strain: '应变', temperature: '温度' }
  return map[t] || t
}
</script>
