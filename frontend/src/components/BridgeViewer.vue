<template>
  <div ref="container" class="relative w-full h-full bg-gradient-to-b from-sky-100 to-stone-200 overflow-hidden">
    <div class="absolute top-4 left-4 z-10 space-y-2">
      <select v-model="selectedBridge" @change="loadBridge"
        class="bg-white border border-stone-300 rounded px-3 py-1.5 text-sm shadow">
        <option v-for="b in bridges" :key="b.id" :value="b.id">{{ b.name }}</option>
      </select>
      <div class="flex gap-2 flex-wrap max-w-md">
        <button @click="onToggleHeatmap"
          :class="showHeatmap ? 'bg-red-500 text-white' : 'bg-white text-stone-700'"
          class="px-3 py-1 text-xs rounded shadow border border-stone-300">
          应力热力图
        </button>
        <button @click="onToggleCracks"
          :class="showCracks ? 'bg-red-600 text-white' : 'bg-white text-stone-700'"
          class="px-3 py-1 text-xs rounded shadow border border-stone-300">
          裂缝标记
        </button>
        <button @click="onToggleWeathering"
          :class="showWeathering ? 'bg-orange-500 text-white' : 'bg-white text-stone-700'"
          class="px-3 py-1 text-xs rounded shadow border border-stone-300">
          风化标注
        </button>
        <button @click="onToggleVibration"
          :class="showVibration ? 'bg-purple-500 text-white' : 'bg-white text-stone-700'"
          class="px-3 py-1 text-xs rounded shadow border border-stone-300">
          振动热力图
        </button>
        <button @click="onToggleForceChain"
          :class="showForceChain ? 'bg-emerald-600 text-white' : 'bg-white text-stone-700'"
          class="px-3 py-1 text-xs rounded shadow border border-stone-300">
          接触力链
        </button>
        <select v-model.number="lodLevel" @change="onUpdateLOD"
          class="bg-white border border-stone-300 rounded px-2 py-1 text-xs shadow">
          <option :value="2">高精度</option>
          <option :value="1">中精度</option>
          <option :value="0">低精度</option>
        </select>
      </div>
      <div class="text-xs text-stone-600 bg-white/80 px-2 py-1 rounded">
        LOD: {{ lodNames[lodLevel] }} | 内存: {{ memoryInfo }}
      </div>
    </div>

    <div class="absolute bottom-4 right-4 z-10 w-56 bg-white/90 rounded shadow p-3 text-xs space-y-3 max-h-[80vh] overflow-auto">
      <div v-if="showHeatmap">
        <div class="font-bold mb-2 text-stone-700">应力图例 (Pa)</div>
        <div class="h-4 rounded bg-gradient-to-r from-blue-500 via-green-500 to-red-500"></div>
        <div class="flex justify-between mt-1 text-stone-500">
          <span>{{ minStress.toExponential(1) }}</span>
          <span>{{ maxStress.toExponential(1) }}</span>
        </div>
      </div>
      <div v-if="showWeathering">
        <div class="font-bold mb-2 text-orange-700">风化深度 (mm)</div>
        <div class="space-y-1">
          <div class="flex items-center gap-2"><div class="w-4 h-4 rounded bg-green-500"></div><span>0-2mm 无风化</span></div>
          <div class="flex items-center gap-2"><div class="w-4 h-4 rounded bg-blue-500"></div><span>2-5mm 轻微</span></div>
          <div class="flex items-center gap-2"><div class="w-4 h-4 rounded bg-yellow-500"></div><span>5-10mm 中等</span></div>
          <div class="flex items-center gap-2"><div class="w-4 h-4 rounded bg-orange-500"></div><span>10-20mm 严重</span></div>
          <div class="flex items-center gap-2"><div class="w-4 h-4 rounded bg-red-500"></div><span>&gt;20mm 危险</span></div>
        </div>
      </div>
      <div v-if="showVibration">
        <div class="font-bold mb-2 text-purple-700">振动加速度 (m/s²)</div>
        <div class="h-4 rounded bg-gradient-to-r from-blue-200 via-purple-400 to-red-500"></div>
        <div class="flex justify-between mt-1 text-stone-500">
          <span>0.0</span>
          <span>0.5</span>
          <span>1.0+</span>
        </div>
      </div>
      <div v-if="showForceChain">
        <div class="font-bold mb-2 text-emerald-700">接触力链</div>
        <div class="space-y-1">
          <div class="flex items-center gap-2"><div class="h-1 w-8 bg-emerald-300 rounded"></div><span>小力 (10kN)</span></div>
          <div class="flex items-center gap-2"><div class="h-2 w-8 bg-emerald-500 rounded"></div><span>中力 (30kN)</span></div>
          <div class="flex items-center gap-2"><div class="h-3 w-8 bg-emerald-700 rounded"></div><span>大力 (50kN+)</span></div>
        </div>
      </div>
    </div>

    <div v-if="selectedSensor" class="absolute top-4 right-4 z-20 w-80 bg-white rounded-lg shadow-xl border border-stone-200 overflow-hidden">
      <div class="bg-primary text-white px-4 py-2 flex justify-between items-center">
        <span class="font-medium text-sm">{{ selectedSensor.name || '传感器详情' }}</span>
        <button @click="selectedSensor = null" class="text-white/80 hover:text-white text-lg leading-none">&times;</button>
      </div>
      <div class="p-3">
        <div class="text-xs text-stone-500 mb-1">类型：{{ sensorTypes[selectedSensor.type] || selectedSensor.type }}</div>
        <div class="text-2xl font-bold text-stone-800">{{ latestValue }}{{ sensorUnits[selectedSensor.type] || '' }}</div>
        <div ref="trendChart" class="h-40 mt-3"></div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted, onUnmounted, watch, nextTick } from 'vue'
import * as echarts from 'echarts'
import axios from 'axios'
import { Bridge3DViewer } from '@/utils/bridge_3d_viewer'

const props = defineProps({
  showHeatmap: { type: Boolean, default: true },
  showCracks: { type: Boolean, default: true }
})

const emit = defineEmits(['sensor-click'])

const container = ref(null)
const trendChart = ref(null)
const selectedBridge = ref(1)
const bridges = ref([])
const lodLevel = ref(1)
const showHeatmap = ref(true)
const showCracks = ref(true)
const showWeathering = ref(false)
const showVibration = ref(false)
const showForceChain = ref(false)
const selectedSensor = ref(null)
const latestValue = ref(0)
const minStress = ref(0)
const maxStress = ref(1e6)
const memoryInfo = ref('')

const lodNames = ['低', '中', '高']
const sensorTypes = { strain: '应变计', displacement: '位移计', crack: '裂缝计', temperature: '温度传感器', vibration: '振动传感器' }
const sensorUnits = { strain: ' με', displacement: ' mm', crack: ' mm', temperature: ' °C', vibration: ' mm/s²' }

let viewer = null
let chartInstance = null
let lastFemData = null
let lastWeatheringData = null
let lastVibrationData = null
let lastForceChainData = null

onMounted(async () => {
  await loadBridgeList()
  viewer = new Bridge3DViewer(container.value, {
    onSensorClick: handleSensorClick,
    onBeforeRender: () => {
      if (memoryInfo.value === '' && viewer?.renderer) {
        memoryInfo.value = viewer.getMemoryInfo()
      }
    }
  })
  viewer.init()
  viewer.setCracksVisible(showCracks.value)
  await loadBridge()
})

onUnmounted(() => {
  if (chartInstance) chartInstance.dispose()
  viewer?.dispose()
  viewer = null
})

async function loadBridgeList() {
  try {
    const res = await axios.get('/api/bridges')
    bridges.value = res.data.data || []
    if (bridges.value.length && !selectedBridge.value) {
      selectedBridge.value = bridges.value[0].id
    }
  } catch (e) {
    bridges.value = [
      { id: 1, name: '赵州桥', spanLength: 37, riseSpanRatio: 0.2, pierThickness: 1.5, archCount: 1 },
      { id: 2, name: '卢沟桥', spanLength: 21.3, riseSpanRatio: 0.25, pierThickness: 1.2, archCount: 11 }
    ]
  }
}

async function loadBridge() {
  const bridge = bridges.value.find(b => b.id === selectedBridge.value) || { spanLength: 37, riseSpanRatio: 0.2, pierThickness: 1.5 }
  viewer.buildBridgeModels(bridge)
  await loadFemResult()
  await loadSensors()
  await loadCracks()
  await loadWeatheringData()
  await loadVibrationData()
  await loadForceChainData()
  memoryInfo.value = viewer?.getMemoryInfo() || ''
}

async function loadFemResult() {
  try {
    const res = await axios.get(`/api/simulation/fem/${selectedBridge.value}`)
    const fem = res.data.data
    if (fem && fem.nodeData) {
      lastFemData = fem.nodeData
      const nodes = fem.nodeData
      minStress.value = Math.min(...nodes.map(n => n.stress || 0))
      maxStress.value = Math.max(...nodes.map(n => n.stress || 0))
      if (showHeatmap.value) viewer.applyHeatmap(nodes)
    }
  } catch (e) {
    console.warn('加载FEM结果失败:', e.message)
  }
}

async function loadSensors() {
  let sensors = []
  try {
    const res = await axios.get(`/api/bridges/${selectedBridge.value}/sensors`)
    sensors = res.data.data || []
  } catch (e) {
    console.warn('加载传感器失败:', e.message)
    sensors = [
      { id: 1, name: '拱顶应变计', type: 'strain', position: { x: 0, y: 5, z: 0 } },
      { id: 2, name: '左拱脚位移计', type: 'displacement', position: { x: -15, y: 0.5, z: 0 } },
      { id: 3, name: '右拱脚位移计', type: 'displacement', position: { x: 15, y: 0.5, z: 0 } }
    ]
  }
  viewer.placeSensors(sensors)
}

async function loadCracks() {
  let cracks = []
  try {
    const res = await axios.get(`/api/bridges/${selectedBridge.value}/sensors`)
    const all = res.data.data || []
    cracks = all.filter(s => s.type === 'crack')
  } catch (e) {
    cracks = [
      { id: 1, name: '左拱腹裂缝', position: { x: -8, y: 3.2, z: -2 }, length: 1.5, depth: 0.05 },
      { id: 2, name: '右拱脚裂缝', position: { x: 12, y: 0.8, z: 1.5 }, length: 0.8, depth: 0.03 }
    ]
  }
  viewer.renderCracks(cracks)
}

function handleSensorClick(sensor) {
  selectedSensor.value = sensor
  emit('sensor-click', sensor)
  loadTrendData(sensor.id)
}

async function loadTrendData(sensorId) {
  let data = []
  try {
    const res = await axios.get(`/api/data/sensors/${sensorId}/trend?days=365`)
    data = res.data.data || []
    const values = data.map(d => d.value != null ? d.value : d.avgValue)
    latestValue.value = values.length ? values[values.length - 1] : 0
  } catch (e) {
    console.warn('加载趋势数据失败:', e.message)
    data = generateDemoTrend(365)
    latestValue.value = data[data.length - 1].value
  }
  drawTrendChart(data)
}

function generateDemoTrend(days) {
  const data = []
  const now = new Date()
  let v = 50
  for (let i = days * 24; i >= 0; i -= 24) {
    const d = new Date(now.getTime() - i * 3600000)
    v += (Math.random() - 0.48) * 3
    data.push({ timestamp: d.toISOString(), value: v })
  }
  return data
}

function drawTrendChart(data) {
  nextTick(() => {
    if (!trendChart.value) return
    if (!chartInstance) chartInstance = echarts.init(trendChart.value)
    chartInstance.setOption({
      grid: { left: 40, right: 10, top: 10, bottom: 25 },
      xAxis: { type: 'time', axisLabel: { fontSize: 9 } },
      yAxis: { type: 'value', scale: true, axisLabel: { fontSize: 9 } },
      tooltip: { trigger: 'axis' },
      series: [{
        type: 'line',
        smooth: true,
        showSymbol: false,
        lineStyle: { width: 1.5, color: '#2563eb' },
        areaStyle: { color: 'rgba(37,99,235,0.1)' },
        data: data.map(d => [d.timestamp, d.value != null ? d.value : d.avgValue])
      }]
    })
  })
}

function onToggleHeatmap() {
  showHeatmap.value = !showHeatmap.value
  if (showHeatmap.value) {
    if (lastFemData) viewer.applyHeatmap(lastFemData)
    else loadFemResult()
  } else {
    viewer.removeHeatmap()
  }
  memoryInfo.value = viewer?.getMemoryInfo() || ''
}

function onToggleCracks() {
  showCracks.value = !showCracks.value
  viewer.setCracksVisible(showCracks.value)
}

async function loadWeatheringData() {
  try {
    const res = await axios.get(`/api/weathering/history/${selectedBridge.value}`)
    const data = res.data.data || []
    if (data.length > 0) {
      lastWeatheringData = data
      if (showWeathering.value) viewer.applyWeatheringOverlay(data)
    } else {
      lastWeatheringData = generateDemoWeathering()
    }
  } catch (e) {
    console.warn('加载风化数据失败:', e.message)
    lastWeatheringData = generateDemoWeathering()
  }
}

function generateDemoWeathering() {
  const span = bridges.value.find(b => b.id === selectedBridge.value)?.spanLength || 37
  const locations = ['拱顶左侧', '拱顶中央', '拱顶右侧', '左拱腹', '右拱腹', '左拱脚', '右拱脚', '左栏杆', '右栏杆']
  const data = []
  for (let i = 0; i < locations.length; i++) {
    const t = i / (locations.length - 1)
    const x = -span / 2 + t * span
    data.push({
      id: i + 1,
      location: locations[i],
      locX: x,
      locY: 4 * 7.4 * (0.25 - (x * x) / (span * span)) + 0.5,
      locZ: (Math.random() - 0.5) * 4,
      estimatedDepth: 1 + Math.random() * 18,
      weatheringGrade: ['none', 'slight', 'moderate', 'severe', 'critical'][Math.floor(Math.random() * 5)]
    })
  }
  return data
}

function onToggleWeathering() {
  showWeathering.value = !showWeathering.value
  if (showWeathering.value) {
    if (lastWeatheringData) viewer.applyWeatheringOverlay(lastWeatheringData)
    else loadWeatheringData()
  } else {
    viewer.removeWeatheringOverlay()
  }
  memoryInfo.value = viewer?.getMemoryInfo() || ''
}

async function loadVibrationData() {
  try {
    const res = await axios.get(`/api/traffic/history/${selectedBridge.value}`)
    const data = res.data.data || []
    if (data.length > 0) {
      lastVibrationData = data.map(d => ({
        locX: d.locX || (Math.random() - 0.5) * 30,
        acceleration: d.maxAcceleration || 0.1 + Math.random() * 0.5
      }))
      if (showVibration.value) viewer.applyVibrationHeatmap(lastVibrationData)
    } else {
      lastVibrationData = generateDemoVibration()
    }
  } catch (e) {
    console.warn('加载振动数据失败:', e.message)
    lastVibrationData = generateDemoVibration()
  }
}

function generateDemoVibration() {
  const span = bridges.value.find(b => b.id === selectedBridge.value)?.spanLength || 37
  const data = []
  for (let i = 0; i < 8; i++) {
    const t = i / 7
    data.push({
      locX: -span / 2 + t * span,
      acceleration: 0.1 + Math.random() * 0.6
    })
  }
  return data
}

function onToggleVibration() {
  showVibration.value = !showVibration.value
  if (showVibration.value) {
    if (lastVibrationData) viewer.applyVibrationHeatmap(lastVibrationData)
    else loadVibrationData()
  } else {
    viewer.removeVibrationHeatmap()
  }
  memoryInfo.value = viewer?.getMemoryInfo() || ''
}

async function loadForceChainData() {
  try {
    const res = await axios.get(`/api/masonry/force-chain/${selectedBridge.value}`)
    const data = res.data.data
    if (data && data.forceChains) {
      lastForceChainData = data.forceChains
      if (showForceChain.value) viewer.renderForceChains(lastForceChainData)
    } else {
      lastForceChainData = generateDemoForceChains()
    }
  } catch (e) {
    console.warn('加载力链数据失败:', e.message)
    lastForceChainData = generateDemoForceChains()
  }
}

function generateDemoForceChains() {
  const span = bridges.value.find(b => b.id === selectedBridge.value)?.spanLength || 37
  const rise = span * 0.2
  const data = []
  const stoneCols = 12
  const stoneRows = 4
  for (let row = 0; row < stoneRows; row++) {
    for (let col = 0; col < stoneCols - 1; col++) {
      const t1 = col / (stoneCols - 1)
      const t2 = (col + 1) / (stoneCols - 1)
      const x1 = -span / 2 + t1 * span
      const x2 = -span / 2 + t2 * span
      const yOffset = row * 0.65
      const y1 = 4 * rise * (0.25 - (x1 * x1) / (span * span)) + yOffset
      const y2 = 4 * rise * (0.25 - (x2 * x2) / (span * span)) + yOffset

      data.push({
        id: data.length + 1,
        x1, y1, z1: 0,
        x2, y2, z2: 0,
        normalForce: 5000 + Math.random() * 50000,
        shearForce: Math.random() * 10000
      })

      if (row < stoneRows - 1) {
        const t3 = (col + 0.5) / (stoneCols - 1)
        const x3 = -span / 2 + t3 * span
        const y3Next = 4 * rise * (0.25 - (x3 * x3) / (span * span)) + (row + 1) * 0.65
        data.push({
          id: data.length + 1,
          x1: (x1 + x2) / 2, y1, z1: 0,
          x2: x3, y2: y3Next, z2: 0,
          normalForce: 8000 + Math.random() * 40000,
          shearForce: Math.random() * 8000
        })
      }
    }
  }
  return data
}

function onToggleForceChain() {
  showForceChain.value = !showForceChain.value
  if (showForceChain.value) {
    if (lastForceChainData) viewer.renderForceChains(lastForceChainData)
    else loadForceChainData()
  } else {
    viewer.removeForceChains()
  }
  memoryInfo.value = viewer?.getMemoryInfo() || ''
}

function onUpdateLOD() {
  viewer.setLODLevel(lodLevel.value)
  memoryInfo.value = viewer?.getMemoryInfo() || ''
}

watch(lodLevel, onUpdateLOD)
watch(showHeatmap, onToggleHeatmap)
watch(showCracks, onToggleCracks)
watch(showWeathering, onToggleWeathering)
watch(showVibration, onToggleVibration)
watch(showForceChain, onToggleForceChain)
</script>
