<template>
  <div class="bridge-prioritizer-component">
    <div v-if="loading" class="loading-overlay">
      <div class="loading-spinner"></div>
      <p>优先级排序计算中...</p>
      <p class="progress-text" v-if="delphiMethodUsed">正在执行德尔菲法专家评分聚合...</p>
    </div>

    <div v-if="error" class="error-banner">
      <span>{{ error }}</span>
    </div>

    <div class="component-content" v-if="result">
      <div class="method-banner" v-if="result.delphiMethodUsed">
        <strong>德尔菲法群决策：</strong>
        {{ result.expertCount }} 位专家参与 | 
        共识系数：{{ (result.expertConsensusCoefficient * 100).toFixed(1) }}%
        <span :class="consensusClass">{{ consensusLevel.label }}</span>
      </div>

      <div class="sensitivity-banner" v-if="result.sensitivityAnalysisPerformed">
        <strong>权重灵敏度分析：</strong>
        排序稳定性指数：{{ (result.rankingStabilityIndex * 100).toFixed(1) }}%
        <span v-if="result.rankingStabilityIndex >= 0.8" class="stable-badge">✓ 结果稳健</span>
        <span v-else class="unstable-badge">⚠ 建议调整权重</span>
      </div>

      <div class="rankings-table">
        <h4>保护优先级排序</h4>
        <table class="data-table">
          <thead>
            <tr>
              <th>排名</th>
              <th>桥梁名称</th>
              <th>贴近度</th>
              <th>优先级</th>
              <th>紧急程度</th>
            </tr>
          </thead>
          <tbody>
            <tr 
              v-for="ranking in rankings" 
              :key="ranking.bridgeId"
              :class="getPriorityLevel(ranking.rank, rankings.length).bgClass"
            >
              <td class="rank-cell">
                <span class="rank-badge" :style="{ background: getPriorityLevel(ranking.rank, rankings.length).color }">
                  {{ ranking.rank }}
                </span>
              </td>
              <td class="bridge-name">{{ ranking.bridgeName }}</td>
              <td class="closeness-cell">
                <div class="closeness-bar-wrapper">
                  <div 
                    class="closeness-bar"
                    :style="{ width: (ranking.closeness * 100) + '%' }"
                  ></div>
                  <span class="closeness-value">{{ ranking.closeness.toFixed(3) }}</span>
                </div>
              </td>
              <td>
                <span 
                  class="priority-tag"
                  :style="{ background: getPriorityLevel(ranking.rank, rankings.length).color }"
                >
                  {{ getPriorityLevel(ranking.rank, rankings.length).label }}
                </span>
              </td>
              <td>{{ ranking.urgency || '-' }}</td>
            </tr>
          </tbody>
        </table>
      </div>

      <div class="expert-panel" v-if="expertResults?.length">
        <h4>专家评分详情</h4>
        <div class="expert-cards">
          <div v-for="(expert, idx) in expertResults" :key="idx" class="expert-card">
            <div class="expert-header">
              <span class="expert-name">{{ expert.expertName }}</span>
              <span class="expert-title">{{ expert.expertTitle }}</span>
            </div>
            <div class="expert-weight">
              个人权重：{{ (expert.expertWeight * 100).toFixed(1) }}%
            </div>
            <div class="expert-top3">
              推荐前三名：{{ expert.top3Picks?.join(' / ') || '-' }}
            </div>
          </div>
        </div>
      </div>

      <div class="decision-report" v-if="groupDecisionReport">
        <h4>群决策报告</h4>
        <div class="report-content">
          <div class="report-section">
            <strong>共识解读：</strong>
            <p>{{ groupDecisionReport.consensusInterpretation }}</p>
          </div>
          <div class="report-section" v-if="groupDecisionReport.minorityOpinions?.length">
            <strong>少数派意见：</strong>
            <ul>
              <li v-for="(opinion, idx) in groupDecisionReport.minorityOpinions" :key="idx">
                {{ opinion }}
              </li>
            </ul>
          </div>
          <div class="report-section">
            <strong>决策建议：</strong>
            <p>{{ groupDecisionReport.finalRecommendation }}</p>
          </div>
        </div>
      </div>

      <div class="annual-plan" v-if="annualPlan">
        <h4>年度保护计划 ({{ annualPlan.year }})</h4>
        <div class="plan-stats">
          <span>总预算：{{ annualPlan.totalBudget?.toLocaleString() }} 万元</span>
          <span>桥梁总数：{{ annualPlan.bridgeCount }}</span>
          <span>紧急项目：{{ annualPlan.immediateCount }} 项</span>
        </div>
      </div>
    </div>

    <div v-if="!result && !loading" class="empty-state">
      <p>选择待排序桥梁并点击"开始排序"以生成保护优先级</p>
    </div>
  </div>
</template>

<script setup>
import { computed } from 'vue';
import { useBridgePrioritizer } from '../../composables/useBridgePrioritizer';

const props = defineProps({
  bridgeIds: {
    type: Array,
    default: () => []
  }
});

const emit = defineEmits(['calculated', 'error']);

const {
  loading,
  error,
  result,
  rankings,
  annualPlan,
  expertResults,
  groupDecisionReport,
  calculate,
  getPriorityLevel,
  getConsensusLevel
} = useBridgePrioritizer();

const delphiMethodUsed = computed(() => result.value?.delphiMethodUsed);

const consensusLevel = computed(() => {
  if (!result.value?.expertConsensusCoefficient) return null;
  return getConsensusLevel(result.value.expertConsensusCoefficient);
});

const consensusClass = computed(() => {
  if (!consensusLevel.value) return '';
  return `consensus-${consensusLevel.value.level}`;
});

const startCalculate = async (options) => {
  try {
    const res = await calculate(props.bridgeIds, options);
    emit('calculated', res);
    return res;
  } catch (e) {
    emit('error', e);
    throw e;
  }
};

defineExpose({ startCalculate });
</script>

<style scoped>
.bridge-prioritizer-component {
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
  border-top-color: #F59E0B;
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

.method-banner {
  background: #ECFDF5;
  color: #065F46;
  padding: 12px 16px;
  border-radius: 8px;
  margin-bottom: 12px;
  font-size: 14px;
}

.sensitivity-banner {
  background: #FEF3C7;
  color: #92400E;
  padding: 12px 16px;
  border-radius: 8px;
  margin-bottom: 16px;
  font-size: 14px;
}

.consensus-perfect, .consensus-good { color: #059669; font-weight: 600; margin-left: 8px; }
.consensus-moderate { color: #D97706; font-weight: 600; margin-left: 8px; }
.consensus-low { color: #DC2626; font-weight: 600; margin-left: 8px; }

.stable-badge {
  background: #059669;
  color: white;
  padding: 2px 8px;
  border-radius: 4px;
  margin-left: 8px;
  font-size: 12px;
}

.unstable-badge {
  background: #DC2626;
  color: white;
  padding: 2px 8px;
  border-radius: 4px;
  margin-left: 8px;
  font-size: 12px;
}

.data-table {
  width: 100%;
  border-collapse: collapse;
  margin-bottom: 24px;
}

.data-table th {
  background: #F3F4F6;
  padding: 12px;
  text-align: left;
  font-weight: 600;
  color: #374151;
  border-bottom: 2px solid #E5E7EB;
}

.data-table td {
  padding: 12px;
  border-bottom: 1px solid #F3F4F6;
}

.rank-badge {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 32px;
  height: 32px;
  border-radius: 50%;
  color: white;
  font-weight: 700;
}

.bridge-name {
  font-weight: 600;
}

.closeness-bar-wrapper {
  position: relative;
  height: 24px;
  background: #E5E7EB;
  border-radius: 4px;
  overflow: hidden;
  min-width: 150px;
}

.closeness-bar {
  height: 100%;
  background: linear-gradient(90deg, #10B981, #3B82F6, #8B5CF6, #EF4444);
  transition: width 0.5s ease;
}

.closeness-value {
  position: absolute;
  right: 8px;
  top: 50%;
  transform: translateY(-50%);
  font-weight: 600;
  font-size: 12px;
  color: #1F2937;
}

.priority-tag {
  display: inline-block;
  padding: 4px 12px;
  border-radius: 4px;
  color: white;
  font-size: 12px;
  font-weight: 600;
}

.expert-cards {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(240px, 1fr));
  gap: 12px;
  margin-bottom: 24px;
}

.expert-card {
  background: #F9FAFB;
  padding: 12px;
  border-radius: 8px;
}

.expert-header {
  display: flex;
  justify-content: space-between;
  margin-bottom: 8px;
}

.expert-name {
  font-weight: 600;
}

.expert-title {
  color: #6B7280;
  font-size: 12px;
}

.expert-weight {
  font-size: 13px;
  color: #374151;
  margin-bottom: 4px;
}

.expert-top3 {
  font-size: 12px;
  color: #6B7280;
}

.report-content {
  background: #F9FAFB;
  padding: 16px;
  border-radius: 8px;
  margin-bottom: 24px;
}

.report-section {
  margin-bottom: 12px;
}

.report-section strong {
  color: #374151;
  display: block;
  margin-bottom: 4px;
}

.report-section p {
  color: #4B5563;
  margin: 0;
}

.report-section ul {
  margin: 0;
  padding-left: 20px;
  color: #4B5563;
}

.plan-stats {
  display: flex;
  gap: 24px;
  padding: 16px;
  background: #FFFBEB;
  border-radius: 8px;
  font-size: 14px;
}

.empty-state {
  text-align: center;
  padding: 48px 16px;
  color: #6B7280;
  background: #F9FAFB;
  border-radius: 8px;
}
</style>
