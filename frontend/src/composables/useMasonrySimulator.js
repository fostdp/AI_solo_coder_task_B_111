import { ref } from 'vue';
import { useApiClient } from './useApiClient';

export function useMasonrySimulator() {
  const { loading, error, get, post } = useApiClient();

  const result = ref(null);
  const forceChains = ref([]);
  const asyncStatus = ref('idle');
  const asyncTaskId = ref(null);

  const simulate = async (bridgeId, options = {}) => {
    const payload = {
      bridgeId,
      computationMode: options.computationMode || 'standard',
      parallelComputingEnabled: options.parallelComputingEnabled ?? true,
      simplifiedContactModelEnabled: options.simplifiedContactModelEnabled ?? true,
      ...options
    };
    result.value = await post('/masonry/simulate', payload);
    if (result.value?.forceChains) {
      forceChains.value = result.value.forceChains;
    }
    return result.value;
  };

  const simulateAsync = async (bridgeId, options = {}) => {
    asyncStatus.value = 'running';
    const payload = {
      bridgeId,
      computationMode: options.computationMode || 'standard',
      parallelComputingEnabled: options.parallelComputingEnabled ?? true,
      simplifiedContactModelEnabled: options.simplifiedContactModelEnabled ?? true,
      ...options
    };
    const response = await post('/masonry/simulate/async', payload);
    asyncTaskId.value = response.taskId;
    return response;
  };

  const checkAsyncStatus = async (taskId) => {
    const response = await get(`/masonry/simulate/status/${taskId}`);
    asyncStatus.value = response.status;
    if (response.status === 'completed' && response.result) {
      result.value = response.result;
      if (response.result.forceChains) {
        forceChains.value = response.result.forceChains;
      }
    }
    return response;
  };

  const getAnalysisTypes = () => {
    return get('/masonry/analysis-types');
  };

  const getHistory = async (bridgeId, startDate, endDate) => {
    return await get(`/masonry/history/${bridgeId}`, { startDate, endDate });
  };

  const getComputationModeOptions = () => [
    { value: 'fast', label: '快速模式', steps: 200 },
    { value: 'standard', label: '标准模式', steps: 500 },
    { value: 'fine', label: '精细模式', steps: 1000 },
  ];

  return {
    loading,
    error,
    result,
    forceChains,
    asyncStatus,
    asyncTaskId,
    simulate,
    simulateAsync,
    checkAsyncStatus,
    getAnalysisTypes,
    getHistory,
    getComputationModeOptions,
  };
}
