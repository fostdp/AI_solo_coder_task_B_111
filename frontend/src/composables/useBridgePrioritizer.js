import { ref } from 'vue';
import { useApiClient } from './useApiClient';

export function useBridgePrioritizer() {
  const { loading, error, get, post } = useApiClient();

  const result = ref(null);
  const rankings = ref([]);
  const annualPlan = ref(null);
  const expertResults = ref([]);
  const sensitivityAnalysis = ref(null);
  const groupDecisionReport = ref(null);
  const asyncStatus = ref('idle');

  const calculate = async (bridgeIds, options = {}) => {
    const payload = {
      bridgeIds,
      expertRatings: options.expertRatings || [],
      delphiMethodEnabled: options.delphiMethodEnabled ?? true,
      sensitivityAnalysisEnabled: options.sensitivityAnalysisEnabled ?? true,
      ...options
    };
    result.value = await post('/priority/calculate', payload);
    if (result.value?.rankings) rankings.value = result.value.rankings;
    if (result.value?.annualPlan) annualPlan.value = result.value.annualPlan;
    if (result.value?.expertResults) expertResults.value = result.value.expertResults;
    if (result.value?.sensitivityAnalysisPerformed) sensitivityAnalysis.value = result.value;
    if (result.value?.groupDecisionReport) groupDecisionReport.value = result.value.groupDecisionReport;
    return result.value;
  };

  const calculateAsync = async (bridgeIds, options = {}) => {
    asyncStatus.value = 'running';
    const payload = {
      bridgeIds,
      expertRatings: options.expertRatings || [],
      delphiMethodEnabled: options.delphiMethodEnabled ?? true,
      sensitivityAnalysisEnabled: options.sensitivityAnalysisEnabled ?? true,
      ...options
    };
    const response = await post('/priority/calculate/async', payload);
    return response;
  };

  const getLatestResult = async (year = 2026) => {
    result.value = await get('/priority/latest', { year });
    if (result.value?.rankings) rankings.value = result.value.rankings;
    return result.value;
  };

  const getAnnualPlan = async (year = 2026) => {
    annualPlan.value = await get(`/priority/plan/${year}`);
    return annualPlan.value;
  };

  const getHistory = async (year) => {
    return await get('/priority/history', { year });
  };

  const getPriorityLevel = (rank, total) => {
    const ratio = rank / total;
    if (ratio <= 0.2) return { level: 'critical', label: '极高优先级', color: '#EF4444', bgClass: 'bg-red-50' };
    if (ratio <= 0.4) return { level: 'high', label: '高优先级', color: '#F97316', bgClass: 'bg-orange-50' };
    if (ratio <= 0.7) return { level: 'medium', label: '中优先级', color: '#FBBF24', bgClass: 'bg-yellow-50' };
    return { level: 'low', label: '低优先级', color: '#10B981', bgClass: 'bg-green-50' };
  };

  const getConsensusLevel = (w) => {
    if (w >= 0.8) return { level: 'perfect', label: '高度一致', color: '#10B981' };
    if (w >= 0.6) return { level: 'good', label: '良好一致', color: '#3B82F6' };
    if (w >= 0.4) return { level: 'moderate', label: '中等一致', color: '#FBBF24' };
    return { level: 'low', label: '一致性差', color: '#EF4444' };
  };

  return {
    loading,
    error,
    result,
    rankings,
    annualPlan,
    expertResults,
    sensitivityAnalysis,
    groupDecisionReport,
    asyncStatus,
    calculate,
    calculateAsync,
    getLatestResult,
    getAnnualPlan,
    getHistory,
    getPriorityLevel,
    getConsensusLevel,
  };
}
