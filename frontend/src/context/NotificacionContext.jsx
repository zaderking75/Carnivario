import React, { createContext, useContext, useState, useCallback } from "react";
 
const NotificacionContext = createContext();
 
export const NotificacionProvider = ({ children }) => {
    const [notificacion, setNotificacion] = useState(null); // { mensaje, tipo }
 
    const mostrarNotificacion = useCallback((mensaje, tipo = "exito") => {
        setNotificacion({ mensaje, tipo });
        setTimeout(() => {
            setNotificacion(null);
        }, 3000);
    }, []);
 
    return (
        <NotificacionContext.Provider value={{ mostrarNotificacion }}>
            {children}
            {notificacion && (
                <div
                    className={`notificacion-toast notificacion-toast-${notificacion.tipo}`}
                    role="status"
                >
                    <span className="notificacion-toast-icono">
                        {notificacion.tipo === "error" ? "✕" : "✓"}
                    </span>
                    <span>{notificacion.mensaje}</span>
                </div>
            )}
        </NotificacionContext.Provider>
    );
};
 
export const useNotificacion = () => useContext(NotificacionContext);