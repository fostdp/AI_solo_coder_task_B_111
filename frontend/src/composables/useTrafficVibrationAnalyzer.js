import { ref } from 'vue';
import { useApiClient } from './useApiClient';

export function useTrafficVibrationAnalyzer() {
  const { loading, error, get, post } = useApiClient();

  const result = ref(null);
  const trafficFlow = ref([]);
  const limitAdvice = ref(null);

  const analyze = async (bridgeId, options = {}) => {
    const payload = {
      bridgeId,
      pavementParams: options.pavementParams || null,
      vehicleTypes: options.vehicleTypes || ['passenger', 'truck_light', 'truck_medium', 'truck_heavy', 'bus'],
      ...options
    };
    result.value = await post('/traffic/vibration/analyze', payload);
    if (result.value?.loadLimitAdvice) {
      limitAdvice.value = result.value.loadLimitAdvice;
    }
    return result.value;
  };

  const getTrafficFlow = async (bridgeId, hours = 24) => {
    trafficFlow.value = await get(`/traffic/flow/${bridgeId}`, { hours });
    return trafficFlow.value;
  };

  const getHistory = async (bridgeId, startDate, endDate) => {
    return await get(`/traffic/vibration/history/${bridgeId}`, { startDate, endDate });
  };

  const getDAFLevel = (daf) => {
    if (daf <= 1.2) return { level: 'low', label: '低', color: '#10B981' };
    if (daf <= 1.6) return { level: 'medium', label: '中', color: '#FBBF24' };
    if (daf <= 2.0) return { level: 'high', label: '高', color: '#F97316' };
    return { level: 'critical', label: '极高', color: '#EF4444' };
  };

  return {
    loading,
    error,
    result,
    trafficFlow,
    limitAdvice,
    analyze,
    getTrafficFlow,
    getHistory,
    getDAFLevel,
  };
}
