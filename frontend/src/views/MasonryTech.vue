<template>
  <div class="p-6 space-y-6">
    <div>
      <h1 class="text-2xl font-bold text-stone-800">古代砌筑工艺数字化复原</h1>
      <p class="text-sm text-stone-500">离散元法(DEM) · 接触力链可视化</p>
    </div>

    <div class="grid grid-cols-3 gap-6">
      <div class="bg-white rounded-lg shadow p-4">
        <h2 class="text-base font-semibold mb-4 text-stone-700">仿真参数</h2>
        <div class="space-y-3">
          <div>
            <label class="block text-xs text-stone-500 mb-1">选择桥梁</label>
            <select v-model.number="form.bridgeId" @change="onBridgeChange"
              class="w-full border border-stone-300 rounded px-2 py-1.5 text-sm">
              <option v-for="b in bridges" :key="b.id" :value="b.id">{{ b.name }}</option>
            </select>
          </div>
          <div>
            <label class="block text-xs text-stone-500 mb-1">分析类型</label>
            <select v-model="form.analysisType"
              class="w-full border border-stone-300 rounded px-2 py-1.5 text-sm">
              <option value="static">静力分析</option>
              <option value="gravity">重力分析</option>
              <option value="seismic">地震作用</option>
              <option value="traffic">交通荷载</option>
            </select>
          </div>
          <div>
            <label class="block text-xs text-stone-500 mb-1">离散单元数</label>
            <input type="number" v-model.number="form.elementCount"
              class="w-full border border-stone-300 rounded px-2 py-1.5 text-sm" />
          </div>
          <div class="border-t border-stone-100 pt-3">
            <div class="text-xs font-medium text-stone-700 mb-2">材料参数</div>
            <div class="space-y-2 text-xs">
              <div class="flex justify-between">
                <span class="text-stone-500">法向刚度 kn (N/m)</span>
                <input type="number" step="1e8" v-model.number="form.normalStiffness"
                  class="w-28 border border-stone-300 rounded px-1 py-0.5 text-right" />
              </div>
              <div class="flex justify-between">
                <span class="text-stone-500">切向刚度 kt (N/m)</span>
                <input type="number" step="1e8" v-model.number="form.shearStiffness"
                  class="w-28 border border-stone-300 rounded px-1 py-0.5 text-right" />
              </div>
              <div class="flex justify-between">
                <span class="text-stone-500">摩擦系数 μ</span>
                <input type="number" step="0.01" v-model.number="form.frictionCoefficient"
                  class="w-28 border border-stone-300 rounded px-1 py-0.5 text-right" />
              </div>
              <div class="flex justify-between">
                <span class="text-stone-500">黏聚力 c (Pa)</span>
                <input type="number" step="1e3" v-model.number="form.cohesion"
                  class="w-28 border border-stone-300 rounded px-1 py-0.5 text-right" />
              </div>
              <div class="flex justify-between">
                <span class="text-stone-500">阻尼系数 η</span>
                <input type="number" step="0.01" v-model.number="form.dampingCoefficient"
                  class="w-28 border border-stone-300 rounded px-1 py-0.5 text-right" />
              </div>
            </div>
          </div>
          <div class="border-t border-stone-100 pt-3">
            <div class="text-xs font-medium text-stone-700 mb-2">灰浆参数</div>
            <div class="space-y-2 text-xs">
              <div class="flex justify-between">
                <span class="text-stone-500">灰浆厚度 (mm)</span>
                <input type="number" step="1" v-model.number="form.mortarThickness"
                  class="w-28 border border-stone-300 rounded px-1 py-0.5 text-right" />
              </div>
              <div class="flex justify-between">
                <span class="text-stone-500">灰浆强度 (MPa)</span>
                <input type="number" step="0.1" v-model.number="form.mortarStrength"
                  class="w-28 border border-stone-300 rounded px-1 py-0.5 text-right" />
              </div>
              <div>
                <label class="block text-stone-500 mb-1">灰浆成分</label>
                <select v-model="form.mortarType"
                  class="w-full border border-stone-300 rounded px-1 py-0.5">
                  <option value="lime">纯石灰砂浆</option>
                  <option value="lime_sand">石灰-砂子砂浆</option>
                  <option value="lime_clay">石灰-黏土砂浆</option>
                  <option value="rice_lime">糯米-石灰砂浆</option>
                </select>
              </div>
            </div>
          </div>
          <button @click="onSimulate"
            class="w-full bg-primary text-white rounded py-2 text-sm font-medium hover:bg-blue-900 transition">
            运行离散元仿真
          </button>
        </div>
      </div>

      <div class="col-span-2 space-y-6">
        <div v-if="result" class="grid grid-cols-3 gap-4">
          <div class="bg-white rounded-lg shadow p-4">
            <div class="text-xs text-stone-500">结构完整性指数</div>
            <div class="text-xl font-bold"
              :class="result.structuralIntegrityIndex > 0.8 ? 'text-green-600' : result.structuralIntegrityIndex > 0.5 ? 'text-yellow-600' : 'text-red-600'">
              {{ formatPercent(result.structuralIntegrityIndex) }}
            </div>
          </div>
          <div class="bg-white rounded-lg shadow p-4">
            <div class="text-xs text-stone-500">荷载传递效率</div>
            <div class="text-xl font-bold text-stone-800">
              {{ formatPercent(result.loadTransferEfficiency) }}
            </div>
          </div>
          <div class="bg-white rounded-lg shadow p-4">
            <div class="text-xs text-stone-500">最大接触力</div>
            <div class="text-xl font-bold text-orange-600">
              {{ formatSci(result.maxContactForce) }} N
            </div>
          </div>
        </div>

        <div class="bg-white rounded-lg shadow p-4">
          <h2 class="text-base font-semibold mb-3 text-stone-700">接触力链分布</h2>
          <div ref="forceChainChart" class="h-80"></div>
        </div>

        <div class="bg-white rounded-lg shadow p-4">
          <h2 class="text-base font-semibold mb-3 text-stone-700">接触力统计分布</h2>
          <div ref="forceDistChart" class="h-48"></div>
        </div>

        <div v-if="result" class="grid grid-cols-2 gap-6">
          <div class="bg-white rounded-lg shadow p-4">
            <h2 class="text-base font-semibold mb-3 text-stone-700">砌筑工艺评估</h2>
            <div class="space-y-2 text-sm">
              <div class="flex justify-between">
                <span class="text-stone-500">砌筑工艺类型</span>
                <span class="font-medium">{{ getMasonryTypeText(result.masonryType) }}</span>
              </div>
              <div class="flex justify-between">
                <span class="text-stone-500">石块排列方式</span>
                <span class="font-medium">{{ getStonePatternText(result.stonePattern) }}</span>
              </div>
              <div class="flex justify-between">
                <span class="text-stone-500">灰浆成分评估</span>
                <span class="font-medium">{{ getMortarQualityText(result.mortarQuality) }}</span>
              </div>
              <div class="flex justify-between">
                <span class="text-stone-500">工艺年代特征</span>
                <span class="font-medium">{{ result.eraCharacteristic || '宋代特征' }}</span>
              </div>
              <div class="flex justify-between">
                <span class="text-stone-500">平均石块尺寸</span>
                <span class="font-medium">{{ formatNum(result.avgStoneSize) }} m</span>
              </div>
              <div class="flex justify-between">
                <span class="text-stone-500">灰缝平均厚度</span>
                <span class="font-medium">{{ formatNum(result.avgJointThickness * 1000) }} mm</span>
              </div>
            </div>
          </div>

          <div class="bg-white rounded-lg shadow p-4">
            <h2 class="text-base font-semibold mb-3 text-stone-700">工艺保护建议</h2>
            <div class="text-sm text-stone-600 leading-relaxed">
              {{ generateRecommendation() }}
            </div>
          </div>
        </div>

        <div v-if="result?.contacts" class="bg-white rounded-lg shadow p-4">
          <h2 class="text-base font-semibold mb-3 text-stone-700">主要接触点详情</h2>
          <div class="overflow-x-auto">
            <table class="w-full text-sm">
              <thead>
                <tr class="bg-stone-50">
                  <th class="px-3 py-2 text-left text-xs font-medium text-stone-500">接触ID</th>
                  <th class="px-3 py-2 text-left text-xs font-medium text-stone-500">位置</th>
                  <th class="px-3 py-2 text-left text-xs font-medium text-stone-500">法向力(N)</th>
                  <th class="px-3 py-2 text-left text-xs font-medium text-stone-500">切向力(N)</th>
                  <th class="px-3 py-2 text-left text-xs font-medium text-stone-500">接触状态</th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="(c, idx) in result.contacts.slice(0, 10)" :key="idx" class="border-t border-stone-100">
                  <td class="px-3 py-2">#{{ c.id }}</td>
                  <td class="px-3 py-2">({{ formatNum(c.x) }}, {{ formatNum(c.y) }})</td>
                  <td class="px-3 py-2">{{ formatSci(c.normalForce) }}</td>
                  <td class="px-3 py-2">{{ formatSci(c.shearForce) }}</td>
                  <td class="px-3 py-2">
                    <span v-if="c.isSliding" class="px-2 py-0.5 rounded text-xs bg-yellow-100 text-yellow-700">
                      滑移
                    </span>
                    <span v-else class="px-2 py-0.5 rounded text-xs bg-green-100 text-green-700">
                      稳定
                    </span>
                  </td>
                </tr>
              </tbody>
            </table>
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
const forceChainChart = ref(null)
const forceDistChart = ref(null)
let forceChainInstance = null
let forceDistInstance = null

const form = reactive({
  bridgeId: 1,
  analysisType: 'static',
  elementCount: 200,
  normalStiffness: 1e9,
  shearStiffness: 5e8,
  frictionCoefficient: 0.6,
  cohesion: 50000,
  dampingCoefficient: 0.05,
  mortarThickness: 15,
  mortarStrength: 2.5,
  mortarType: 'rice_lime'
})

function formatNum(v) {
  if (v == null) return '-'
  return Number(v).toFixed(2)
}

function formatSci(v) {
  if (v == null) return '-'
  if (Math.abs(v) >= 1000 || (Math.abs(v) < 0.01 && v !== 0)) {
    return Number(v).toExponential(2)
  }
  return Number(v).toFixed(2)
}

function formatPercent(v) {
  if (v == null) return '-'
  return (Number(v) * 100).toFixed(1) + '%'
}

function getMasonryTypeText(type) {
  const map = {
    'shunqi': '顺砌法',
    'dingqi': '丁砌法',
    'shunding': '顺丁相间',
    'wuhuagong': '五花座砌'
  }
  return map[type] || type || '传统工艺'
}

function getStonePatternText(pattern) {
  const map = {
    'horizontal': '横向排列',
    'herringbone': '人字形排列',
    'fan': '扇形排列',
    'cross': '十字交错'
  }
  return map[pattern] || pattern || '规整排列'
}

function getMortarQualityText(quality) {
  const map = {
    'excellent': '优质（糯米灰浆）',
    'good': '良好',
    'fair': '一般',
    'poor': '较差'
  }
  return map[quality] || quality || '需要检测'
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

async function onSimulate() {
  try {
    const payload = {
      bridgeId: form.bridgeId,
      analysisType: form.analysisType,
      elementCount: form.elementCount,
      normalStiffness: form.normalStiffness,
      shearStiffness: form.shearStiffness,
      frictionCoefficient: form.frictionCoefficient,
      cohesion: form.cohesion,
      dampingCoefficient: form.dampingCoefficient,
      mortarThickness: form.mortarThickness / 1000,
      mortarStrength: form.mortarStrength * 1e6,
      mortarType: form.mortarType
    }

    const res = await fetch('/api/masonry/simulate', {
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
    console.error('离散元仿真失败', e)
  }
}

function generateRecommendation() {
  if (!result.value) return ''

  const integrity = result.value.structuralIntegrityIndex
  const transfer = result.value.loadTransferEfficiency

  let rec = `该桥采用${getMasonryTypeText(result.value.masonryType)}工艺，`
  rec += `石块${getStonePatternText(result.value.stonePattern)}，`
  rec += `灰浆质量${getMortarQualityText(result.value.mortarQuality)}。`

  if (integrity < 0.5) {
    rec += ` 结构完整性较差（${formatPercent(integrity)}），建议立即进行结构加固，重点修复开裂灰缝。`
  } else if (integrity < 0.7) {
    rec += ` 结构完整性一般（${formatPercent(integrity)}），建议对滑移接触点进行注浆加固。`
  } else {
    rec += ` 结构完整性良好（${formatPercent(integrity)}），建议维持现有监测。`
  }

  if (transfer < 0.6) {
    rec += ` 荷载传递效率偏低（${formatPercent(transfer)}），需关注石块接触状态变化。`
  }

  rec += ` 针对古代砌筑工艺特点，建议采用传统材料进行修复，保护工艺原貌。`

  return rec
}

function updateCharts() {
  if (!result.value) return

  if (forceChainChart.value) {
    if (!forceChainInstance) {
      forceChainInstance = echarts.init(forceChainChart.value)
    }

    const chains = result.value.forceChains || []
    const maxForce = Math.max(...chains.map(c => c.normalForce || 1), 1)

    const data = chains.map(chain => ({
      coords: [
        [chain.x1 || 0, chain.y1 || 0],
        [chain.x2 || 0, chain.y2 || 0]
      ],
      lineStyle: {
        width: Math.max(1, (chain.normalForce / maxForce) * 8),
        color: chain.normalForce / maxForce > 0.7 ? '#ef4444' :
               chain.normalForce / maxForce > 0.4 ? '#f97316' :
               chain.normalForce / maxForce > 0.2 ? '#eab308' : '#22c55e'
      }
    }))

    const option = {
      tooltip: { trigger: 'item' },
      xAxis: {
        type: 'value',
        min: 0,
        max: 30,
        name: 'X (m)'
      },
      yAxis: {
        type: 'value',
        min: 0,
        max: 10,
        name: 'Y (m)'
      },
      series: [{
        type: 'lines',
        coordinateSystem: 'cartesian2d',
        data: data,
        polyline: false,
        effect: {
          show: true,
          period: 4,
          trailLength: 0.1,
          symbol: 'arrow',
          symbolSize: 6
        }
      }, {
        type: 'scatter',
        data: chains.map(c => [c.x1 || 0, c.y1 || 0]),
        symbolSize: 8,
        itemStyle: { color: '#64748b' }
      }]
    }
    forceChainInstance.setOption(option)
  }

  if (forceDistChart.value) {
    if (!forceDistInstance) {
      forceDistInstance = echarts.init(forceDistChart.value)
    }

    const forces = (result.value.contacts || []).map(c => c.normalForce || 0)
    const bins = [0, 1000, 5000, 10000, 50000, 100000, Infinity]
    const labels = ['0-1k', '1k-5k', '5k-10k', '10k-50k', '50k-100k', '>100k']
    const counts = new Array(bins.length - 1).fill(0)

    forces.forEach(f => {
      for (let i = 0; i < bins.length - 1; i++) {
        if (f >= bins[i] && f < bins[i + 1]) {
          counts[i]++
          break
        }
      }
    })

    const option = {
      tooltip: { trigger: 'axis' },
      xAxis: {
        type: 'category',
        data: labels,
        name: '法向力范围(N)'
      },
      yAxis: {
        type: 'value',
        name: '接触点数'
      },
      series: [{
        type: 'bar',
        data: counts,
        itemStyle: {
          color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
            { offset: 0, color: '#3b82f6' },
            { offset: 1, color: '#1d4ed8' }
          ])
        }
      }]
    }
    forceDistInstance.setOption(option)
  }
}

function resize() {
  forceChainInstance?.resize()
  forceDistInstance?.resize()
}

onMounted(async () => {
  await loadBridges()
  window.addEventListener('resize', resize)
})

watch([forceChainChart, forceDistChart], () => {
  if (result.value) updateCharts()
})

onUnmounted(() => {
  window.removeEventListener('resize', resize)
  forceChainInstance?.dispose()
  forceDistInstance?.dispose()
})
</script>
