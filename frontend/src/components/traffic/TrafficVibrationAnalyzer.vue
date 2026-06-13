<template>
  <div class="traffic-vibration-component">
    <div v-if="loading" class="loading-overlay">
      <div class="loading-spinner"></div>
      <p>振动分析计算中...</p>
    </div>

    <div v-if="error" class="error-banner">
      <span>{{ error }}</span>
    </div>

    <div class="component-content" v-if="result">
      <div class="pavement-info" v-if="result.pavementDampingApplied">
        <strong>铺装层阻尼已应用：</strong>
        {{ result.pavementThickness }}m × {{ result.pavementDampingRatio }}阻尼比
        (修正系数: {{ result.pavementCorrectionFactor?.toFixed(3) }})
      </div>

      <div class="result-summary">
        <div class="stat-card" v-for="stat in summaryStats" :key="stat.label">
          <div class="stat-value" :style="{ color: stat.color }">
            {{ stat.value }}
          </div>
          <div class="stat-label">{{ stat.label }}</div>
        </div>
      </div>

      <div class="daf-analysis">
        <h4>动力放大系数 (DAF)</h4>
        <div class="daf-chart">
          <div 
            v-for="item in dafByVehicleType" 
            :key="item.type"
            class="daf-bar"
          >
            <div class="bar-label">{{ item.label }}</div>
            <div class="bar-wrapper">
              <div 
                class="bar-fill" 
                :style="{ width: (item.daf / 3) * 100 + '%', backgroundColor: item.color }"
              ></div>
              <span class="bar-value">{{ item.daf.toFixed(2) }}</span>
            </div>
          </div>
        </div>
        <div class="daf-legend">
          <span class="legend-item"><span class="legend-dot" style="background: #10B981"></span>安全 (≤1.2)</span>
          <span class="legend-item"><span class="legend-dot" style="background: #FBBF24"></span>注意 (1.2-1.6)</span>
          <span class="legend-item"><span class="legend-dot" style="background: #F97316"></span>警告 (1.6-2.0)</span>
          <span class="legend-item"><span class="legend-dot" style="background: #EF4444"></span>危险 (>2.0)</span>
        </div>
      </div>

      <div class="limit-advice" v-if="limitAdvice">
        <h4>限载建议</h4>
        <div class="advice-card" :class="adviceSeverity">
          <p><strong>建议限重：</strong>{{ limitAdvice.recommendedLimitTons }} 吨</p>
          <p><strong>安全评估：</strong>{{ limitAdvice.safetyAssessment }}</p>
          <p v-if="limitAdvice.restrictions"><strong>限制措施：</strong>{{ limitAdvice.restrictions }}</p>
        </div>
      </div>
    </div>

    <div v-if="!result && !loading" class="empty-state">
      <p>选择桥梁并点击"开始分析"以查看交通振动分析结果</p>
    </div>
  </div>
</template>

<script setup>
import { computed } from 'vue';
import { useTrafficVibrationAnalyzer } from '../../composables/useTrafficVibrationAnalyzer';

const props = defineProps({
  bridgeId: {
    type: Number,
    required: true
  }
});

const emit = defineEmits(['analyzed', 'error']);

const { loading, error, result, limitAdvice, analyze, getDAFLevel } = useTrafficVibrationAnalyzer();

const summaryStats = computed(() => {
  if (!result) return [];
  const maxDAF = Math.max(...Object.values(result.dafByVehicleType || {}));
  const maxLevel = getDAFLevel(maxDAF);
  const avgAccel = result.maxAcceleration?.toFixed(4) || 'N/A';
  const maxDisplace = result.maxDisplacement?.toFixed(4) || 'N/A';

  return [
    { label: '最大DAF', value: maxDAF.toFixed(2), color: maxLevel.color },
    { label: '最大加速度', value: avgAccel + ' m/s²', color: '#3B82F6' },
    { label: '最大位移', value: maxDisplace + ' m', color: '#8B5CF6' },
    { label: '基频', value: (result.naturalFrequency || 0).toFixed(2) + ' Hz', color: '#10B981' }
  ];
});

const dafByVehicleType = computed(() => {
  if (!result?.dafByVehicleType) return [];
  const typeLabels = {
    passenger: '小轿车',
    truck_light: '轻卡',
    truck_medium: '中卡',
    truck_heavy: '重卡',
    bus: '公交车'
  };

  return Object.entries(result.dafByVehicleType).map(([type, daf]) => {
    const level = getDAFLevel(daf);
    return {
      type,
      label: typeLabels[type] || type,
      daf,
      color: level.color
    };
  }).sort((a, b) => b.daf - a.daf);
});

const adviceSeverity = computed(() => {
  if (!limitAdvice) return '';
  const assessment = limitAdvice.safetyAssessment || '';
  if (assessment.includes('安全')) return 'advice-green';
  if (assessment.includes('限制')) return 'advice-yellow';
  return 'advice-red';
});

const startAnalyze = async (options) => {
  try {
    const res = await analyze(props.bridgeId, options);
    emit('analyzed', res);
  } catch (e) {
    emit('error', e);
  }
};

defineExpose({ startAnalyze });
</script>

<style scoped>
.traffic-vibration-component {
  position: relative;
  padding: 16px;
}

.loading-overlay {
  position: absolute;
  inset: 0;
  background: rgba(255, 255, 255, 0.9);
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  z-index: 10;
}

.loading-spinner {
  width: 48px;
  height: 48px;
  border: 4px solid #E5E7EB;
  border-top-color: #3B82F6;
  border-radius: 50%;
  animation: spin 1s linear infinite;
}

@keyframes spin {
  to { transform: rotate(360deg); }
}

.error-banner {
  background: #FEE2E2;
  color: #991B1B;
  padding: 12px 16px;
  border-radius: 8px;
  margin-bottom: 16px;
}

.pavement-info {
  background: #EFF6FF;
  color: #1E40AF;
  padding: 12px 16px;
  border-radius: 8px;
  margin-bottom: 16px;
}

.result-summary {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(160px, 1fr));
  gap: 16px;
  margin-bottom: 24px;
}

.stat-card {
  background: #F9FAFB;
  padding: 16px;
  border-radius: 8px;
  text-align: center;
}

.stat-value {
  font-size: 24px;
  font-weight: bold;
  margin-bottom: 4px;
}

.stat-label {
  color: #6B7280;
  font-size: 14px;
}

.daf-bar {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 8px;
}

.bar-label {
  width: 60px;
  font-size: 14px;
}

.bar-wrapper {
  flex: 1;
  height: 28px;
  background: #E5E7EB;
  border-radius: 4px;
  overflow: hidden;
  position: relative;
}

.bar-fill {
  height: 100%;
  transition: width 0.3s ease;
}

.bar-value {
  position: absolute;
  right: 8px;
  top: 50%;
  transform: translateY(-50%);
  font-weight: 600;
  font-size: 13px;
  color: #1F2937;
}

.daf-legend {
  display: flex;
  flex-wrap: wrap;
  gap: 16px;
  margin-top: 16px;
  padding: 12px;
  background: #F9FAFB;
  border-radius: 8px;
}

.legend-item {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 13px;
}

.legend-dot {
  width: 12px;
  height: 12px;
  border-radius: 50%;
}

.advice-card {
  padding: 16px;
  border-radius: 8px;
}

.advice-green { background: #D1FAE5; }
.advice-yellow { background: #FEF3C7; }
.advice-red { background: #FEE2E2; }

.empty-state {
  text-align: center;
  padding: 48px 16px;
  color: #6B7280;
  background: #F9FAFB;
  border-radius: 8px;
}
</style>
