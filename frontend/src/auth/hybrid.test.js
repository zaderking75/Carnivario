import { beforeEach, describe, expect, it, vi } from "vitest";

vi.mock("./msal", () => ({
    getMicrosoftAccessToken: vi.fn(),
    msalInstance: {
        getActiveAccount: vi.fn(),
        loginRedirect: vi.fn(),
        logoutRedirect: vi.fn(),
    },
    loginRequest: { scopes: ["Carnivario.Read", "Carnivario.Write"] },
}));

import api from "../api/axiosConfig";
import AuthService from "../services/AuthService";
import { getMicrosoftAccessToken, msalInstance } from "./msal";

const stored = new Map();
const storage = {
    getItem: (key) => stored.get(key) ?? null,
    setItem: (key, value) => stored.set(key, String(value)),
    removeItem: (key) => stored.delete(key),
};
const adapter = vi.fn(async (config) => ({ data: {}, status: 200, headers: {}, config }));

beforeEach(() => {
    stored.clear();
    vi.resetAllMocks();
    vi.stubGlobal("localStorage", storage);
    api.defaults.adapter = adapter;
    adapter.mockImplementation(async (config) => ({ data: {}, status: 200, headers: {}, config }));
});

describe("Encabezados de autenticacion", () => {
    it("envia solo X-Local-Token para login local", async () => {
        AuthService.saveSession("local-token", { id: 1, role: "ADMIN" });
        const result = await api.get("/user/api/me", { headers: { Authorization: "Bearer stale" } });
        expect(result.config.headers.get("X-Local-Token")).toBe("local-token");
        expect(result.config.headers.has("Authorization")).toBe(false);
        expect(getMicrosoftAccessToken).not.toHaveBeenCalled();
    });

    it("envia el access token Microsoft sin el JWT local", async () => {
        storage.setItem("authType", "microsoft");
        storage.setItem("jwtToken", "old-local-token");
        getMicrosoftAccessToken.mockResolvedValue("azure-access-token");
        const result = await api.get("/user/api/me", { headers: { "X-Local-Token": "stale" } });
        expect(result.config.headers.get("Authorization")).toBe("Bearer azure-access-token");
        expect(result.config.headers.has("X-Local-Token")).toBe(false);
    });

    it("permite cambiar de cuenta aunque la sesion Microsoft anterior haya expirado", async () => {
        storage.setItem("authType", "microsoft");
        getMicrosoftAccessToken.mockRejectedValue(new Error("expired"));
        await AuthService.login({ email: "local@example.test", password: "example" });
        await AuthService.register({ email: "new@example.test" });
        expect(getMicrosoftAccessToken).not.toHaveBeenCalled();
        for (const [config] of adapter.mock.calls) {
            expect(config.headers.has("Authorization")).toBe(false);
            expect(config.headers.has("X-Local-Token")).toBe(false);
        }
    });

    it("no convierte un fallo de renovacion en una solicitud anonima", async () => {
        storage.setItem("authType", "microsoft");
        getMicrosoftAccessToken.mockRejectedValue(new Error("expired"));
        await expect(api.get("/user/api/me")).rejects.toThrow("expired");
        expect(adapter).not.toHaveBeenCalled();
    });

    it("deja el catalogo publico sin encabezados de autenticacion", async () => {
        const result = await api.get("/planta/api");
        expect(result.config.headers.has("Authorization")).toBe(false);
        expect(result.config.headers.has("X-Local-Token")).toBe(false);
    });
});

describe("Sesiones locales y Microsoft", () => {
    it("guarda el usuario sincronizado y elimina el JWT local al entrar con Microsoft", async () => {
        AuthService.saveSession("local-token", { id: 1, role: "ADMIN" });
        getMicrosoftAccessToken.mockResolvedValue("azure-access-token");
        adapter.mockImplementation(async (config) => ({ data: { id: 2, role: "CLIENTE" }, status: 200, config }));
        await AuthService.completeMicrosoftLogin();
        expect(storage.getItem("authType")).toBe("microsoft");
        expect(storage.getItem("jwtToken")).toBeNull();
        expect(AuthService.getCurrentUser().id).toBe(2);
        expect(AuthService.isAdmin()).toBe(false);
    });

    it("limpia el usuario anterior si la sincronizacion Microsoft falla", async () => {
        AuthService.saveSession("local-token", { id: 1, role: "ADMIN" });
        getMicrosoftAccessToken.mockRejectedValue(new Error("expired"));
        await expect(AuthService.completeMicrosoftLogin()).rejects.toThrow();
        expect(AuthService.getCurrentUser()).toBeNull();
        expect(storage.getItem("authType")).toBeNull();
        expect(storage.getItem("jwtToken")).toBeNull();
    });

    it("cierra Microsoft solo cuando esa es la sesion seleccionada", async () => {
        const account = { homeAccountId: "test-account" };
        msalInstance.getActiveAccount.mockReturnValue(account);
        AuthService.saveSession("local-token", { id: 1, role: "CLIENTE" });
        await AuthService.logout();
        expect(msalInstance.logoutRedirect).not.toHaveBeenCalled();
        AuthService.saveMicrosoftSession({ id: 2, role: "CLIENTE" });
        await AuthService.logout();
        expect(msalInstance.logoutRedirect).toHaveBeenCalledWith(expect.objectContaining({ account }));
        expect(AuthService.getCurrentUser()).toBeNull();
    });

    it("un usuario almacenado corrupto no rompe la interfaz", () => {
        storage.setItem("usuarioLogueado", "not-json");
        expect(AuthService.getCurrentUser()).toBeNull();
        expect(AuthService.isAdmin()).toBe(false);
    });
});
