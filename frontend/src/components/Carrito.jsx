import React, { useState, useEffect } from "react";
import { useNavigate } from "react-router-dom";
import AuthService from "../services/AuthService";
import CompraService from "../services/CompraService";
import "../styles/panelCarrito.css";
import CarritoService from "../services/CarritoService";

const Carrito = () => {
    const navigate = useNavigate();
    const [carrito, setCarrito] = useState([]);
    const [total, setTotal] = useState(0);

    useEffect(() => {
        const carritoGuardado = CarritoService.getCarrito();
        setCarrito(carritoGuardado);
        calcularTotal(carritoGuardado);
    }, []);

    const calcularTotal = (items) => {
        const suma = items.reduce((acc, item) => acc + (item.price * item.cantidad), 0);
        setTotal(suma);
    };

    const modificarCantidad = (id, delta) => {
        const nuevoCarrito = carrito.map(item => {
            if (item.id === id) {
                const nuevaCant = item.cantidad + delta;
                return { ...item, cantidad: nuevaCant < 1 ? 1 : nuevaCant };
            }
            return item;
        });
        setCarrito(nuevoCarrito);
        CarritoService.guardarCarrito(nuevoCarrito);
        calcularTotal(nuevoCarrito);
    };

    const eliminarProducto = (id) => {
        const nuevoCarrito = carrito.filter(item => item.id !== id);
        setCarrito(nuevoCarrito);
        CarritoService.guardarCarrito(nuevoCarrito);
        calcularTotal(nuevoCarrito);
        window.location.reload();
    };

    const handleCheckout = async () => {
        const usuario = AuthService.getCurrentUser();
        if (!usuario) {
            alert("Debes iniciar sesión para comprar.");
            navigate("/login");
            return;
        }

        try {
            for (const item of carrito) {
                const compraRequest = {
                    idPlanta: item.id,
                    quantity: item.cantidad
                };
                await CompraService.createPurchase(compraRequest);
            }

            alert("¡Compra realizada con éxito! Gracias por tu preferencia.");

            CarritoService.vaciarCarrito();
            setCarrito([]);
            setTotal(0);
            window.location.reload();

        } catch (error) {
            console.error("Error al comprar:", error);
            if (error.response && error.response.status === 400) {
                alert("Error: " + (error.response.data?.message || error.response.data));
            } else {
                alert("Hubo un error al procesar la compra. Intenta de nuevo.");
            }
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
                            <img src={item.image} alt={item.name} className="item-img" />
                            <span>{item.name}</span>
                        </td>
                        <td>${item.price.toLocaleString('es-CL')}</td>
                        <td>
                            <button className="btn-cantidad" onClick={() => modificarCantidad(item.id, -1)}>-</button>
                            {item.cantidad}
                            <button className="btn-cantidad" onClick={() => modificarCantidad(item.id, 1)}>+</button>
                        </td>
                        <td>${(item.price * item.cantidad).toLocaleString('es-CL')}</td>
                        <td>
                            <button className="btn-eliminar" onClick={() => eliminarProducto(item.id)}>🗑️</button>
                        </td>
                    </tr>
                ))}
                </tbody>
            </table>

            <div className="carrito-resumen">
                <h2>Total: ${total.toLocaleString('es-CL')}</h2>
                <button className="btn-checkout" onClick={handleCheckout}>Finalizar Compra</button>
            </div>
        </div>
    );
};

export default Carrito;