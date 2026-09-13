import axios from 'axios';

const baseURL = import.meta.env.MODE === 'production' ? 'http://54.84.229.217:8081' : 'http://localhost:8081';
const api = axios.create({
    baseURL: baseURL
});
api.interceptors.request.use(
    (config) => {
        const token = localStorage.getItem('jwtToken');

        if (token) {
            config.headers.Authorization = `Bearer ${token}`;
        }

        return config;
    },
    (error) => {
        return Promise.reject(error);
    }
);

export default api;