import { ref } from 'vue';
import { useApiClient } from './useApiClient';

export function useWeatheringEvaluator() {
  const { loading, error, get, post } = useApiClient();

  const result = ref(null);
  const measurements = ref([]);
  const dataQuality = ref(null);

  const evaluate = async (bridgeId, options = {}) => {
    const payload = {
      bridgeId,
      measurementPoints: options.measurementPoints || [],
      useHardness: options.useHardness ?? true,
      useVelocity: options.useVelocity ?? true,
      dataQualityCheckEnabled: options.dataQualityCheckEnabled ?? true,
      ...options
    };
    result.value = await post('/weathering/evaluate', payload);
    if (result.value?.dataQualityReport) {
      dataQuality.value = result.value.dataQualityReport;
    }
    return result.value;
  };

  const getLatestData = async (bridgeId, limit = 100) => {
    measurements.value = await get(`/weathering/data/${bridgeId}`, { limit });
    return measurements.value;
  };

  const getHistory = async (bridgeId, startDate, endDate) => {
    return await get(`/weathering/history/${bridgeId}`, { startDate, endDate });
  };

  const getWeatheringGrade = (depth) => {
    if (depth <= 2) return { grade: 'none', label: '无风化', color: '#10B981' };
    if (depth <= 5) return { grade: 'slight', label: '轻微', color: '#3B82F6' };
    if (depth <= 10) return { grade: 'moderate', label: '中等', color: '#FBBF24' };
    if (depth <= 20) return { grade: 'severe', label: '严重', color: '#F97316' };
    return { grade: 'critical', label: '极严重', color: '#EF4444' };
  };

  return {
    loading,
    error,
    result,
    measurements,
    dataQuality,
    evaluate,
    getLatestData,
    getHistory,
    getWeatheringGrade,
  };
}
