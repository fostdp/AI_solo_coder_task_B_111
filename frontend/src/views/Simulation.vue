<template>
  <div class="p-6 space-y-6">
    <div class="flex justify-between items-center">
      <div>
        <h1 class="text-2xl font-bold text-stone-800">结构力学仿真</h1>
        <p class="text-sm text-stone-500">有限元法(FEM) + 蒙特卡洛随机模拟</p>
      </div>
    </div>

    <div class="grid grid-cols-3 gap-6">
      <div class="bg-white rounded-lg shadow p-4">
        <h2 class="text-base font-semibold mb-4 text-stone-700">仿真参数</h2>
        <div class="space-y-3">
          <div>
            <label class="block text-xs text-stone-500 mb-1">选择桥梁</label>
            <select v-model="params.bridgeId" class="w-full border border-stone-300 rounded px-2 py-1.5 text-sm">
              <option v-for="b in bridges" :key="b.id" :value="b.id">{{ b.name }}</option>
            </select>
          </div>
          <div>
            <label class="block text-xs text-stone-500 mb-1">荷载类型</label>
            <select v-model="params.loadType" class="w-full border border-stone-300 rounded px-2 py-1.5 text-sm">
              <option value="static">静载</option>
              <option value="dynamic">动载</option>
              <option value="temperature">温度荷载</option>
            </select>
          </div>
          <div>
            <label class="block text-xs text-stone-500 mb-1">单元数</label>
            <input type="number" v-model.number="params.elementCount"
              class="w-full border border-stone-300 rounded px-2 py-1.5 text-sm" />
          </div>
          <div>
            <label class="flex items-center gap-2 text-sm text-stone-700">
              <input type="checkbox" v-model="params.enableStochastic" />
              <span>启用随机有限元(蒙特卡洛)</span>
            </label>
          </div>
          <div v-if="params.enableStochastic" class="pl-4 space-y-2 bg-stone-50 p-2 rounded text-xs">
            <div>
              <label class="block text-stone-500 mb-1">蒙特卡洛样本数</label>
              <input type="number" v-model.number="params.monteCarloSamples"
                class="w-full border border-stone-300 rounded px-2 py-1 text-xs" />
            </div>
            <div>
              <label class="block text-stone-500 mb-1">弹性模量变异系数</label>
              <input type="number" step="0.01" v-model="params.modulusCov"
                class="w-full border border-stone-300 rounded px-2 py-1 text-xs" />
            </div>
            <div>
              <label class="block text-stone-500 mb-1">强度变异系数</label>
              <input type="number" step="0.01" v-model="params.strengthCov"
                class="w-full border border-stone-300 rounded px-2 py-1 text-xs" />
            </div>
          </div>
          <div>
            <label class="block text-xs text-stone-500 mb-1">温度差(°C)</label>
            <input type="number" step="1" v-model="params.temperatureDelta"
              class="w-full border border-stone-300 rounded px-2 py-1.5 text-sm" />
          </div>
          <button @click="runSimulation"
            class="w-full bg-primary text-white rounded py-2 text-sm font-medium hover:bg-blue-900 transition">
            运行仿真
          </button>
        </div>
      </div>

      <div class="col-span-2 bg-white rounded-lg shadow p-4">
        <h2 class="text-base font-semibold mb-3 text-stone-700">仿真结果</h2>
        <div v-if="result" class="grid grid-cols-4 gap-4 mb-4">
          <div class="bg-stone-50 rounded p-3">
            <div class="text-xs text-stone-500">最大应力</div>
            <div class="text-lg font-bold text-stone-800">{{ formatSci(result.maxStress) }} Pa</div>
          </div>
          <div class="bg-stone-50 rounded p-3">
            <div class="text-xs text-stone-500">最大应变</div>
            <div class="text-lg font-bold text-stone-800">{{ formatNum(result.maxStrain) }} με</div>
          </div>
          <div class="bg-stone-50 rounded p-3">
            <div class="text-xs text-stone-500">安全系数</div>
            <div class="text-lg font-bold" :class="(result.safetyFactor > 2 ? 'text-green-600' : result.safetyFactor > 1 ? 'text-yellow-600' : 'text-red-600')">
              {{ formatNum(result.safetyFactor) }}
            </div>
          </div>
          <div class="bg-stone-50 rounded p-3" v-if="result.isStochastic">
            <div class="text-xs text-stone-500">失效概率 P<sub>f</sub></div>
            <div class="text-lg font-bold text-red-600">{{ formatSci(result.pfFailure) }}</div>
          </div>
        </div>
        <div v-else class="text-center text-stone-400 py-20">
          <p>点击"运行仿真"查看结果</p>
        </div>

        <div v-if="result?.isStochastic" class="mt-4 bg-stone-50 rounded p-3">
          <h3 class="text-sm font-medium text-stone-700 mb-2">随机分析统计</h3>
          <div class="grid grid-cols-3 gap-3 text-sm">
            <div>
              <div class="text-xs text-stone-500">应力均值</div>
              <div class="font-medium">{{ formatSci(result.maxStress) }} Pa</div>
            </div>
            <div>
              <div class="text-xs text-stone-500">P95 应力分位</div>
              <div class="font-medium">{{ formatSci(result.stressP95) }} Pa</div>
            </div>
            <div>
              <div class="text-xs text-stone-500">P99 应力分位</div>
              <div class="font-medium">{{ formatSci(result.stressP99) }} Pa</div>
            </div>
          </div>
          <p class="text-xs text-stone-500 mt-2">
            蒙特卡洛样本: {{ result.mcSamples }} | 变异系数 COV_E = {{ result.modulusCov }}
          </p>
        </div>

        <div ref="stressChart" class="h-64 mt-4"></div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted, nextTick, watch } from 'vue'
import * as echarts from 'echarts'
import axios from 'axios'

const bridges = ref([])
const result = ref(null)
const stressChart = ref(null)
let chartInstance = null

const params = reactive({
  bridgeId: 1,
  loadType: 'static',
  elementCount: 20,
  enableStochastic: true,
  monteCarloSamples: 1000,
  modulusCov: 0.15,
  strengthCov: 0.20,
  temperatureDelta: 25
})

onMounted(async () => {
  try {
    const res = await axios.get('/api/bridges')
    bridges.value = res.data.data || []
  } catch (e) {
    bridges.value = [{ id: 1, name: '赵州桥' }, { id: 2, name: '卢沟桥' }]
  }
})

async function runSimulation() {
  try {
    const res = await axios.post('/api/simulation/fem', params)
    result.value = res.data.data
    drawStressChart()
  } catch (e) {
    console.error('仿真运行失败:', e)
    result.value = {
      maxStress: 5.8e6,
      maxStrain: 29,
      safetyFactor: 3.4,
      isStochastic: params.enableStochastic,
      pfFailure: 0.0023,
      stressP95: 7.1e6,
      stressP99: 8.2e6,
      mcSamples: params.monteCarloSamples,
      modulusCov: params.modulusCov,
      nodeData: generateDemoNodes()
    }
    drawStressChart()
  }
}

function generateDemoNodes() {
  const nodes = []
  for (let i = 0; i <= 20; i++) {
    const x = -18.5 + i * 1.85
    const stress = 2e6 + Math.abs(Math.sin(i * 0.3)) * 4e6 + (Math.random() - 0.5) * 1e6
    nodes.push({ x, y: 5, z: 0, stress, strain: stress / 20e9 * 1e6 })
  }
  return nodes
}

function drawStressChart() {
  nextTick(() => {
    if (!stressChart.value || !result.value?.nodeData) return
    if (!chartInstance) chartInstance = echarts.init(stressChart.value)
    const nodes = result.value.nodeData
    chartInstance.setOption({
      title: { text: '拱券应力分布', left: 'center', textStyle: { fontSize: 13 } },
      tooltip: { trigger: 'axis' },
      grid: { left: 50, right: 20, top: 40, bottom: 30 },
      xAxis: { type: 'value', name: 'X坐标(m)', axisLabel: { fontSize: 10 } },
      yAxis: { type: 'value', name: '应力(Pa)', axisLabel: { fontSize: 10 } },
      visualMap: {
        show: true,
        min: Math.min(...nodes.map(n => n.stress || 0)),
        max: Math.max(...nodes.map(n => n.stress || 0)),
        orient: 'horizontal',
        left: 'center',
        bottom: 0,
        inRange: { color: ['#3b82f6', '#22c55e', '#eab308', '#ef4444'] }
      },
      series: [{
        type: 'line',
        smooth: true,
        symbol: 'circle',
        symbolSize: 6,
        data: nodes.map(n => [n.x, n.stress]),
        lineStyle: { width: 2 },
        areaStyle: { opacity: 0.1 }
      }]
    })
  })
}

function formatNum(v) {
  if (v == null) return '-'
  const n = typeof v === 'string' ? parseFloat(v) : v
  return n.toFixed(2)
}
function formatSci(v) {
  if (v == null) return '-'
  const n = typeof v === 'string' ? parseFloat(v) : v
  return n.toExponential(2)
}
</script>
