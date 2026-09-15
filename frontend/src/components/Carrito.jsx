import React, { useState } from "react";
import { useNavigate } from "react-router-dom";
import AuthService from "../services/AuthService";
import api from "../api/axiosConfig";
import { useCarrito } from "../context/CarritoContext";
import { useNotificacion } from "../context/NotificacionContext";
import "../styles/panelCarrito.css";
import { resolverImagen } from "../utils/imageUrl";

const Carrito = () => {
    const navigate = useNavigate();
    const { carrito, modificarCantidad, eliminarProducto, vaciarCarrito, total } = useCarrito();
    const { mostrarNotificacion } = useNotificacion();
    const [loading, setLoading] = useState(false);

    const handleCheckout = async () => {
        if (loading) return;
        const usuario = AuthService.getCurrentUser();
        if (!usuario) {
            mostrarNotificacion("Debes iniciar sesión para comprar.", "error");
            navigate("/login");
            return;
        }
        setLoading(true);

        try {
            const items = carrito.map(item => ({
                idPlanta: item.id,
                cantidad: item.cantidad
            }));

            await api.post("/checkout/api", { items });

            mostrarNotificacion("¡Compra realizada con éxito! Revisa tu correo para la confirmación.");

            vaciarCarrito(); 
            navigate("/home");

        } catch (error) {
            console.error("Error al comprar:", error);
            if (error.response && error.response.status === 400) {
                mostrarNotificacion("Error: " + (error.response.data?.message || error.response.data), "error");
            } else {
                mostrarNotificacion("Hubo un error al procesar la compra. Intenta de nuevo.", "error");
            }
        } finally {
            setLoading(false);
        }
    };

    if (carrito.length === 0) {
        return (
            <div className="carrito-container">
                <h2 className="mensaje-vacio">El carrito está vacío 🛒</h2>
                <div style={{textAlign: 'center', marginTop: '20px'}}>
                    <button onClick={() => navigate("/")} className="btn-checkout">Ir a comprar</button>
                </div>
            </div>
        );
    }

    return (
        <div className="carrito-container">
            <h1 className="carrito-titulo">Tu Carrito de Compras</h1>

            <table className="tabla-carrito">
                <thead>
                <tr>
                    <th>Producto</th>
                    <th>Precio</th>
                    <th>Cantidad</th>
                    <th>Subtotal</th>
                    <th>Acción</th>
                </tr>
                </thead>
                <tbody>
                {carrito.map((item) => (
                    <tr key={item.id}>
                        <td style={{display: 'flex', alignItems: 'center', gap: '10px'}}>
                            <img src={resolverImagen(item.image)} alt={item.name} className="item-img" />
                            <span>{item.name}</span>
                        </td>
                        <td>${item.price.toLocaleString('es-CL')}</td>
                        <td>
                            <button className="btn-cantidad" onClick={() => modificarCantidad(item.id, -1)} disabled={loading}>-</button>
                            {item.cantidad}
                            <button className="btn-cantidad" onClick={() => modificarCantidad(item.id, 1)} disabled={loading}>+</button>
                        </td>
                        <td>${(item.price * item.cantidad).toLocaleString('es-CL')}</td>
                        <td>
                            <button className="btn-eliminar" onClick={() => eliminarProducto(item.id)} disabled={loading}>🗑️</button>
                        </td>
                    </tr>
                ))}
                </tbody>
            </table>

            <div className="carrito-resumen">
                <h2>Total: ${total.toLocaleString('es-CL')}</h2>
                <button className="btn-checkout" onClick={handleCheckout} disabled={loading}>
                    {loading ? "Procesando..." : "Finalizar Compra"}
                </button>
            </div>
        </div>
    );
};

export default Carrito;