import React, { createContext, useContext, useState, useEffect } from "react";
import CarritoService from "../services/CarritoService";
const CarritoContext = createContext();

export const CarritoProvider = ({ children }) => {
    const [carrito, setCarrito] = useState(() => CarritoService.getCarrito());

    useEffect(() => {
        CarritoService.guardarCarrito(carrito);
    }, [carrito]);

    
    const agregarProducto = (planta, cantidadAAgregar = 1) => {
        const stockReal = parseInt(planta.stock);
        let resultado = { ok: true, mensaje: `¡${planta.name} añadida al carrito!` };

        setCarrito(prev => {
            const indice = prev.findIndex(item => item.id === planta.id);

            if (indice !== -1) {
                const cantidadEnCarrito = parseInt(prev[indice].cantidad);
                const nuevaCantidadTotal = cantidadEnCarrito + cantidadAAgregar;

                if (nuevaCantidadTotal > stockReal) {
                    resultado = {
                        ok: false,
                        mensaje: `No puedes añadir ${cantidadAAgregar} más. Ya tienes ${cantidadEnCarrito} en el carrito y el stock máximo es ${stockReal}.`
                    };
                    return prev;
                }

                const nuevo = [...prev];
                nuevo[indice] = { ...nuevo[indice], cantidad: nuevaCantidadTotal };
                return nuevo;
            } else {
                if (cantidadAAgregar > stockReal) {
                    resultado = { ok: false, mensaje: "No hay suficiente stock." };
                    return prev;
                }
                return [...prev, { ...planta, cantidad: cantidadAAgregar }];
            }
        });

        return resultado;
    };

    const modificarCantidad = (id, delta) => {
        setCarrito(prev => prev.map(item => {
            if (item.id === id) {
                const nuevaCant = item.cantidad + delta;
                return { ...item, cantidad: nuevaCant < 1 ? 1 : nuevaCant };
            }
            return item;
        }));
    };

    const eliminarProducto = (id) => {
        setCarrito(prev => prev.filter(item => item.id !== id));
    };

    const vaciarCarrito = () => {
        setCarrito([]);
        CarritoService.vaciarCarrito();
    };

    const cantidadTotal = carrito.reduce((acc, item) => acc + (parseInt(item.cantidad) || 0), 0);
    const total = carrito.reduce((acc, item) => acc + (item.price * item.cantidad), 0);

    return (
        <CarritoContext.Provider value={{
            carrito,
            agregarProducto,
            modificarCantidad,
            eliminarProducto,
            vaciarCarrito,
            cantidadTotal,
            total
        }}>
            {children}
        </CarritoContext.Provider>
    );
};

export const useCarrito = () => useContext(CarritoContext);