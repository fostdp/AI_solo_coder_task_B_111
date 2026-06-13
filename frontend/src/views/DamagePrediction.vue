<template>
  <div class="p-6 space-y-6">
    <div>
      <h1 class="text-2xl font-bold text-stone-800">损伤演化预测</h1>
      <p class="text-sm text-stone-500">Paris公式 + 贝叶斯MCMC在线标定</p>
    </div>

    <div class="grid grid-cols-3 gap-6">
      <div class="bg-white rounded-lg shadow p-4">
        <h2 class="text-base font-semibold mb-4 text-stone-700">预测参数</h2>
        <div class="space-y-3">
          <div>
            <label class="block text-xs text-stone-500 mb-1">选择桥梁</label>
            <select v-model.number="form.bridgeId" @change="onBridgeChange"
              class="w-full border border-stone-300 rounded px-2 py-1.5 text-sm">
              <option v-for="b in bridges" :key="b.id" :value="b.id">{{ b.name }}</option>
            </select>
          </div>
          <div>
            <label class="block text-xs text-stone-500 mb-1">裂缝传感器</label>
            <select v-model.number="form.crackSensorId" class="w-full border border-stone-300 rounded px-2 py-1.5 text-sm">
              <option v-for="s in crackSensors" :key="s.id" :value="s.id">{{ s.name || `裂缝计${s.id}` }}</option>
            </select>
          </div>
          <div>
            <label class="block text-xs text-stone-500 mb-1">初始裂缝长度(mm)</label>
            <input type="number" step="0.1" v-model.number="form.initialLength" @change="emitParamsChange"
              class="w-full border border-stone-300 rounded px-2 py-1.5 text-sm" />
          </div>
          <div>
            <label class="block text-xs text-stone-500 mb-1">预测年限(年)</label>
            <input type="number" v-model.number="form.yearsToPredict" @change="emitParamsChange"
              class="w-full border border-stone-300 rounded px-2 py-1.5 text-sm" />
          </div>
          <div>
            <label class="flex items-center gap-2 text-sm text-stone-700">
              <input type="checkbox" v-model="form.enableBayesian" @change="emitParamsChange" />
              <span>启用贝叶斯在线标定</span>
            </label>
          </div>
          <div v-if="form.enableBayesian" class="pl-4 space-y-2 bg-stone-50 p-2 rounded text-xs">
            <div>
              <label class="block text-stone-500 mb-1">MCMC样本数</label>
              <input type="number" v-model.number="form.mcmcSamples" @change="emitParamsChange"
                class="w-full border border-stone-300 rounded px-2 py-1 text-xs" />
            </div>
            <div>
              <label class="block text-stone-500 mb-1">先验 C 均值</label>
              <input type="text" v-model="form.priorC_mean" @change="emitParamsChange"
                class="w-full border border-stone-300 rounded px-2 py-1 text-xs" />
            </div>
            <div>
              <label class="block text-stone-500 mb-1">先验 m 均值</label>
              <input type="number" step="0.1" v-model.number="form.priorM_mean" @change="emitParamsChange"
                class="w-full border border-stone-300 rounded px-2 py-1 text-xs" />
            </div>
          </div>
          <div>
            <label class="block text-xs text-stone-500 mb-1">应力幅 Δσ(Pa)</label>
            <input type="number" v-model.number="form.stressAmplitude" @change="emitParamsChange"
              class="w-full border border-stone-300 rounded px-2 py-1.5 text-sm" />
          </div>
          <button @click="onCalculate"
            class="w-full bg-primary text-white rounded py-2 text-sm font-medium hover:bg-blue-900 transition">
            计算预测
          </button>
        </div>
      </div>

      <div class="col-span-2 space-y-6">
        <div class="bg-white rounded-lg shadow p-4">
          <h2 class="text-base font-semibold mb-3 text-stone-700">裂缝扩展预测曲线</h2>
          <div ref="predChart" class="h-72"></div>
        </div>

        <div v-if="result" class="grid grid-cols-2 gap-6">
          <div class="bg-white rounded-lg shadow p-4">
            <h2 class="text-base font-semibold mb-3 text-stone-700">预测结果摘要</h2>
            <div class="space-y-2 text-sm">
              <div class="flex justify-between">
                <span class="text-stone-500">初始裂缝长度</span>
                <span class="font-medium">{{ formatNum(result.initialLength) }} mm</span>
              </div>
              <div class="flex justify-between">
                <span class="text-stone-500">{{ form.yearsToPredict }}年后预测长度</span>
                <span class="font-medium text-red-600">{{ finalLength }} mm</span>
              </div>
              <div class="flex justify-between">
                <span class="text-stone-500">年均扩展率</span>
                <span class="font-medium">{{ avgGrowthRate }} mm/年</span>
              </div>
              <div class="flex justify-between">
                <span class="text-stone-500">建议维修年份</span>
                <span class="font-medium text-orange-600">
                  {{ result.maintenanceYear || '5年内无需维修' }}
                </span>
              </div>
              <div class="flex justify-between">
                <span class="text-stone-500">Paris 参数 C</span>
                <span class="font-mono text-xs">{{ formatSci(result.parisC) }}</span>
              </div>
              <div class="flex justify-between">
                <span class="text-stone-500">Paris 参数 m</span>
                <span class="font-mono text-xs">{{ formatNum(result.parisM) }}</span>
              </div>
              <div v-if="result.isBayesian" class="pt-2 border-t border-stone-100">
                <div class="text-xs text-green-700 font-medium">✓ 贝叶斯在线标定已启用</div>
                <div class="text-xs text-stone-500 mt-1">
                  后验 C: {{ formatSci(result.parisCPosteriorMean) }} (±{{ formatSci(result.parisCPosteriorStd) }})<br/>
                  后验 m: {{ formatNum(result.parisMPosteriorMean) }} (±{{ formatNum(result.parisMPosteriorStd) }})<br/>
                  MCMC样本: {{ result.mcmcSamples }}
                </div>
              </div>
            </div>
          </div>

          <div class="bg-white rounded-lg shadow p-4">
            <h2 class="text-base font-semibold mb-3 text-stone-700">维修建议</h2>
            <div class="text-sm text-stone-600 leading-relaxed">
              {{ result.recommendation || '运行预测后显示建议' }}
            </div>
            <div v-if="result.predictionData" class="mt-4 space-y-1">
              <div v-for="p in result.predictionData" :key="p.year"
                class="flex justify-between items-center text-xs p-1.5 rounded"
                :class="p.risk === 'danger' ? 'bg-red-50' : p.risk === 'warning' ? 'bg-yellow-50' : 'bg-green-50'">
                <span class="text-stone-600">{{ p.year }}年</span>
                <span class="font-medium">{{ formatNum(p.length) }} mm</span>
                <span :class="riskClass(p.risk)">{{ riskText(p.risk) }}</span>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted, onUnmounted, watch } from 'vue'
import { DamagePanelController } from '@/utils/damage_panel'

const bridges = ref([])
const crackSensors = ref([])
const result = ref(null)
const predChart = ref(null)

const form = reactive({
  bridgeId: 1,
  crackSensorId: 1,
  initialLength: 1.5,
  yearsToPredict: 5,
  annualCycles: 365,
  stressAmplitude: 5000000,
  enableBayesian: true,
  mcmcSamples: 10000,
  priorC_mean: 1e-12,
  priorM_mean: 3.0
})

let ctrl = null

const finalLength = computed(() => DamagePanelController.getFinalLength(result.value))
const avgGrowthRate = computed(() => DamagePanelController.getAvgGrowthRate(result.value, form.yearsToPredict))

function formatNum(v) { return DamagePanelController.formatNum(v) }
function formatSci(v) { return DamagePanelController.formatSci(v) }
function riskClass(r) { return DamagePanelController.riskClass(r) }
function riskText(r) { return DamagePanelController.riskText(r) }

function emitParamsChange() {
  if (!ctrl) return
  ctrl.setParams({ ...form })
}

async function onBridgeChange() {
  if (!ctrl) return
  ctrl.setParam('bridgeId', form.bridgeId)
  crackSensors.value = await ctrl.loadCrackSensors(form.bridgeId)
  if (crackSensors.value.length) {
    form.crackSensorId = crackSensors.value[0].id
    ctrl.setParam('crackSensorId', form.crackSensorId)
  }
}

async function onCalculate() {
  if (!ctrl) return
  ctrl.setParams({ ...form })
  result.value = await ctrl.calculate()
}

onMounted(async () => {
  ctrl = new DamagePanelController({
    onResultChange: (r) => { result.value = r }
  })
  bridges.value = await ctrl.loadBridges()
  if (bridges.value.length) {
    form.bridgeId = bridges.value[0].id
    crackSensors.value = await ctrl.loadCrackSensors(form.bridgeId)
    if (crackSensors.value.length) form.crackSensorId = crackSensors.value[0].id
  }
  if (predChart.value) ctrl.setChartElement(predChart.value)
  window.addEventListener('resize', () => ctrl?.resize())
})

watch(predChart, (el) => {
  if (el && ctrl) ctrl.setChartElement(el)
})

onUnmounted(() => {
  ctrl?.dispose()
  ctrl = null
})
</script>
