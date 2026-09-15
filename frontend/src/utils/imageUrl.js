const BACKEND_URL = import.meta.env.PROD
    ? import.meta.env.VITE_API_GATEWAY_URL
    : "http://localhost:8081";

export function resolverImagen(rutaImagen) {
    if (!rutaImagen) return "";
    if (rutaImagen.startsWith("http")) return rutaImagen;
    return `${BACKEND_URL}${rutaImagen}`;
}   