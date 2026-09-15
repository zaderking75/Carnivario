import axios from "axios";
import { getMicrosoftAccessToken } from "../auth/msal";

const baseURL = import.meta.env.PROD
    ? import.meta.env.VITE_API_GATEWAY_URL
    : "http://localhost:8081";

const api = axios.create({ baseURL });

api.interceptors.request.use(async (config) => {
    // Un cambio de cuenta no debe enviar las credenciales de la sesion anterior.
    delete config.headers.Authorization;
    delete config.headers["X-Local-Token"];
    if (["/user/api/login", "/user/api/register"].includes(config.url)) {
        return config;
    }
    const authType = localStorage.getItem("authType");
    if (authType === "local") {
        const token = localStorage.getItem("jwtToken");
        if (token) config.headers["X-Local-Token"] = token;
    } else if (authType === "microsoft") {
        // Si falla la renovacion, no enviar la peticion como anonima.
        config.headers.Authorization = `Bearer ${await getMicrosoftAccessToken()}`;
    }
    return config;
});

export default api;
