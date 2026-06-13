<template>
  <div class="weathering-evaluator-component">
    <div v-if="loading" class="loading-overlay">
      <div class="loading-spinner"></div>
      <p>风化评估计算中...</p>
    </div>

    <div v-if="error" class="error-banner">
      <span>{{ error }}</span>
    </div>

    <div class="component-content" v-if="result">
      <div class="quality-banner" :class="qualityClass">
        <strong>数据质量：</strong>
        {{ dataQuality?.passRate?.toFixed(1) }}% 合格率
        <span v-if="dataQuality?.recommendation" class="recommendation">
          | 建议：{{ dataQuality.recommendation }}
        </span>
      </div>

      <div class="result-summary">
        <div class="stat-card" v-for="stat in summaryStats" :key="stat.label">
          <div class="stat-value" :style="{ color: stat.color }">
            {{ stat.value }}
          </div>
          <div class="stat-label">{{ stat.label }}</div>
        </div>
      </div>

      <div class="result-chart">
        <h4>风化深度分布</h4>
        <div class="chart-placeholder">
          <div v-for="item in depthDistribution" :key="item.grade" class="depth-bar">
            <div class="bar-label">{{ item.label }}</div>
            <div class="bar-wrapper">
              <div 
                class="bar-fill" 
                :style="{ width: item.percentage + '%', backgroundColor: item.color }"
              ></div>
            </div>
            <div class="bar-percentage">{{ item.percentage.toFixed(1) }}%</div>
          </div>
        </div>
      </div>
    </div>

    <div v-if="!result && !loading" class="empty-state">
      <p>选择桥梁并点击"开始评估"以查看风化分析结果</p>
    </div>
  </div>
</template>

<script setup>
import { computed } from 'vue';
import { useWeatheringEvaluator } from '../../composables/useWeatheringEvaluator';

const props = defineProps({
  bridgeId: {
    type: Number,
    required: true
  },
  autoEvaluate: {
    type: Boolean,
    default: false
  }
});

const emit = defineEmits(['evaluated', 'error']);

const { loading, error, result, dataQuality, evaluate } = useWeatheringEvaluator();

const qualityClass = computed(() => {
  if (!dataQuality?.passRate) return '';
  if (dataQuality.passRate >= 80) return 'quality-good';
  if (dataQuality.passRate >= 60) return 'quality-warning';
  return 'quality-error';
});

const summaryStats = computed(() => {
  if (!result?.estimatedDepths) return [];
  const depths = Object.values(result.estimatedDepths);
  const avgDepth = depths.reduce((a, b) => a + b, 0) / depths.length;
  const maxDepth = Math.max(...depths);
  const avgGrade = useWeatheringEvaluator().getWeatheringGrade(avgDepth);
  const maxGrade = useWeatheringEvaluator().getWeatheringGrade(maxDepth);

  return [
    { label: '平均深度', value: avgDepth.toFixed(2) + ' mm', color: avgGrade.color },
    { label: '最大深度', value: maxDepth.toFixed(2) + ' mm', color: maxGrade.color },
    { label: '风化等级', value: avgGrade.label, color: avgGrade.color },
    { label: 'R²拟合度', value: (result.rSquared * 100).toFixed(1) + '%', color: '#10B981' }
  ];
});

const depthDistribution = computed(() => {
  if (!result?.estimatedDepths) return [];
  const counts = { none: 0, slight: 0, moderate: 0, severe: 0, critical: 0 };
  const total = Object.keys(result.estimatedDepths).length;
  const { getWeatheringGrade } = useWeatheringEvaluator();

  Object.values(result.estimatedDepths).forEach(depth => {
    const { grade } = getWeatheringGrade(depth);
    counts[grade]++;
  });

  return [
    { grade: 'none', label: '无风化', percentage: (counts.none / total) * 100, color: '#10B981' },
    { grade: 'slight', label: '轻微', percentage: (counts.slight / total) * 100, color: '#3B82F6' },
    { grade: 'moderate', label: '中等', percentage: (counts.moderate / total) * 100, color: '#FBBF24' },
    { grade: 'severe', label: '严重', percentage: (counts.severe / total) * 100, color: '#F97316' },
    { grade: 'critical', label: '极严重', percentage: (counts.critical / total) * 100, color: '#EF4444' }
  ];
});

const startEvaluate = async (options) => {
  try {
    const res = await evaluate(props.bridgeId, options);
    emit('evaluated', res);
  } catch (e) {
    emit('error', e);
  }
};

defineExpose({ startEvaluate });
</script>

<style scoped>
.weathering-evaluator-component {
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

.quality-banner {
  padding: 12px 16px;
  border-radius: 8px;
  margin-bottom: 16px;
}

.quality-good { background: #D1FAE5; color: #065F46; }
.quality-warning { background: #FEF3C7; color: #92400E; }
.quality-error { background: #FEE2E2; color: #991B1B; }

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

.depth-bar {
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
  height: 24px;
  background: #E5E7EB;
  border-radius: 4px;
  overflow: hidden;
}

.bar-fill {
  height: 100%;
  transition: width 0.3s ease;
}

.bar-percentage {
  width: 60px;
  text-align: right;
  font-size: 14px;
  color: #6B7280;
}

.empty-state {
  text-align: center;
  padding: 48px 16px;
  color: #6B7280;
  background: #F9FAFB;
  border-radius: 8px;
}
</style>
