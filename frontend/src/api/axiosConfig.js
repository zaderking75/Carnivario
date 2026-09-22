import axios from "axios";
import { getMicrosoftAccessToken } from "../auth/msal";

const baseURL = import.meta.env.PROD
    ? import.meta.env.VITE_API_GATEWAY_URL
    : "http://localhost:8081";

const api = axios.create({ baseURL });

api.interceptors.request.use(async (config) => {
    // 1. Limpiar ambas cabeceras antes de cada petición
    delete config.headers.Authorization;
    delete config.headers["X-Local-Token"];

    // Rutas públicas de auth
    if (["/user/api/login", "/user/api/register"].includes(config.url)) {
        return config;
    }

    const authType = localStorage.getItem("authType");

    if (authType === "local") {
        const token = localStorage.getItem("jwtToken");
        if (token) {
            config.headers["X-Local-Token"] = token;
        }
    } 
    
    else if (authType === "microsoft") {
        const msToken = await getMicrosoftAccessToken();
        if (msToken) {
            config.headers.Authorization = `Bearer ${msToken}`;
        }
    }

    return config;
});

export default api;