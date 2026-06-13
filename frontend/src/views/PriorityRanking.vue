<template>
  <div class="p-6 space-y-6">
    <div class="flex justify-between items-center">
      <div>
        <h1 class="text-2xl font-bold text-stone-800">多桥对比与保护优先级排序</h1>
        <p class="text-sm text-stone-500">TOPSIS多属性决策 · 年度保护计划生成</p>
      </div>
      <div class="flex gap-2">
        <select v-model.number="selectedYear" @change="loadData"
          class="border border-stone-300 rounded px-3 py-1.5 text-sm">
          <option v-for="y in availableYears" :key="y" :value="y">{{ y }}年度</option>
        </select>
        <button @click="onCalculate"
          class="bg-primary text-white rounded px-4 py-1.5 text-sm font-medium hover:bg-blue-900 transition">
          重新计算排序
        </button>
      </div>
    </div>

    <div v-if="showConfig" class="bg-white rounded-lg shadow p-6">
      <h2 class="text-base font-semibold mb-4 text-stone-700">TOPSIS计算参数</h2>
      <div class="grid grid-cols-5 gap-4 mb-4">
        <div v-for="(w, key) in weights" :key="key" class="space-y-1">
          <label class="block text-xs text-stone-500">{{ getWeightName(key) }}</label>
          <input type="number" step="0.05" v-model.number="weights[key]"
            class="w-full border border-stone-300 rounded px-2 py-1 text-sm" />
        </div>
      </div>
      <div class="flex gap-2">
        <label class="flex items-center gap-2 text-sm text-stone-700">
          <input type="checkbox" v-model="generatePlan" />
          <span>生成年度保护计划</span>
        </label>
      </div>
      <div class="flex gap-2 mt-4">
        <button @click="confirmCalculate"
          class="bg-primary text-white rounded px-4 py-1.5 text-sm font-medium hover:bg-blue-900 transition">
          确认计算
        </button>
        <button @click="showConfig = false"
          class="bg-stone-200 text-stone-700 rounded px-4 py-1.5 text-sm font-medium hover:bg-stone-300 transition">
          取消
        </button>
      </div>
    </div>

    <div class="grid grid-cols-4 gap-4">
      <div class="bg-white rounded-lg shadow p-4">
        <div class="text-xs text-stone-500">参与排序桥梁数</div>
        <div class="text-2xl font-bold text-stone-800">{{ rankings.length }}</div>
      </div>
      <div class="bg-white rounded-lg shadow p-4">
        <div class="text-xs text-stone-500">急需保护(立即处理)</div>
        <div class="text-2xl font-bold text-red-600">{{ urgentCount.immediate }}</div>
      </div>
      <div class="bg-white rounded-lg shadow p-4">
        <div class="text-xs text-stone-500">紧急保护</div>
        <div class="text-2xl font-bold text-orange-600">{{ urgentCount.urgent }}</div>
      </div>
      <div class="bg-white rounded-lg shadow p-4">
        <div class="text-xs text-stone-500">年度总预算</div>
        <div class="text-2xl font-bold text-green-600">¥{{ formatBudget(totalBudget) }}</div>
      </div>
    </div>

    <div class="grid grid-cols-3 gap-6">
      <div class="col-span-2 bg-white rounded-lg shadow p-4">
        <h2 class="text-base font-semibold mb-3 text-stone-700">TOPSIS综合评分排名</h2>
        <div ref="radarChart" class="h-80"></div>
      </div>

      <div class="bg-white rounded-lg shadow p-4">
        <h2 class="text-base font-semibold mb-3 text-stone-700">权重分布</h2>
        <div ref="weightChart" class="h-80"></div>
      </div>
    </div>

    <div class="bg-white rounded-lg shadow p-4">
      <h2 class="text-base font-semibold mb-3 text-stone-700">保护优先级排序</h2>
      <div class="overflow-x-auto">
        <table class="w-full text-sm">
          <thead>
            <tr class="bg-stone-50">
              <th class="px-3 py-2 text-center text-xs font-medium text-stone-500">排名</th>
              <th class="px-3 py-2 text-left text-xs font-medium text-stone-500">桥梁名称</th>
              <th class="px-3 py-2 text-center text-xs font-medium text-stone-500">TOPSIS得分</th>
              <th class="px-3 py-2 text-center text-xs font-medium text-stone-500">结构安全</th>
              <th class="px-3 py-2 text-center text-xs font-medium text-stone-500">损伤趋势</th>
              <th class="px-3 py-2 text-center text-xs font-medium text-stone-500">风化程度</th>
              <th class="px-3 py-2 text-center text-xs font-medium text-stone-500">交通影响</th>
              <th class="px-3 py-2 text-center text-xs font-medium text-stone-500">历史价值</th>
              <th class="px-3 py-2 text-center text-xs font-medium text-stone-500">紧急程度</th>
              <th class="px-3 py-2 text-center text-xs font-medium text-stone-500">预估费用</th>
              <th class="px-3 py-2 text-center text-xs font-medium text-stone-500">优先级</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="r in rankings" :key="r.bridgeId" class="border-t border-stone-100 hover:bg-stone-50">
              <td class="px-3 py-2 text-center">
                <span class="inline-flex items-center justify-center w-6 h-6 rounded-full text-xs font-bold"
                  :class="r.ranking <= 3 ? 'bg-red-100 text-red-700' : r.ranking <= 6 ? 'bg-orange-100 text-orange-700' : 'bg-stone-100 text-stone-700'">
                  {{ r.ranking }}
                </span>
              </td>
              <td class="px-3 py-2 font-medium">{{ r.bridgeName }}</td>
              <td class="px-3 py-2 text-center font-mono">{{ formatNum(r.topsisScore, 4) }}</td>
              <td class="px-3 py-2 text-center" :class="scoreClass(r.structureSafetyScore)">{{ formatPercent(r.structureSafetyScore) }}</td>
              <td class="px-3 py-2 text-center" :class="scoreClass(r.damageTrendScore)">{{ formatPercent(r.damageTrendScore) }}</td>
              <td class="px-3 py-2 text-center" :class="scoreClass(r.weatheringScore)">{{ formatPercent(r.weatheringScore) }}</td>
              <td class="px-3 py-2 text-center" :class="scoreClass(r.trafficImpactScore)">{{ formatPercent(r.trafficImpactScore) }}</td>
              <td class="px-3 py-2 text-center" :class="scoreClass(r.historicalValueScore)">{{ formatPercent(r.historicalValueScore) }}</td>
              <td class="px-3 py-2 text-center">
                <span class="px-2 py-0.5 rounded text-xs font-medium"
                  :class="urgencyClass(r.maintenanceUrgency)">
                  {{ urgencyText(r.maintenanceUrgency) }}
                </span>
              </td>
              <td class="px-3 py-2 text-center font-medium">¥{{ formatBudget(r.estimatedCost) }}</td>
              <td class="px-3 py-2 text-center">
                <span class="px-2 py-0.5 rounded text-xs font-medium"
                  :class="priorityClass(r.priorityLevel)">
                  {{ priorityText(r.priorityLevel) }}
                </span>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
    </div>

    <div class="bg-white rounded-lg shadow p-4">
      <h2 class="text-base font-semibold mb-3 text-stone-700">{{ selectedYear }}年度保护计划</h2>
      <div class="overflow-x-auto">
        <table class="w-full text-sm">
          <thead>
            <tr class="bg-stone-50">
              <th class="px-3 py-2 text-center text-xs font-medium text-stone-500">优先级</th>
              <th class="px-3 py-2 text-left text-xs font-medium text-stone-500">桥梁名称</th>
              <th class="px-3 py-2 text-left text-xs font-medium text-stone-500">项目名称</th>
              <th class="px-3 py-2 text-left text-xs font-medium text-stone-500">工程类型</th>
              <th class="px-3 py-2 text-center text-xs font-medium text-stone-500">时间安排</th>
              <th class="px-3 py-2 text-right text-xs font-medium text-stone-500">预估预算</th>
              <th class="px-3 py-2 text-center text-xs font-medium text-stone-500">状态</th>
              <th class="px-3 py-2 text-left text-xs font-medium text-stone-500">说明</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="p in protectionPlan" :key="p.id" class="border-t border-stone-100 hover:bg-stone-50">
              <td class="px-3 py-2 text-center font-medium">{{ p.priorityRanking }}</td>
              <td class="px-3 py-2 font-medium">{{ p.bridgeName }}</td>
              <td class="px-3 py-2">{{ p.projectName }}</td>
              <td class="px-3 py-2">{{ getProjectTypeText(p.projectType) }}</td>
              <td class="px-3 py-2 text-center">{{ p.timeline }}</td>
              <td class="px-3 py-2 text-right font-medium">¥{{ formatBudget(p.estimatedBudget) }}</td>
              <td class="px-3 py-2 text-center">
                <span class="px-2 py-0.5 rounded text-xs font-medium"
                  :class="statusClass(p.status)">
                  {{ statusText(p.status) }}
                </span>
              </td>
              <td class="px-3 py-2 text-xs text-stone-500 max-w-xs truncate" :title="p.description">
                {{ p.description }}
              </td>
            </tr>
          </tbody>
          <tfoot>
            <tr class="bg-stone-50 font-semibold">
              <td class="px-3 py-2 text-right" colspan="5">年度总预算：</td>
              <td class="px-3 py-2 text-right text-green-600">¥{{ formatBudget(totalBudget) }}</td>
              <td class="px-3 py-2" colspan="2"></td>
            </tr>
          </tfoot>
        </table>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted, onUnmounted, watch } from 'vue'
import * as echarts from 'echarts'

const rankings = ref([])
const protectionPlan = ref([])
const availableYears = ref([2026, 2025])
const selectedYear = ref(2026)
const showConfig = ref(false)
const generatePlan = ref(true)
const radarChart = ref(null)
const weightChart = ref(null)
let radarInstance = null
let weightInstance = null

const weights = reactive({
  'structure-safety': 0.30,
  'damage-trend': 0.25,
  'weathering': 0.15,
  'traffic-impact': 0.15,
  'historical-value': 0.15
})

const urgentCount = computed(() => {
  return {
    immediate: rankings.value.filter(r => r.maintenanceUrgency === 'immediate').length,
    urgent: rankings.value.filter(r => r.maintenanceUrgency === 'urgent').length,
    normal: rankings.value.filter(r => r.maintenanceUrgency === 'normal').length,
    low: rankings.value.filter(r => r.maintenanceUrgency === 'low').length
  }
})

const totalBudget = computed(() => {
  return protectionPlan.value.reduce((sum, p) => sum + (p.estimatedBudget || 0), 0)
})

function formatNum(v, decimals = 2) {
  if (v == null) return '-'
  return Number(v).toFixed(decimals)
}

function formatPercent(v) {
  if (v == null) return '-'
  return (Number(v) * 100).toFixed(0) + '%'
}

function formatBudget(v) {
  if (v == null) return '-'
  if (v >= 10000) {
    return (v / 10000).toFixed(2) + '万'
  }
  return Number(v).toFixed(0)
}

function getWeightName(key) {
  const map = {
    'structure-safety': '结构安全度',
    'damage-trend': '损伤演化趋势',
    'weathering': '风化程度',
    'traffic-impact': '交通影响',
    'historical-value': '历史价值'
  }
  return map[key] || key
}

function getProjectTypeText(type) {
  const map = {
    'structural_reinforcement': '结构加固',
    'crack_repair': '裂缝修复',
    'weathering_protection': '防风化保护',
    'vibration_control': '减振控制',
    'comprehensive_maintenance': '综合养护'
  }
  return map[type] || type
}

function scoreClass(score) {
  if (score >= 0.7) return 'text-red-600 font-medium'
  if (score >= 0.4) return 'text-yellow-600'
  return 'text-green-600'
}

function urgencyClass(urgency) {
  const map = {
    immediate: 'bg-red-100 text-red-700',
    urgent: 'bg-orange-100 text-orange-700',
    normal: 'bg-yellow-100 text-yellow-700',
    low: 'bg-green-100 text-green-700'
  }
  return map[urgency] || 'bg-stone-100 text-stone-700'
}

function urgencyText(urgency) {
  const map = {
    immediate: '立即处理',
    urgent: '紧急',
    normal: '一般',
    low: '低'
  }
  return map[urgency] || urgency
}

function priorityClass(level) {
  const map = {
    critical: 'bg-red-100 text-red-700',
    high: 'bg-orange-100 text-orange-700',
    normal: 'bg-stone-100 text-stone-700'
  }
  return map[level] || 'bg-stone-100 text-stone-700'
}

function priorityText(level) {
  const map = {
    critical: '特级',
    high: '重点',
    normal: '常规'
  }
  return map[level] || level
}

function statusClass(status) {
  const map = {
    planned: 'bg-blue-100 text-blue-700',
    in_progress: 'bg-yellow-100 text-yellow-700',
    completed: 'bg-green-100 text-green-700',
    deferred: 'bg-stone-100 text-stone-700'
  }
  return map[status] || 'bg-stone-100 text-stone-700'
}

function statusText(status) {
  const map = {
    planned: '计划中',
    in_progress: '进行中',
    completed: '已完成',
    deferred: '延后'
  }
  return map[status] || status || '计划中'
}

async function loadData() {
  try {
    const [rankRes, planRes, yearsRes] = await Promise.all([
      fetch(`/api/priority/rankings?planYear=${selectedYear.value}`),
      fetch(`/api/priority/plan?planYear=${selectedYear.value}`),
      fetch('/api/priority/years')
    ])

    const rankData = await rankRes.json()
    const planData = await planRes.json()
    const yearsData = await yearsRes.json()

    if (rankData.code === 200) rankings.value = rankData.data || []
    if (planData.code === 200) protectionPlan.value = planData.data || []
    if (yearsData.code === 200) availableYears.value = yearsData.data || [2026]

    updateCharts()
  } catch (e) {
    console.error('加载数据失败', e)
  }
}

function onCalculate() {
  showConfig.value = true
}

async function confirmCalculate() {
  try {
    const payload = {
      planYear: selectedYear.value,
      weights: { ...weights },
      generateProtectionPlan: generatePlan.value
    }

    const res = await fetch('/api/priority/calculate', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(payload)
    })

    const data = await res.json()
    if (data.code === 200) {
      showConfig.value = false
      await loadData()
    }
  } catch (e) {
    console.error('计算失败', e)
  }
}

function updateCharts() {
  if (rankings.value.length === 0) return

  if (radarChart.value) {
    if (!radarInstance) {
      radarInstance = echarts.init(radarChart.value)
    }

    const top5 = rankings.value.slice(0, 5)
    const indicators = [
      { name: '结构安全', max: 1 },
      { name: '损伤趋势', max: 1 },
      { name: '风化程度', max: 1 },
      { name: '交通影响', max: 1 },
      { name: '历史价值', max: 1 }
    ]

    const colors = ['#ef4444', '#f97316', '#eab308', '#3b82f6', '#22c55e']

    const option = {
      tooltip: { trigger: 'item' },
      legend: {
        data: top5.map(r => r.bridgeName),
        bottom: 0
      },
      radar: {
        indicator: indicators,
        radius: '60%',
        center: ['50%', '50%']
      },
      series: [{
        type: 'radar',
        data: top5.map((r, i) => ({
          name: r.bridgeName,
          value: [
            r.structureSafetyScore,
            r.damageTrendScore,
            r.weatheringScore,
            r.trafficImpactScore,
            r.historicalValueScore
          ],
          lineStyle: { color: colors[i] },
          areaStyle: { color: colors[i], opacity: 0.1 }
        }))
      }]
    }
    radarInstance.setOption(option)
  }

  if (weightChart.value) {
    if (!weightInstance) {
      weightInstance = echarts.init(weightChart.value)
    }

    const option = {
      tooltip: { trigger: 'item', formatter: '{b}: {d}%' },
      series: [{
        type: 'pie',
        radius: ['40%', '70%'],
        data: Object.keys(weights).map(key => ({
          name: getWeightName(key),
          value: weights[key]
        })),
        label: {
          formatter: '{b}\n{d}%'
        }
      }]
    }
    weightInstance.setOption(option)
  }
}

function resize() {
  radarInstance?.resize()
  weightInstance?.resize()
}

onMounted(async () => {
  await loadData()
  window.addEventListener('resize', resize)
})

watch([radarChart, weightChart], () => {
  if (rankings.value.length > 0) updateCharts()
})

onUnmounted(() => {
  window.removeEventListener('resize', resize)
  radarInstance?.dispose()
  weightInstance?.dispose()
})
</script>
