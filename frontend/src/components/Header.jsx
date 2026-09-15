import React, { useState, useEffect } from "react";
import { useNavigate,useLocation} from "react-router-dom";
import AuthService from "../services/AuthService";
import "../App.css";
import { useCarrito } from "../context/CarritoContext"

const Header = ({search, setSearch}) => {
    const location = useLocation();
    const navigate = useNavigate();
    const [usuario, setUsuario] = useState(AuthService.getCurrentUser());
    const { cantidadTotal } = useCarrito();
    const mostrarBarra = location.pathname === "/" || location.pathname === "/home" || location.pathname === "/catalogo";
    const isAdmin = AuthService.isAdmin();

    useEffect(() => {
        const userStored = JSON.parse(localStorage.getItem("usuarioLogueado"));
        if (userStored) {
            setUsuario(userStored);
        }
    }, []);
    const handleLogoClick = () => {
        if (isAdmin) {
            navigate("/admin");
        } else {
            navigate("/");
        }
    };
    const handleLogout = async () => {
        await AuthService.logout();
        setUsuario(null);
        navigate("/");
    };

    return (
        <header className="header" id="header">

            <div className="logo" onClick={handleLogoClick} style={{ cursor: "pointer" }}>
                <img src="/ui/logo_sin_fondo.png" alt="logo" />
                <h1>Carnivero</h1>
            </div>

            <div className="search-bar">
                {mostrarBarra && (
                    <input
                        type="text"
                        placeholder="Buscar planta..."
                        value={search}
                        onChange={(e) => setSearch(e.target.value)}
                    />
                )}
            </div>


            <div className="header-icons">
                {!isAdmin && (
                    <>
                        <div className="icon-cart" title="Carrito" onClick={() => navigate("/carrito")}>
                            <span className="carrito-contador">{cantidadTotal}</span>{}
                        </div>

                        <div className="icon-heart" title="Favoritos" onClick={() => navigate("/favoritos")}></div>
                    </>
                )}
                {isAdmin && (
                    <button
                        type="button"
                        onClick={() => navigate("/admin")}
                        style={{ fontSize: "0.8rem", padding: "6px 10px", cursor: "pointer" }}
                    >
                        Admin
                    </button>
                )}
                <div className="perfil-contenedor" style={{ display: "flex", flexDirection: "column", alignItems: "center" }}>
            {usuario ? (
            <>
            <span className="nav-icon" style={{ fontSize: "1.5rem" }} onClick={() => navigate("/perfil")}>👤</span>
            <span style={{ fontSize: "0.8rem", color: "#ff5722" }}>{usuario.name}</span>
            {!AuthService.isAdmin() && (
                <button
                    onClick={handleLogout}
                    style={{ fontSize: "0.7rem", padding: "2px 5px", marginTop: "5px", cursor: "pointer" }}
                >
                    Salir
                </button>)
            }
            </>
            ) : (
                <div onClick={() => navigate("/login")} style={{ cursor: "pointer", textAlign: "center" }}>
                    <span className="nav-icon" style={{ fontSize: "1.5rem" }}>👤</span>
                    <span style={{ display: "block", fontSize: "0.8rem" }}>Ingresar</span>
                </div>
            )}
            </div>
            </div>
        </header>
    );
};

export default Header;
