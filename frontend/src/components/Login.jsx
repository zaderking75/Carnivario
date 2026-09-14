import React, { useState } from "react";
import { useNavigate } from "react-router-dom";
import AuthService from "../services/AuthService";
import "../styles/panelLogin.css";

function Login() {
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [mensaje, setMensaje] = useState("");
  const [error, setError] = useState(false);
  const navigate = useNavigate();

  const handleSubmit = async (e) => {
    e.preventDefault();

    setMensaje("");
    setError(false);

    try {
      const response = await AuthService.login({ email, password });

      const { token, user } = response.data;
      AuthService.saveSession(token, user);
      const usuarioLogueado = AuthService.getCurrentUser();

      setMensaje("¡Login exitoso! Redirigiendo...");
      setError(false);

      setTimeout(() => {
        if (usuarioLogueado?.role === "ADMIN") {
          navigate("/admin");
        } else {
          navigate("/home");
        }
        window.location.reload();
      }, 1000);

    } catch (err) {
      console.error("Error completo:", err);
      setError(true);
      if (err.response && err.response.data) {
        const mensajeBackend = err.response.data.message || err.response.data.error || JSON.stringify(err.response.data);
        setMensaje("Error: " + mensajeBackend);
      } else {
        setMensaje("Error: No se pudo conectar con el servidor.");
      }
    }
  };

  return (
    <div className="login-wrapper">
      {/* Botón flotando en la esquina superior izquierda de la pantalla */}
      <button 
        type="button" 
        onClick={() => navigate("/home")} 
        className="btn-volver-flotante"
      >
        ← Volver a la tienda
      </button>

      <div className="login-container">
        <form onSubmit={handleSubmit}>
          <h2>Iniciar sesión</h2>

          {mensaje && (
            <div className={error ? "mensaje-error" : "mensaje-exito"}>
              {mensaje}
            </div>
          )}

          <div className="form-group">
            <label>Email</label>
            <input
              type="email"
              value={email}
              onChange={(e) => setEmail(e.target.value)}
              required
            />
          </div>

          <div className="form-group">
            <label>Contraseña</label>
            <input
              type="password"
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              required
            />
          </div>

          <button type="submit" className="btn-primario">Ingresar</button>

          <div className="divisor">
            <span>o</span>
          </div>

          <button
            type="button"
            className="btn-microsoft"
            onClick={() => AuthService.loginWithMicrosoft()}
          >
            <svg width="18" height="18" viewBox="0 0 21 21" xmlns="http://www.w3.org/2000/svg">
              <rect x="1" y="1" width="9" height="9" fill="#f25022"/>
              <rect x="11" y="1" width="9" height="9" fill="#7fba00"/>
              <rect x="1" y="11" width="9" height="9" fill="#00a4ef"/>
              <rect x="11" y="11" width="9" height="9" fill="#ffb900"/>
            </svg>
            Ingresar con Microsoft
          </button>

          <button
            type="button"
            className="registro-link"
            onClick={() => navigate("/registro")}
          >
            ¿No tienes cuenta? Regístrate
          </button>
        </form>
      </div>
    </div>
  );
}

export default Login;