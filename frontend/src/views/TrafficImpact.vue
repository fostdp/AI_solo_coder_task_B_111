<template>
  <div class="p-6 space-y-6">
    <div>
      <h1 class="text-2xl font-bold text-stone-800">交通振动影响分析</h1>
      <p class="text-sm text-stone-500">车桥耦合振动模型 · 动力时程分析</p>
    </div>

    <div class="grid grid-cols-3 gap-6">
      <div class="bg-white rounded-lg shadow p-4">
        <h2 class="text-base font-semibold mb-4 text-stone-700">分析参数</h2>
        <div class="space-y-3">
          <div>
            <label class="block text-xs text-stone-500 mb-1">选择桥梁</label>
            <select v-model.number="form.bridgeId" @change="onBridgeChange"
              class="w-full border border-stone-300 rounded px-2 py-1.5 text-sm">
              <option v-for="b in bridges" :key="b.id" :value="b.id">{{ b.name }}</option>
            </select>
          </div>
          <div>
            <label class="block text-xs text-stone-500 mb-1">桥梁自振频率(Hz)</label>
            <input type="number" step="0.1" v-model.number="form.naturalFrequency"
              class="w-full border border-stone-300 rounded px-2 py-1.5 text-sm" />
          </div>
          <div>
            <label class="block text-xs text-stone-500 mb-1">阻尼比 ζ</label>
            <input type="number" step="0.01" v-model.number="form.dampingRatio"
              class="w-full border border-stone-300 rounded px-2 py-1.5 text-sm" />
          </div>
          <div>
            <label class="block text-xs text-stone-500 mb-1">桥梁质量(ton)</label>
            <input type="number" step="10" v-model.number="form.bridgeMass"
              class="w-full border border-stone-300 rounded px-2 py-1.5 text-sm" />
          </div>
          <div class="border-t border-stone-100 pt-3">
            <div class="text-xs font-medium text-stone-700 mb-2">交通荷载工况</div>
            <div v-for="(v, idx) in form.vehicles" :key="idx"
              class="grid grid-cols-3 gap-1 text-xs mb-2">
              <input type="text" v-model="v.type" placeholder="车型"
                class="border border-stone-300 rounded px-1 py-0.5" />
              <input type="number" step="0.5" v-model.number="v.weight" placeholder="重量(t)"
                class="border border-stone-300 rounded px-1 py-0.5" />
              <input type="number" step="5" v-model.number="v.speed" placeholder="速度(km/h)"
                class="border border-stone-300 rounded px-1 py-0.5" />
            </div>
            <button @click="addVehicle"
              class="w-full text-xs text-primary border border-primary rounded py-1 hover:bg-blue-50">
              + 添加车辆
            </button>
          </div>
          <div class="border-t border-stone-100 pt-3">
            <div class="text-xs font-medium text-stone-700 mb-2">控制标准</div>
            <div class="space-y-2">
              <div class="flex justify-between text-xs">
                <span class="text-stone-500">允许加速度(m/s²)</span>
                <input type="number" step="0.01" v-model.number="form.allowableAcceleration"
                  class="w-20 border border-stone-300 rounded px-1 py-0.5 text-right" />
              </div>
              <div class="flex justify-between text-xs">
                <span class="text-stone-500">允许位移(mm)</span>
                <input type="number" step="0.1" v-model.number="form.allowableDisplacement"
                  class="w-20 border border-stone-300 rounded px-1 py-0.5 text-right" />
              </div>
            </div>
          </div>
          <button @click="onAnalyze"
            class="w-full bg-primary text-white rounded py-2 text-sm font-medium hover:bg-blue-900 transition">
            开始分析
          </button>
        </div>
      </div>

      <div class="col-span-2 space-y-6">
        <div v-if="result" class="grid grid-cols-4 gap-4">
          <div class="bg-white rounded-lg shadow p-4">
            <div class="text-xs text-stone-500">最大加速度</div>
            <div class="text-xl font-bold"
              :class="result.maxAcceleration > form.allowableAcceleration ? 'text-red-600' : 'text-green-600'">
              {{ formatNum(result.maxAcceleration) }} m/s²
            </div>
            <div class="text-xs text-stone-400">允许: {{ form.allowableAcceleration }}</div>
          </div>
          <div class="bg-white rounded-lg shadow p-4">
            <div class="text-xs text-stone-500">最大位移</div>
            <div class="text-xl font-bold"
              :class="result.maxDisplacement * 1000 > form.allowableDisplacement ? 'text-red-600' : 'text-green-600'">
              {{ formatNum(result.maxDisplacement * 1000) }} mm
            </div>
            <div class="text-xs text-stone-400">允许: {{ form.allowableDisplacement }}</div>
          </div>
          <div class="bg-white rounded-lg shadow p-4">
            <div class="text-xs text-stone-500">动力放大系数</div>
            <div class="text-xl font-bold text-stone-800">
              {{ formatNum(result.dynamicAmplificationFactor) }}
            </div>
          </div>
          <div class="bg-white rounded-lg shadow p-4">
            <div class="text-xs text-stone-500">安全裕度</div>
            <div class="text-xl font-bold"
              :class="result.safetyMargin < 1 ? 'text-red-600' : result.safetyMargin < 1.5 ? 'text-yellow-600' : 'text-green-600'">
              {{ formatNum(result.safetyMargin) }}
            </div>
          </div>
        </div>

        <div v-if="result" class="grid grid-cols-2 gap-4">
          <div class="bg-white rounded-lg shadow p-4">
            <div class="text-xs text-stone-500">建议限载</div>
            <div class="text-2xl font-bold text-orange-600">
              {{ formatNum(result.allowableWeightLimit) }} 吨
            </div>
          </div>
          <div class="bg-white rounded-lg shadow p-4">
            <div class="text-xs text-stone-500">建议限速</div>
            <div class="text-2xl font-bold text-blue-600">
              {{ formatNum(result.allowableSpeedLimit) }} km/h
            </div>
          </div>
        </div>

        <div class="bg-white rounded-lg shadow p-4">
          <h2 class="text-base font-semibold mb-3 text-stone-700">动力时程响应</h2>
          <div ref="timeHistoryChart" class="h-64"></div>
        </div>

        <div class="bg-white rounded-lg shadow p-4">
          <h2 class="text-base font-semibold mb-3 text-stone-700">不同车速下的振动响应</h2>
          <div ref="speedChart" class="h-48"></div>
        </div>

        <div v-if="result" class="bg-white rounded-lg shadow p-4">
          <h2 class="text-base font-semibold mb-3 text-stone-700">各车型分析结果</h2>
          <div class="overflow-x-auto">
            <table class="w-full text-sm">
              <thead>
                <tr class="bg-stone-50">
                  <th class="px-3 py-2 text-left text-xs font-medium text-stone-500">车型</th>
                  <th class="px-3 py-2 text-left text-xs font-medium text-stone-500">车重(t)</th>
                  <th class="px-3 py-2 text-left text-xs font-medium text-stone-500">车速(km/h)</th>
                  <th class="px-3 py-2 text-left text-xs font-medium text-stone-500">最大加速度(m/s²)</th>
                  <th class="px-3 py-2 text-left text-xs font-medium text-stone-500">最大位移(mm)</th>
                  <th class="px-3 py-2 text-left text-xs font-medium text-stone-500">动力放大系数</th>
                  <th class="px-3 py-2 text-left text-xs font-medium text-stone-500">是否超限</th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="(v, idx) in result.vehicleResults" :key="idx" class="border-t border-stone-100">
                  <td class="px-3 py-2">{{ v.vehicleType }}</td>
                  <td class="px-3 py-2">{{ formatNum(v.vehicleWeight) }}</td>
                  <td class="px-3 py-2">{{ formatNum(v.speed) }}</td>
                  <td class="px-3 py-2"
                    :class="v.maxAcceleration > form.allowableAcceleration ? 'text-red-600' : ''">
                    {{ formatNum(v.maxAcceleration) }}
                  </td>
                  <td class="px-3 py-2"
                    :class="v.maxDisplacement * 1000 > form.allowableDisplacement ? 'text-red-600' : ''">
                    {{ formatNum(v.maxDisplacement * 1000) }}
                  </td>
                  <td class="px-3 py-2">{{ formatNum(v.dynamicAmplificationFactor) }}</td>
                  <td class="px-3 py-2">
                    <span v-if="v.isOverLimit" class="px-2 py-0.5 rounded text-xs bg-red-100 text-red-700">
                      超限
                    </span>
                    <span v-else class="px-2 py-0.5 rounded text-xs bg-green-100 text-green-700">
                      正常
                    </span>
                  </td>
                </tr>
              </tbody>
            </table>
          </div>
        </div>

        <div v-if="result" class="bg-white rounded-lg shadow p-4">
          <h2 class="text-base font-semibold mb-3 text-stone-700">限载建议</h2>
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
const timeHistoryChart = ref(null)
const speedChart = ref(null)
let timeHistoryInstance = null
let speedChartInstance = null

const form = reactive({
  bridgeId: 1,
  naturalFrequency: 3.0,
  dampingRatio: 0.05,
  bridgeMass: 1000,
  allowableAcceleration: 0.5,
  allowableDisplacement: 5.0,
  vehicles: [
    { type: '小轿车', weight: 1.5, speed: 60 },
    { type: '货车', weight: 30, speed: 40 },
    { type: '重型货车', weight: 55, speed: 30 }
  ]
})

function formatNum(v) {
  if (v == null) return '-'
  return Number(v).toFixed(2)
}

function addVehicle() {
  form.vehicles.push({ type: '', weight: 10, speed: 50 })
}

async function onBridgeChange() {
  result.value = null
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

async function onAnalyze() {
  try {
    const payload = {
      bridgeId: form.bridgeId,
      naturalFrequency: form.naturalFrequency,
      dampingRatio: form.dampingRatio,
      bridgeMass: form.bridgeMass,
      allowableAcceleration: form.allowableAcceleration,
      allowableDisplacement: form.allowableDisplacement / 1000,
      vehicles: form.vehicles
    }

    const res = await fetch('/api/traffic/analyze', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(payload)
    })

    const data = await res.json()
    if (data.code === 200) {
      result.value = data.data
      updateCharts()
    }
  } catch (e) {
    console.error('振动分析失败', e)
  }
}

function generateRecommendation() {
  if (!result.value) return ''

  const safety = result.value.safetyMargin
  const weightLimit = result.value.allowableWeightLimit
  const speedLimit = result.value.allowableSpeedLimit

  let rec = ''
  if (safety < 0.7) {
    rec = `当前桥梁在现有交通荷载下安全裕度不足（${formatNum(safety)}），存在较大安全隐患。`
  } else if (safety < 1.0) {
    rec = `当前桥梁安全裕度偏低（${formatNum(safety)}），需加强交通管制。`
  } else if (safety < 1.5) {
    rec = `当前桥梁安全裕度适中（${formatNum(safety)}），建议定期监测。`
  } else {
    rec = `当前桥梁安全裕度充足（${formatNum(safety)}），可维持现有交通管制。`
  }

  rec += ` 建议采取以下措施：`
  rec += ` 1. 实施车辆限载${formatNum(weightLimit)}吨，禁止超重车辆通行；`
  rec += ` 2. 实施车辆限速${formatNum(speedLimit)}km/h，减少冲击荷载；`
  rec += ` 3. 在桥头设置明显的限载限速标志；`
  rec += ` 4. 加强桥梁振动监测，定期复核动力响应特性。`

  if (safety < 1.0) {
    rec += ` 5. 考虑对桥梁进行动力特性加固，提高抗振能力。`
  }

  return rec
}

function updateCharts() {
  if (!result.value) return

  if (timeHistoryChart.value) {
    if (!timeHistoryInstance) {
      timeHistoryInstance = echarts.init(timeHistoryChart.value)
    }

    const time = result.value.timeHistory?.time || []
    const acceleration = result.value.timeHistory?.acceleration || []
    const displacement = result.value.timeHistory?.displacement || []

    const option = {
      tooltip: { trigger: 'axis' },
      legend: { data: ['加速度(m/s²)', '位移(mm)'] },
      xAxis: {
        type: 'category',
        data: time.map(t => t.toFixed(1)),
        name: '时间(s)'
      },
      yAxis: [
        { type: 'value', name: '加速度(m/s²)' },
        { type: 'value', name: '位移(mm)' }
      ],
      series: [
        {
          name: '加速度(m/s²)',
          type: 'line',
          data: acceleration,
          lineStyle: { color: '#ef4444' },
          showSymbol: false,
          smooth: true
        },
        {
          name: '位移(mm)',
          type: 'line',
          yAxisIndex: 1,
          data: displacement.map(d => d * 1000),
          lineStyle: { color: '#3b82f6' },
          showSymbol: false,
          smooth: true
        }
      ]
    }
    timeHistoryInstance.setOption(option)
  }

  if (speedChart.value) {
    if (!speedChartInstance) {
      speedChartInstance = echarts.init(speedChart.value)
    }

    const speeds = [20, 30, 40, 50, 60, 70, 80]
    const accelerations = speeds.map(s => {
      const base = result.value.maxAcceleration || 0.2
      return base * (0.6 + 0.01 * s)
    })

    const option = {
      tooltip: { trigger: 'axis' },
      xAxis: {
        type: 'category',
        data: speeds,
        name: '车速(km/h)'
      },
      yAxis: {
        type: 'value',
        name: '最大加速度(m/s²)'
      },
      series: [{
        type: 'line',
        data: accelerations,
        lineStyle: { color: '#f97316' },
        itemStyle: { color: '#f97316' },
        areaStyle: {
          color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
            { offset: 0, color: 'rgba(249, 115, 22, 0.3)' },
            { offset: 1, color: 'rgba(249, 115, 22, 0)' }
          ])
        },
        markLine: {
          data: [{
            yAxis: form.allowableAcceleration,
            lineStyle: { color: '#ef4444', type: 'dashed' },
            label: { formatter: '允许值' }
          }]
        }
      }]
    }
    speedChartInstance.setOption(option)
  }
}

function resize() {
  timeHistoryInstance?.resize()
  speedChartInstance?.resize()
}

onMounted(async () => {
  await loadBridges()
  window.addEventListener('resize', resize)
})

watch([timeHistoryChart, speedChart], () => {
  if (result.value) updateCharts()
})

onUnmounted(() => {
  window.removeEventListener('resize', resize)
  timeHistoryInstance?.dispose()
  speedChartInstance?.dispose()
})
</script>
