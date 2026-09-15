import { StrictMode } from "react";
import { createRoot } from "react-dom/client";
import { MsalProvider } from "@azure/msal-react";

import "./index.css";
import App from "./App.jsx";
import { msalInstance } from "./auth/msal";
import AuthService from "./services/AuthService";

function loginFailed() {
    AuthService.clearSession();
    sessionStorage.setItem("authError", "No se pudo completar el ingreso con Microsoft. Intenta nuevamente.");
    window.history.replaceState({}, document.title, "/login");
}

async function bootstrap() {

    await msalInstance.initialize();

    let redirectResult = null;

    try {
        redirectResult =
            await msalInstance.handleRedirectPromise();
    } catch (error) {
        console.error(
            "Error procesando redirect Microsoft:",
            error
        );
        loginFailed();
    }

    if (redirectResult?.account) {

        msalInstance.setActiveAccount(
            redirectResult.account
        );

        localStorage.setItem(
            "authType",
            "microsoft"
        );

        localStorage.removeItem("jwtToken");

        try {
            const user =
                await AuthService.completeMicrosoftLogin();

            const destination =
                user?.role === "ADMIN"
                    ? "/admin"
                    : "/home";

            window.history.replaceState(
                {},
                document.title,
                destination
            );

        } catch (error) {
            console.error(
                "No se pudo sincronizar usuario Microsoft:",
                error
            );
            loginFailed();
        }

    } else {

        const accounts =
            msalInstance.getAllAccounts();

        if (accounts.length > 0) {
            msalInstance.setActiveAccount(
                accounts[0]
            );
        } else if (localStorage.getItem("authType") === "microsoft") {
            AuthService.clearSession();
        }
    }

    // Migrar las sesiones locales anteriores al encabezado X-Local-Token.
    if (!localStorage.getItem("authType") && localStorage.getItem("jwtToken")) {
        localStorage.setItem("authType", "local");
    }

    createRoot(
        document.getElementById("root")
    ).render(
        <StrictMode>
            <MsalProvider instance={msalInstance}>
                <App />
            </MsalProvider>
        </StrictMode>
    );
}

bootstrap();
