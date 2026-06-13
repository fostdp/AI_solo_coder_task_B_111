<template>
  <div class="p-6 space-y-6">
    <div class="flex justify-between items-center">
      <div>
        <h1 class="text-2xl font-bold text-stone-800">监测总览</h1>
        <p class="text-sm text-stone-500">实时掌握10座古代石拱桥健康状态</p>
      </div>
      <div class="text-right">
        <div class="text-xs text-stone-400">最近更新</div>
        <div class="text-sm font-medium text-stone-700">{{ lastUpdate }}</div>
      </div>
    </div>

    <div class="grid grid-cols-4 gap-4">
      <div v-for="c in cards" :key="c.label"
        class="bg-white rounded-lg shadow p-4 border-l-4"
        :style="{ borderLeftColor: c.color }">
        <div class="text-xs text-stone-500">{{ c.label }}</div>
        <div class="text-3xl font-bold mt-1" :style="{ color: c.color }">{{ c.value }}</div>
        <div class="text-xs text-stone-400 mt-2">{{ c.desc }}</div>
      </div>
    </div>

    <div class="grid grid-cols-3 gap-6">
      <div class="col-span-2 bg-white rounded-lg shadow p-4">
        <h2 class="text-base font-semibold mb-3 text-stone-700">桥梁健康评分分布</h2>
        <div ref="healthChart" class="h-64"></div>
      </div>
      <div class="bg-white rounded-lg shadow p-4">
        <h2 class="text-base font-semibold mb-3 text-stone-700">最新告警</h2>
        <div class="space-y-2">
          <div v-for="a in recentAlerts" :key="a.id"
            class="flex items-center justify-between p-2 rounded text-sm"
            :class="a.level === 'danger' ? 'bg-red-50' : 'bg-yellow-50'">
            <div>
              <div class="font-medium text-stone-700">{{ a.bridgeName || `桥梁${a.bridgeId}` }}</div>
              <div class="text-xs text-stone-500">{{ a.message }}</div>
            </div>
            <span class="text-xs px-2 py-0.5 rounded-full"
              :class="a.level === 'danger' ? 'bg-red-500 text-white' : 'bg-yellow-500 text-white'">
              {{ a.level === 'danger' ? '危险' : '预警' }}
            </span>
          </div>
        </div>
      </div>
    </div>

    <div class="bg-white rounded-lg shadow p-4">
      <h2 class="text-base font-semibold mb-3 text-stone-700">桥梁列表</h2>
      <div class="overflow-x-auto">
        <table class="w-full text-sm">
          <thead class="bg-stone-50 text-stone-500">
            <tr>
              <th class="text-left px-4 py-2">桥梁名称</th>
              <th class="text-left px-4 py-2">位置</th>
              <th class="text-left px-4 py-2">建成年代</th>
              <th class="text-left px-4 py-2">传感器数</th>
              <th class="text-left px-4 py-2">健康评分</th>
              <th class="text-left px-4 py-2">状态</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="b in bridges" :key="b.id" class="border-t border-stone-100 hover:bg-stone-50 cursor-pointer"
              @click="$router.push('/bridge?bridgeId=' + b.id)">
              <td class="px-4 py-3 font-medium text-stone-800">{{ b.name }}</td>
              <td class="px-4 py-3 text-stone-600">{{ b.location || '-' }}</td>
              <td class="px-4 py-3 text-stone-600">{{ b.builtYear || '-' }}年</td>
              <td class="px-4 py-3 text-stone-600">{{ b.sensorCount || '-' }}</td>
              <td class="px-4 py-3">
                <div class="flex items-center gap-2">
                  <div class="w-20 h-2 bg-stone-200 rounded-full overflow-hidden">
                    <div class="h-full" :class="healthColor(b.healthScore)"
                      :style="{ width: (b.healthScore || 0) + '%' }"></div>
                  </div>
                  <span class="text-xs text-stone-600">{{ b.healthScore || 0 }}</span>
                </div>
              </td>
              <td class="px-4 py-3">
                <span class="text-xs px-2 py-0.5 rounded-full"
                  :class="statusClass(b.status)">{{ statusText(b.status) }}</span>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted, nextTick } from 'vue'
import * as echarts from 'echarts'
import axios from 'axios'

const healthChart = ref(null)
const bridges = ref([])
const recentAlerts = ref([])
const lastUpdate = ref('加载中...')
let chartInstance = null

const cards = ref([
  { label: '监测桥梁', value: 10, desc: '座古代石拱桥', color: '#1e3a8a' },
  { label: '传感器', value: 168, desc: '台在线设备', color: '#059669' },
  { label: '告警', value: 3, desc: '条未处理', color: '#dc2626' },
  { label: '平均健康分', value: 85.6, desc: '整体良好', color: '#d97706' }
])

const defaultBridges = [
  { id: 1, name: '赵州桥', location: '河北赵县', builtYear: 605, sensorCount: 18, healthScore: 92, status: 'healthy' },
  { id: 2, name: '卢沟桥', location: '北京丰台', builtYear: 1192, sensorCount: 24, healthScore: 78, status: 'warning' },
  { id: 3, name: '广济桥', location: '广东潮州', builtYear: 1171, sensorCount: 22, healthScore: 85, status: 'healthy' },
  { id: 4, name: '洛阳桥', location: '福建泉州', builtYear: 1059, sensorCount: 20, healthScore: 88, status: 'healthy' },
  { id: 5, name: '宝带桥', location: '江苏苏州', builtYear: 816, sensorCount: 28, healthScore: 72, status: 'warning' },
  { id: 6, name: '灞桥', location: '陕西西安', builtYear: 583, sensorCount: 16, healthScore: 65, status: 'danger' },
  { id: 7, name: '安平桥', location: '福建晋江', builtYear: 1152, sensorCount: 26, healthScore: 82, status: 'healthy' },
  { id: 8, name: '五亭桥', location: '江苏扬州', builtYear: 1757, sensorCount: 14, healthScore: 90, status: 'healthy' },
  { id: 9, name: '十字桥', location: '山西太原', builtYear: 1102, sensorCount: 12, healthScore: 76, status: 'warning' },
  { id: 10, name: '风雨桥', location: '广西三江', builtYear: 1916, sensorCount: 18, healthScore: 86, status: 'healthy' }
]

onMounted(async () => {
  try {
    const res = await axios.get('/api/bridges')
    bridges.value = res.data.data?.length ? res.data.data : defaultBridges
  } catch (e) {
    bridges.value = defaultBridges
  }
  try {
    const alertsRes = await axios.get('/api/alerts/unacknowledged')
    recentAlerts.value = (alertsRes.data.data || []).slice(0, 5)
  } catch (e) {
    recentAlerts.value = [
      { id: 1, bridgeId: 6, bridgeName: '灞桥', level: 'danger', message: '桥墩沉降超限 12.3mm' },
      { id: 2, bridgeId: 2, bridgeName: '卢沟桥', level: 'warning', message: '裂缝扩展速率 0.62mm/月' },
      { id: 3, bridgeId: 5, bridgeName: '宝带桥', level: 'warning', message: '拱券应变 112μ ε超预警' }
    ]
  }
  lastUpdate.value = new Date().toLocaleString('zh-CN')

  nextTick(() => {
    if (healthChart.value) {
      chartInstance = echarts.init(healthChart.value)
      chartInstance.setOption({
        tooltip: { trigger: 'axis' },
        grid: { left: 30, right: 20, top: 30, bottom: 30 },
        xAxis: { type: 'category', data: bridges.value.map(b => b.name), axisLabel: { fontSize: 10, rotate: 30 } },
        yAxis: { type: 'value', min: 50, max: 100 },
        series: [{
          type: 'bar',
          data: bridges.value.map(b => ({
            value: b.healthScore,
            itemStyle: { color: b.healthScore >= 85 ? '#059669' : b.healthScore >= 70 ? '#d97706' : '#dc2626' }
          })),
          barWidth: 20
        }]
      })
    }
  })
})

function healthColor(score) {
  if (!score) return 'bg-stone-300'
  if (score >= 85) return 'bg-green-500'
  if (score >= 70) return 'bg-yellow-500'
  return 'bg-red-500'
}
function statusClass(status) {
  const map = {
    healthy: 'bg-green-100 text-green-700',
    warning: 'bg-yellow-100 text-yellow-700',
    danger: 'bg-red-100 text-red-700'
  }
  return map[status] || 'bg-stone-100 text-stone-700'
}
function statusText(status) {
  const map = { healthy: '健康', warning: '预警', danger: '危险' }
  return map[status] || '未知'
}
</script>
