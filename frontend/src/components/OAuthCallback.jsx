import React, { useEffect, useState } from "react";
import { useNavigate } from "react-router-dom";
import AuthService from "../services/AuthService";

const OAuthCallback = () => {
    const navigate = useNavigate();

    const [mensaje, setMensaje] = useState(
        "Iniciando sesión con Microsoft..."
    );

    useEffect(() => {
        const completarLogin = async () => {
            const parametros = new URLSearchParams(
                window.location.search
            );

            const token = parametros.get("token");

            if (!token) {
                setMensaje(
                    "Microsoft no devolvió un token válido."
                );
                return;
            }

            try {
                const usuario =
                    await AuthService.completeMicrosoftLogin(
                        token
                    );

                if (usuario.role === "ADMIN") {
                    window.location.replace("/admin");
                } else {
                    window.location.replace("/");
                }

            } catch (error) {
                console.error(
                    "Error en login Microsoft:",
                    error
                );

                setMensaje(
                    "No se pudo completar el inicio de sesión."
                );
            }
        };

        completarLogin();
    }, [navigate]);

    return (
        <div
            style={{
                padding: "50px",
                textAlign: "center"
            }}
        >
            <h2>{mensaje}</h2>
        </div>
    );
};

export default OAuthCallback;