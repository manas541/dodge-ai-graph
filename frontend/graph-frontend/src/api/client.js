import axios from 'axios';

const BASE_URL = import.meta.env.VITE_API_URL || 'http://localhost:8080';

const api = axios.create({
  baseURL: BASE_URL,
  headers: { 'Content-Type': 'application/json' },
});

export const fetchGraph    = ()      => api.get('/api/graph');
export const fetchNeighbor = (id)    => api.get(`/api/graph/node/${id}`);
export const fetchStats    = ()      => api.get('/api/graph/stats');
export const sendMessage   = (msg)   => api.post('/api/chat/query', { message: msg });
export const clearChat     = ()      => api.post('/api/chat/clear');