import api from "../api/axiosConfig";

const API_BASE_URL = "/user/api";
class AuthService {

    register(usuario) {
        return api.post(`${API_BASE_URL}/register`, usuario);
    }

    login(credenciales) {
        return api.post(`${API_BASE_URL}/login`, credenciales);
    }

    getAllUsers() {
        return api.get(API_BASE_URL);
    }

    createUser(usuario) {
        return api.post(API_BASE_URL, usuario);
    }

    updateRole(id, role) {
        return api.put(`${API_BASE_URL}/${id}/role`, { role });
    }

    saveSession(token, user) {
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
