import { InteractionRequiredAuthError, PublicClientApplication } from "@azure/msal-browser";

export const apiScopes = {
    read: import.meta.env.VITE_AZURE_SCOPE_READ,
    write: import.meta.env.VITE_AZURE_SCOPE_WRITE
};

export const msalConfig = {
    auth: {
        clientId: import.meta.env.VITE_AZURE_CLIENT_ID,
        authority:
            `https://login.microsoftonline.com/${import.meta.env.VITE_AZURE_TENANT_ID}`,
        redirectUri: import.meta.env.VITE_AZURE_REDIRECT_URI,
        postLogoutRedirectUri: import.meta.env.VITE_AZURE_REDIRECT_URI,
        navigateToLoginRequestUrl: false
    },

    cache: {
        cacheLocation: "sessionStorage"
    }
};

export const loginRequest = {
    scopes: [
        "openid",
        "profile",
        "email",
        apiScopes.read,
        apiScopes.write
    ]
};

export const msalInstance =
    new PublicClientApplication(msalConfig);

let interactiveRequest;

export async function getMicrosoftAccessToken() {
    const account = msalInstance.getActiveAccount();
    if (!account) throw new Error("Tu sesion Microsoft termino. Vuelve a iniciar sesion.");
    try {
        const response = await msalInstance.acquireTokenSilent({
            account, scopes: [apiScopes.read, apiScopes.write]
        });
        if (!response.accessToken) throw new Error("Microsoft no devolvio un access token.");
        return response.accessToken;
    } catch (error) {
        if (error instanceof InteractionRequiredAuthError) {
            interactiveRequest ??= msalInstance.acquireTokenRedirect({ account, ...loginRequest })
                .finally(() => { interactiveRequest = undefined; });
            await interactiveRequest;
        }
        throw error;
    }
}
