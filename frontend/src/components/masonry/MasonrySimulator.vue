<template>
  <div class="masonry-simulator-component">
    <div v-if="loading" class="loading-overlay">
      <div class="loading-spinner"></div>
      <p>离散元模拟计算中...</p>
      <p class="progress-text" v-if="result">已执行 {{ result?.performanceStats?.completedSteps || 0 }} 步</p>
    </div>

    <div v-if="error" class="error-banner">
      <span>{{ error }}</span>
    </div>

    <div class="async-banner" v-if="asyncStatus === 'running'">
      <span class="async-dot"></span>
      异步计算进行中... (任务ID: {{ asyncTaskId }})
      <button @click="checkStatus" class="btn-refresh">刷新状态</button>
    </div>

    <div class="component-content" v-if="result">
      <div class="performance-stats">
        <span v-if="result.performanceStats">
          <strong>计算性能：</strong>
          用时 {{ result.performanceStats.computationTimeSeconds?.toFixed(1) }}s | 
          步数 {{ result.performanceStats.completedSteps }} | 
          {{ result.parallelComputingUsed ? '并行' : '串行' }}计算 | 
          {{ result.simplifiedModelUsed ? '简化' : '完整' }}模型
        </span>
      </div>

      <div class="result-summary">
        <div class="stat-card" v-for="stat in summaryStats" :key="stat.label">
          <div class="stat-value">{{ stat.value }}</div>
          <div class="stat-label">{{ stat.label }}</div>
        </div>
      </div>

      <div class="force-chain-info">
        <h4>接触力链分布</h4>
        <div class="chain-stats">
          <span>力链数量：{{ forceChains?.length || 0 }}</span>
          <span>最大接触力：{{ maxForce?.toFixed(2) }} kN</span>
          <span>平均接触力：{{ avgForce?.toFixed(2) }} kN</span>
        </div>
        <div class="force-legend">
          <span class="legend-item">
            <span class="force-line" style="width: 3px; background: #10B981;"></span>
            弱力 (≤20%)
          </span>
          <span class="legend-item">
            <span class="force-line" style="width: 6px; background: #3B82F6;"></span>
            中等 (20-60%)
          </span>
          <span class="legend-item">
            <span class="force-line" style="width: 10px; background: #EF4444;"></span>
            强力 (>60%)
          </span>
        </div>
      </div>

      <div class="convergence-chart" v-if="result.convergenceData">
        <h4>收敛曲线</h4>
        <div class="chart-placeholder">
          <div class="convergence-info">
            <span v-if="result.converged" class="converged-badge">✓ 已收敛</span>
            <span v-else class="not-converged-badge">⚠ 未收敛</span>
            <span>最终残差：{{ result.finalResidual?.toExponential(2) }}</span>
          </div>
        </div>
      </div>
    </div>

    <div v-if="!result && !loading && asyncStatus !== 'running'" class="empty-state">
      <p>选择桥梁并点击"开始模拟"以查看砌筑工艺模拟结果</p>
    </div>
  </div>
</template>

<script setup>
import { computed } from 'vue';
import { useMasonrySimulator } from '../../composables/useMasonrySimulator';

const props = defineProps({
  bridgeId: {
    type: Number,
    required: true
  }
});

const emit = defineEmits(['simulated', 'error']);

const {
  loading,
  error,
  result,
  forceChains,
  asyncStatus,
  asyncTaskId,
  simulate,
  simulateAsync,
  checkAsyncStatus
} = useMasonrySimulator();

const maxForce = computed(() => {
  if (!forceChains.value?.length) return 0;
  return Math.max(...forceChains.value.map(f => f.magnitude || 0));
});

const avgForce = computed(() => {
  if (!forceChains.value?.length) return 0;
  const sum = forceChains.value.reduce((acc, f) => acc + (f.magnitude || 0), 0);
  return sum / forceChains.value.length;
});

const summaryStats = computed(() => {
  if (!result.value) return [];
  const maxF = maxForce.value;
  const avgF = avgForce.value;

  return [
    { label: '单元数量', value: result.value.elementCount || '-' },
    { label: '力链数量', value: forceChains.value?.length || 0 },
    { label: '最大接触力', value: maxF.toFixed(2) + ' kN' },
    { label: '平均接触力', value: avgF.toFixed(2) + ' kN' },
    { label: '计算时间', value: (result.value.performanceStats?.computationTimeSeconds || 0).toFixed(1) + ' s' },
    { label: '摩擦系数', value: result.value.frictionCoefficient?.toFixed(2) || '-' }
  ];
});

const checkStatus = () => {
  if (asyncTaskId.value) {
    checkAsyncStatus(asyncTaskId.value);
  }
};

const startSimulate = async (options) => {
  try {
    const res = await simulate(props.bridgeId, options);
    emit('simulated', res);
    return res;
  } catch (e) {
    emit('error', e);
    throw e;
  }
};

const startSimulateAsync = async (options) => {
  try {
    return await simulateAsync(props.bridgeId, options);
  } catch (e) {
    emit('error', e);
    throw e;
  }
};

defineExpose({ startSimulate, startSimulateAsync, checkStatus });
</script>

<style scoped>
.masonry-simulator-component {
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
  border-top-color: #8B5CF6;
  border-radius: 50%;
  animation: spin 1s linear infinite;
}

@keyframes spin {
  to { transform: rotate(360deg); }
}

.progress-text {
  margin-top: 8px;
  color: #6B7280;
}

.error-banner {
  background: #FEE2E2;
  color: #991B1B;
  padding: 12px 16px;
  border-radius: 8px;
  margin-bottom: 16px;
}

.async-banner {
  background: #FEF3C7;
  color: #92400E;
  padding: 12px 16px;
  border-radius: 8px;
  margin-bottom: 16px;
  display: flex;
  align-items: center;
  gap: 8px;
}

.async-dot {
  width: 10px;
  height: 10px;
  background: #FBBF24;
  border-radius: 50%;
  animation: pulse 1.5s infinite;
}

@keyframes pulse {
  0%, 100% { opacity: 1; }
  50% { opacity: 0.5; }
}

.btn-refresh {
  margin-left: auto;
  padding: 4px 12px;
  background: #F59E0B;
  color: white;
  border: none;
  border-radius: 4px;
  cursor: pointer;
}

.performance-stats {
  background: #F5F3FF;
  color: #5B21B6;
  padding: 12px 16px;
  border-radius: 8px;
  margin-bottom: 16px;
  font-size: 14px;
}

.result-summary {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(140px, 1fr));
  gap: 12px;
  margin-bottom: 24px;
}

.stat-card {
  background: #F9FAFB;
  padding: 12px;
  border-radius: 8px;
  text-align: center;
}

.stat-value {
  font-size: 20px;
  font-weight: bold;
  color: #8B5CF6;
  margin-bottom: 4px;
}

.stat-label {
  color: #6B7280;
  font-size: 12px;
}

.force-chain-info {
  background: #F9FAFB;
  padding: 16px;
  border-radius: 8px;
  margin-bottom: 16px;
}

.chain-stats {
  display: flex;
  gap: 24px;
  margin-bottom: 12px;
  font-size: 14px;
}

.force-legend {
  display: flex;
  flex-wrap: wrap;
  gap: 16px;
  padding: 12px;
  background: white;
  border-radius: 6px;
}

.legend-item {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 13px;
}

.force-line {
  display: inline-block;
  height: 12px;
  border-radius: 2px;
}

.convergence-info {
  display: flex;
  align-items: center;
  gap: 16px;
  padding: 12px;
  background: white;
  border-radius: 6px;
}

.converged-badge {
  background: #D1FAE5;
  color: #065F46;
  padding: 4px 12px;
  border-radius: 4px;
  font-weight: 600;
}

.not-converged-badge {
  background: #FEE2E2;
  color: #991B1B;
  padding: 4px 12px;
  border-radius: 4px;
  font-weight: 600;
}

.empty-state {
  text-align: center;
  padding: 48px 16px;
  color: #6B7280;
  background: #F9FAFB;
  border-radius: 8px;
}
</style>
