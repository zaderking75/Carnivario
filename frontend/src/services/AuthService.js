import api from "../api/axiosConfig";

const API_BASE_URL = "/user/api";
class AuthService {

    register(usuario) {
        return api.post(`${API_BASE_URL}/register`, usuario);
    }

    login(credenciales) {
        return api.post(`${API_BASE_URL}/login`, credenciales);
    }

    loginWithMicrosoft() {
    window.location.href =
        `${api.defaults.baseURL}/oauth2/authorization/azure`;
    }

    async completeMicrosoftLogin(token) {
        localStorage.setItem("jwtToken", token);

        try {
            const response = await api.get(
                `${API_BASE_URL}/me`
            );

            this.saveSession(token, response.data);

            return response.data;
        } catch (error) {
            this.logout();
            throw error;
        }
    }

    getAllUsers() {
        return api.get(API_BASE_URL);
    }

    getProfile() {
        return api.get(`${API_BASE_URL}/me`);
    }

    updateProfile(datosPerfil) {
        return api.put(`${API_BASE_URL}/update`, datosPerfil);
    }

    createUser(usuario) {
        return api.post(API_BASE_URL, usuario);
    }

    updateRole(id, role) {
        return api.put(`${API_BASE_URL}/${id}/role`, { role });
    }

    saveSession(token, user) {
        localStorage.removeItem("carrito");
        localStorage.removeItem("favoritos");
        localStorage.setItem("jwtToken", token);
        this.saveUser(this.withRoleFromToken(user, token));
    }

    saveUser(user) {
        localStorage.setItem("usuarioLogueado", JSON.stringify(user));
    }
    logout() {
        localStorage.removeItem("usuarioLogueado");
        localStorage.removeItem("jwtToken");
    }

    getCurrentUser() {
        const userStr = localStorage.getItem("usuarioLogueado");
        if (userStr) {
            return JSON.parse(userStr);
        }
        return null;
    }

    getCurrentRole() {
        return this.getCurrentUser()?.role || this.getRoleFromToken(localStorage.getItem("jwtToken"));
    }

    isAdmin() {
        return this.getCurrentRole() === "ADMIN";
    }

    withRoleFromToken(user, token) {
        const tokenRole = this.getRoleFromToken(token);
        return tokenRole ? { ...user, role: tokenRole } : user;
    }

    getRoleFromToken(token) {
        if (!token) return null;

        try {
            const payload = JSON.parse(atob(token.split(".")[1]));
            return payload.role || null;
        } catch (error) {
            return null;
        }
    }
}

export default new AuthService();
