import React, { useEffect, useState } from 'react';
import PlantaService from '../services/PlantaService';
import { useNavigate } from "react-router-dom";
import AuthService from "../services/AuthService";
import '../App.css';
import FavoritoService from "../services/FavoritoService";
import { useCarrito } from "../context/CarritoContext";
import { useNotificacion } from "../context/NotificacionContext";

const Catalogo = ({ search = "" }) => {
    const [plantas, setPlantas] = useState([]);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState("");
    const navigate = useNavigate();
    const [, setUpdate] = useState(0);
    const { agregarProducto } = useCarrito();
    const { mostrarNotificacion } = useNotificacion(); 

    const handleToggleFavorito = (id) => {
        if (!AuthService.getCurrentUser()) {
            mostrarNotificacion("Debes iniciar sesión para guardar favoritos.", "error");
            navigate("/login");
            return;
        }
        FavoritoService.toggleFavorito(id);
        setUpdate(prev => prev + 1);
    };

    useEffect(() => {
        setLoading(true);
        setError("");
        PlantaService.getAllPlanta()
            .then(response => {
                setPlantas(Array.isArray(response.data) ? response.data : []);
            })
            .catch(error => {
                console.error("Error cargando plantas:", error);
                setError("No se pudieron cargar los productos.");
                setPlantas([]);
            })
            .finally(() => {
                setLoading(false);
            });
    }, []);

    const anadirAlCarrito = (planta) => {
        const usuario = AuthService.getCurrentUser();

        if (!usuario) {
            mostrarNotificacion("Debes iniciar sesión para añadir productos al carrito.", "error");
            navigate("/login");
            return;
        }

        const resultado = agregarProducto(planta, 1);
        mostrarNotificacion(resultado.mensaje, resultado.ok ? "exito" : "error");
    };

    const visiblePlants = plantas.filter((plant) =>
        (plant.name || "").toLowerCase().includes(search.toLowerCase())
    );

    return (
        <main className="catalogo-container">
            {loading && <p className="catalogo-status">Cargando productos...</p>}
            {!loading && error && <p className="catalogo-status catalogo-status-error">{error}</p>}
            {!loading && !error && visiblePlants.length === 0 && (
                <p className="catalogo-status">
                    {plantas.length === 0 ? "No hay productos registrados." : "No se encontraron productos."}
                </p>
            )}
            <div className="productos-grid">
                {visiblePlants.map((planta) => {
                    const agotado = planta.stock <= 0;
                    const esFav = FavoritoService.esFavorito(planta.id);

                    return (
                        <div
                            key={planta.id}
                            className="store-container"
                            onClick={() => navigate(`/producto/${planta.id}`)}
                            style={{ cursor: 'pointer' }}
                        >
                            <div style={{ position: 'relative' }}>
                                <img
                                    src={planta.image}
                                    alt={planta.name}
                                    style={agotado ? { filter: 'grayscale(100%)' } : {}}
                                />
                                <span
                                    onClick={(e) => {
                                        e.stopPropagation();
                                        handleToggleFavorito(planta.id);
                                    }}
                                    style={{
                                        position: 'absolute', top: '10px', right: '10px',
                                        fontSize: '1.5rem', cursor: 'pointer',
                                        filter: 'drop-shadow(0 0 2px white)'
                                    }}
                                >
                                    {esFav ? "❤️" : "🤍"}
                                </span>
                                {agotado && (
                                    <span style={{
                                        position: 'absolute', top: '10px', right: '10px',
                                        background: 'red', color: 'white', padding: '5px',
                                        borderRadius: '5px', fontSize: '0.8rem'
                                    }}>AGOTADO</span>
                                )}
                            </div>

                            <div className="content">
                                <h3>{planta.name}</h3>
                                <p>{planta.description}</p>
                                <p><strong>${Number(planta.price || 0).toLocaleString('es-CL')}</strong></p>
                                {!agotado && <p style={{ fontSize: '0.8rem', color: '#666' }}>Stock: {planta.stock}</p>}

                                <button
                                    type="button"
                                    disabled={agotado}
                                    className={agotado ? "btn-agotado" : ""}
                                    onClick={(e) => {
                                        e.stopPropagation();
                                        anadirAlCarrito(planta);
                                    }}
                                    style={agotado ? { backgroundColor: '#ccc', cursor: 'not-allowed' } : {}}
                                >
                                    {agotado ? "Sin Stock" : "Añadir al carrito"}
                                </button>
                            </div>
                        </div>
                    );
                })}
            </div>
        </main>
    );
};

export default Catalogo;