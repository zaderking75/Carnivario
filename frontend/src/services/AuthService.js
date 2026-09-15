import api from "../api/axiosConfig";
import {
    msalInstance,
    loginRequest
} from "../auth/msal";

const API_BASE_URL = "/user/api";

class AuthService {

    register(usuario) {
        return api.post(
            `${API_BASE_URL}/register`,
            usuario
        );
    }

    // LOGIN PROPIO
    login(credenciales) {
        return api.post(
            `${API_BASE_URL}/login`,
            credenciales
        );
    }

    // LOGIN MICROSOFT
    async loginWithMicrosoft() {
        await msalInstance.loginRedirect(
            loginRequest
        );
    }

    async completeMicrosoftLogin() {

        this.clearSession();
        localStorage.removeItem("jwtToken");
        localStorage.setItem(
            "authType",
            "microsoft"
        );

        let response;
        try {
            response = await api.get(`${API_BASE_URL}/me`);
        } catch (error) {
            this.clearSession();
            throw error;
        }

        this.saveMicrosoftSession(
            response.data
        );

        return response.data;
    }

    getAllUsers() {
        return api.get(API_BASE_URL);
    }

    getProfile() {
        return api.get(
            `${API_BASE_URL}/me`
        );
    }

    updateProfile(datosPerfil) {
        return api.put(
            `${API_BASE_URL}/update`,
            datosPerfil
        );
    }

    createUser(usuario) {
        return api.post(
            API_BASE_URL,
            usuario
        );
    }
    updateUser(id, usuario) {
        return api.put(`${API_BASE_URL}/${id}`, usuario);
    }

    deleteUser(id) {
        return api.delete(`${API_BASE_URL}/${id}`);
    }

    updateRole(id, role) {
        return api.put(
            `${API_BASE_URL}/${id}/role`,
            { role }
        );
    }

    // Se conserva este nombre porque Login.jsx ya lo usa
    saveSession(token, user) {
        this.saveLocalSession(
            token,
            user
        );
    }

    saveLocalSession(token, user) {
        localStorage.removeItem("carrito");
        localStorage.removeItem("favoritos");

        localStorage.setItem(
            "authType",
            "local"
        );

        localStorage.setItem(
            "jwtToken",
            token
        );

        this.saveUser(user);
    }

    saveMicrosoftSession(user) {
        localStorage.removeItem("carrito");
        localStorage.removeItem("favoritos");
        localStorage.removeItem("jwtToken");

        localStorage.setItem(
            "authType",
            "microsoft"
        );

        this.saveUser(user);
    }

    saveUser(user) {
        localStorage.setItem(
            "usuarioLogueado",
            JSON.stringify(user)
        );
    }

    clearSession() {
        localStorage.removeItem(
            "usuarioLogueado"
        );
        localStorage.removeItem("jwtToken");
        localStorage.removeItem("authType");
        localStorage.removeItem("carrito");
        localStorage.removeItem("favoritos");
    }

    async logout() {
        const authType = localStorage.getItem("authType");
        this.clearSession();

        if (authType === "microsoft") {

            const account =
                msalInstance.getActiveAccount();

            if (account) {
                await msalInstance.logoutRedirect({
                    account,
                    postLogoutRedirectUri:
                        import.meta.env.VITE_AZURE_REDIRECT_URI
                });
            }
        }
    }

    getCurrentUser() {
        const userStr =
            localStorage.getItem(
                "usuarioLogueado"
            );

        if (userStr) {
            try {
                return JSON.parse(userStr);
            } catch {
                this.clearSession();
            }
        }

        return null;
    }

    getCurrentRole() {
        return this.getCurrentUser()?.role || null;
    }

    isAdmin() {
        return this.getCurrentRole() === "ADMIN";
    }
}

export default new AuthService();
