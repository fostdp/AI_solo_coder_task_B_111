import { ref, reactive } from 'vue';
import axios from 'axios';

const API_BASE = import.meta.env.VITE_API_BASE || 'http://localhost:8080/api';

export function useApiClient() {
  const loading = ref(false);
  const error = ref(null);
  const response = ref(null);

  const client = axios.create({
    baseURL: API_BASE,
    timeout: 30000,
    headers: {
      'Content-Type': 'application/json',
    },
  });

  const request = async (config) => {
    loading.value = true;
    error.value = null;
    try {
      const res = await client.request(config);
      response.value = res.data;
      return res.data;
    } catch (err) {
      error.value = err.response?.data?.message || err.message;
      throw err;
    } finally {
      loading.value = false;
    }
  };

  const get = (url, params) => request({ method: 'GET', url, params });
  const post = (url, data) => request({ method: 'POST', url, data });

  return {
    loading,
    error,
    response,
    get,
    post,
    client,
  };
}
