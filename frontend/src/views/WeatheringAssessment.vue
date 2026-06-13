<template>
  <div class="p-6 space-y-6">
    <div>
      <h1 class="text-2xl font-bold text-stone-800">石材风化深度评估</h1>
      <p class="text-sm text-stone-500">表面硬度计 + 超声波速 · 多元回归模型</p>
    </div>

    <div class="grid grid-cols-3 gap-6">
      <div class="bg-white rounded-lg shadow p-4">
        <h2 class="text-base font-semibold mb-4 text-stone-700">评估参数</h2>
        <div class="space-y-3">
          <div>
            <label class="block text-xs text-stone-500 mb-1">选择桥梁</label>
            <select v-model.number="form.bridgeId" @change="onBridgeChange"
              class="w-full border border-stone-300 rounded px-2 py-1.5 text-sm">
              <option v-for="b in bridges" :key="b.id" :value="b.id">{{ b.name }}</option>
            </select>
          </div>
          <div>
            <label class="block text-xs text-stone-500 mb-1">测量点数</label>
            <input type="number" v-model.number="form.pointCount"
              class="w-full border border-stone-300 rounded px-2 py-1.5 text-sm" />
          </div>
          <div>
            <label class="block text-xs text-stone-500 mb-1">硬度系数 aH</label>
            <input type="number" step="0.01" v-model.number="form.hardnessCoefficient"
              class="w-full border border-stone-300 rounded px-2 py-1.5 text-sm" />
          </div>
          <div>
            <label class="block text-xs text-stone-500 mb-1">波速系数 aV</label>
            <input type="number" step="0.01" v-model.number="form.velocityCoefficient"
              class="w-full border border-stone-300 rounded px-2 py-1.5 text-sm" />
          </div>
          <div>
            <label class="block text-xs text-stone-500 mb-1">截距 b</label>
            <input type="number" step="0.1" v-model.number="form.intercept"
              class="w-full border border-stone-300 rounded px-2 py-1.5 text-sm" />
          </div>
          <div>
            <label class="flex items-center gap-2 text-sm text-stone-700">
              <input type="checkbox" v-model="form.useSimulatedData" />
              <span>使用模拟测量数据</span>
            </label>
          </div>
          <div v-if="!form.useSimulatedData" class="space-y-2">
            <div class="text-xs text-stone-500 font-medium">手动输入测量点</div>
            <div v-for="(p, idx) in form.measurements" :key="idx"
              class="grid grid-cols-3 gap-1 text-xs">
              <input type="number" step="0.1" v-model.number="p.hardness" placeholder="硬度"
                class="border border-stone-300 rounded px-1 py-0.5" />
              <input type="number" step="0.01" v-model.number="p.velocity" placeholder="波速"
                class="border border-stone-300 rounded px-1 py-0.5" />
              <input type="text" v-model="p.location" placeholder="位置"
                class="border border-stone-300 rounded px-1 py-0.5" />
            </div>
            <button @click="addMeasurement"
              class="w-full text-xs text-primary border border-primary rounded py-1 hover:bg-blue-50">
              + 添加测量点
            </button>
          </div>
          <button @click="onEvaluate"
            class="w-full bg-primary text-white rounded py-2 text-sm font-medium hover:bg-blue-900 transition">
            开始评估
          </button>
        </div>
      </div>

      <div class="col-span-2 space-y-6">
        <div v-if="result" class="grid grid-cols-4 gap-4">
          <div class="bg-white rounded-lg shadow p-4">
            <div class="text-xs text-stone-500">平均风化深度</div>
            <div class="text-xl font-bold" :class="depthClass(result.avgDepth)">
              {{ formatNum(result.avgDepth) }} mm
            </div>
          </div>
          <div class="bg-white rounded-lg shadow p-4">
            <div class="text-xs text-stone-500">最大风化深度</div>
            <div class="text-xl font-bold" :class="depthClass(result.maxDepth)">
              {{ formatNum(result.maxDepth) }} mm
            </div>
          </div>
          <div class="bg-white rounded-lg shadow p-4">
            <div class="text-xs text-stone-500">综合风化等级</div>
            <div class="text-xl font-bold" :class="gradeClass(result.overallGrade)">
              {{ gradeText(result.overallGrade) }}
            </div>
          </div>
          <div class="bg-white rounded-lg shadow p-4">
            <div class="text-xs text-stone-500">回归拟合度 R²</div>
            <div class="text-xl font-bold text-stone-800">
              {{ formatNum(result.rSquared) }}
            </div>
          </div>
        </div>

        <div class="bg-white rounded-lg shadow p-4">
          <h2 class="text-base font-semibold mb-3 text-stone-700">风化深度分布</h2>
          <div ref="depthChart" class="h-64"></div>
        </div>

        <div class="bg-white rounded-lg shadow p-4">
          <h2 class="text-base font-semibold mb-3 text-stone-700">风化等级分布</h2>
          <div ref="gradeChart" class="h-48"></div>
        </div>

        <div v-if="result" class="bg-white rounded-lg shadow p-4">
          <h2 class="text-base font-semibold mb-3 text-stone-700">测量点详情</h2>
          <div class="overflow-x-auto">
            <table class="w-full text-sm">
              <thead>
                <tr class="bg-stone-50">
                  <th class="px-3 py-2 text-left text-xs font-medium text-stone-500">位置</th>
                  <th class="px-3 py-2 text-left text-xs font-medium text-stone-500">表面硬度(HLD)</th>
                  <th class="px-3 py-2 text-left text-xs font-medium text-stone-500">超声波速(km/s)</th>
                  <th class="px-3 py-2 text-left text-xs font-medium text-stone-500">估算深度(mm)</th>
                  <th class="px-3 py-2 text-left text-xs font-medium text-stone-500">风化等级</th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="(p, idx) in result.points" :key="idx" class="border-t border-stone-100">
                  <td class="px-3 py-2">{{ p.location }}</td>
                  <td class="px-3 py-2">{{ formatNum(p.surfaceHardness) }}</td>
                  <td class="px-3 py-2">{{ formatNum(p.ultrasonicVelocity) }}</td>
                  <td class="px-3 py-2" :class="depthClass(p.estimatedDepth)">{{ formatNum(p.estimatedDepth) }}</td>
                  <td class="px-3 py-2">
                    <span class="px-2 py-0.5 rounded text-xs font-medium"
                      :class="gradeBadgeClass(p.weatheringGrade)">
                      {{ gradeText(p.weatheringGrade) }}
                    </span>
                  </td>
                </tr>
              </tbody>
            </table>
          </div>
        </div>

        <div v-if="result" class="bg-white rounded-lg shadow p-4">
          <h2 class="text-base font-semibold mb-3 text-stone-700">保护建议</h2>
          <div class="text-sm text-stone-600 leading-relaxed">
            {{ generateRecommendation() }}
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted, onUnmounted, watch } from 'vue'
import * as echarts from 'echarts'

const bridges = ref([])
const result = ref(null)
const gradeDistribution = ref({})
const depthChart = ref(null)
const gradeChart = ref(null)
let depthChartInstance = null
let gradeChartInstance = null

const form = reactive({
  bridgeId: 1,
  pointCount: 15,
  hardnessCoefficient: 0.15,
  velocityCoefficient: 8.0,
  intercept: 25.0,
  useSimulatedData: true,
  measurements: [
    { hardness: 45, velocity: 3.5, location: '拱顶左侧' },
    { hardness: 38, velocity: 3.0, location: '拱顶右侧' },
    { hardness: 52, velocity: 4.0, location: '拱脚左侧' }
  ]
})

function formatNum(v) {
  if (v == null) return '-'
  return Number(v).toFixed(2)
}

function gradeText(grade) {
  const map = { none: '无风化', slight: '轻微', moderate: '中等', severe: '严重', critical: '危急' }
  return map[grade] || grade
}

function gradeClass(grade) {
  const map = {
    none: 'text-green-600',
    slight: 'text-blue-600',
    moderate: 'text-yellow-600',
    severe: 'text-orange-600',
    critical: 'text-red-600'
  }
  return map[grade] || 'text-stone-600'
}

function gradeBadgeClass(grade) {
  const map = {
    none: 'bg-green-100 text-green-700',
    slight: 'bg-blue-100 text-blue-700',
    moderate: 'bg-yellow-100 text-yellow-700',
    severe: 'bg-orange-100 text-orange-700',
    critical: 'bg-red-100 text-red-700'
  }
  return map[grade] || 'bg-stone-100 text-stone-700'
}

function depthClass(depth) {
  if (depth <= 2) return 'text-green-600'
  if (depth <= 5) return 'text-blue-600'
  if (depth <= 10) return 'text-yellow-600'
  if (depth <= 20) return 'text-orange-600'
  return 'text-red-600'
}

function addMeasurement() {
  form.measurements.push({ hardness: 40, velocity: 3.5, location: '' })
}

async function onBridgeChange() {
  result.value = null
  await loadDistribution()
}

async function loadBridges() {
  try {
    const res = await fetch('/api/bridge/list')
    const data = await res.json()
    if (data.code === 200) {
      bridges.value = data.data
    }
  } catch (e) {
    console.error('加载桥梁列表失败', e)
  }
}

async function loadDistribution() {
  try {
    const res = await fetch(`/api/weathering/distribution/${form.bridgeId}`)
    const data = await res.json()
    if (data.code === 200) {
      gradeDistribution.value = data.data || {}
    }
  } catch (e) {
    console.error('加载风化分布失败', e)
  }
}

async function onEvaluate() {
  try {
    const payload = {
      bridgeId: form.bridgeId,
      pointCount: form.pointCount,
      hardnessCoefficient: form.hardnessCoefficient,
      velocityCoefficient: form.velocityCoefficient,
      intercept: form.intercept,
      useSimulatedData: form.useSimulatedData,
      measurements: form.useSimulatedData ? null : form.measurements
    }

    const res = await fetch('/api/weathering/evaluate', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(payload)
    })

    const data = await res.json()
    if (data.code === 200) {
      result.value = data.data
      updateCharts()
      await loadDistribution()
    }
  } catch (e) {
    console.error('风化评估失败', e)
  }
}

function generateRecommendation() {
  if (!result.value) return ''
  const grade = result.value.overallGrade
  const avg = result.value.avgDepth

  const recs = {
    none: '石材状态良好，无明显风化。建议每2-3年进行一次定期检测，维持正常养护即可。',
    slight: '存在轻微风化现象。建议每年进行一次表面硬度和超声波检测，可考虑施加渗透型防风化保护剂。',
    moderate: '中等风化程度，部分区域风化较深。建议6个月内安排专业检测，对风化深度超过5mm的区域进行表面修复处理。',
    severe: '风化严重，石材表层劣化明显。建议立即启动防风化保护工程，采用表面包覆或化学加固等处理措施。',
    critical: '风化危急，石材结构性能受到严重影响。建议立即限制通行，组织专家论证，实施结构加固或石材更换工程。'
  }

  return `根据检测结果，该桥平均风化深度为${formatNum(avg)}mm，综合评定为${gradeText(grade)}。${recs[grade] || ''}`
}

function updateCharts() {
  if (!result.value) return

  if (depthChart.value) {
    if (!depthChartInstance) {
      depthChartInstance = echarts.init(depthChart.value)
    }

    const points = result.value.points
    const option = {
      tooltip: { trigger: 'axis' },
      xAxis: {
        type: 'category',
        data: points.map(p => p.location),
        axisLabel: { rotate: 45, fontSize: 10 }
      },
      yAxis: {
        type: 'value',
        name: '风化深度(mm)'
      },
      visualMap: {
        min: 0,
        max: 20,
        left: 'right',
        top: 'center',
        inRange: {
          color: ['#22c55e', '#3b82f6', '#eab308', '#f97316', '#ef4444']
        }
      },
      series: [{
        type: 'bar',
        data: points.map(p => ({
          value: p.estimatedDepth,
          itemStyle: {
            color: p.estimatedDepth <= 2 ? '#22c55e' :
                   p.estimatedDepth <= 5 ? '#3b82f6' :
                   p.estimatedDepth <= 10 ? '#eab308' :
                   p.estimatedDepth <= 20 ? '#f97316' : '#ef4444'
          }
        })),
        barWidth: '60%'
      }]
    }
    depthChartInstance.setOption(option)
  }

  if (gradeChart.value) {
    if (!gradeChartInstance) {
      gradeChartInstance = echarts.init(gradeChart.value)
    }

    const dist = gradeDistribution.value || {}
    const option = {
      tooltip: { trigger: 'item' },
      legend: { bottom: '0' },
      series: [{
        type: 'pie',
        radius: ['40%', '70%'],
        data: [
          { value: dist.none || 0, name: '无风化', itemStyle: { color: '#22c55e' } },
          { value: dist.slight || 0, name: '轻微', itemStyle: { color: '#3b82f6' } },
          { value: dist.moderate || 0, name: '中等', itemStyle: { color: '#eab308' } },
          { value: dist.severe || 0, name: '严重', itemStyle: { color: '#f97316' } },
          { value: dist.critical || 0, name: '危急', itemStyle: { color: '#ef4444' } }
        ],
        label: {
          formatter: '{b}: {c}处'
        }
      }]
    }
    gradeChartInstance.setOption(option)
  }
}

function resize() {
  depthChartInstance?.resize()
  gradeChartInstance?.resize()
}

onMounted(async () => {
  await loadBridges()
  await loadDistribution()
  window.addEventListener('resize', resize)
})

watch([depthChart, gradeChart], () => {
  if (result.value) updateCharts()
})

onUnmounted(() => {
  window.removeEventListener('resize', resize)
  depthChartInstance?.dispose()
  gradeChartInstance?.dispose()
})
</script>
