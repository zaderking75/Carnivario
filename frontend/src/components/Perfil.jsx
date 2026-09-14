import React, { useEffect, useState } from "react";
import { useNavigate } from "react-router-dom";
import AuthService from "../services/AuthService";
import "../styles/panelPerfil.css";

const camposIniciales = {
    name: "",
    lastname: "",
    email: "",
    phone: "",
    address: "",
    commune: "",
    role: "CLIENTE"
};

function Perfil() {
    const navigate = useNavigate();
    const [perfil, setPerfil] = useState({ ...camposIniciales, ...AuthService.getCurrentUser() });
    const [editando, setEditando] = useState(false);
    const [cargando, setCargando] = useState(true);
    const [guardando, setGuardando] = useState(false);
    const [mensaje, setMensaje] = useState("");
    const [esError, setEsError] = useState(false);

    useEffect(() => {
        let activo = true;

        const cargarPerfil = async () => {
            try {
                const response = await AuthService.getProfile();
                if (!activo) return;
                setPerfil(response.data);
                AuthService.saveUser(response.data);
            } catch (error) {
                if (!activo) return;
                if (error.response?.status === 401 || error.response?.status === 403) {
                    AuthService.logout();
                    navigate("/login", { replace: true });
                    return;
                }
                setEsError(true);
            } finally {
                if (activo) setCargando(false);
            }
        };

        cargarPerfil();
        return () => { activo = false; };
    }, [navigate]);

    const handleChange = (event) => {
        const { name, value } = event.target;
        setPerfil((actual) => ({ ...actual, [name]: value }));
    };

    const guardarCambios = async (event) => {
        event.preventDefault();
        setGuardando(true);
        setMensaje("");
        setEsError(false);

        try {
            const datosEditables = {
                name: perfil.name,
                lastname: perfil.lastname,
                phone: perfil.phone,
                address: perfil.address,
                commune: perfil.commune
            };
            const response = await AuthService.updateProfile(datosEditables);
            setPerfil(response.data);
            AuthService.saveUser(response.data);
            setEditando(false);
            setMensaje("Tus datos se actualizaron correctamente.");
        } catch (error) {
            setEsError(true);
            setMensaje(error.response?.data?.message || "No fue posible guardar los cambios. Inténtalo nuevamente.");
        } finally {
            setGuardando(false);
        }
    };

    const cancelarEdicion = async () => {
        setEditando(false);
        setMensaje("");
        try {
            const response = await AuthService.getProfile();
            setPerfil(response.data);
        } catch {
            setPerfil({ ...camposIniciales, ...AuthService.getCurrentUser() });
        }
    };

    const cerrarSesion = () => {
        AuthService.logout();
        navigate("/", { replace: true });
        window.location.reload();
    };

    const iniciales = `${perfil.name?.charAt(0) || ""}${perfil.lastname?.charAt(0) || ""}`.toUpperCase() || "U";
    const rolVisible = perfil.role === "ADMIN" ? "Administrador" : "Cliente";

    if (cargando) {
        return <div className="perfil-cargando">Cargando tu perfil...</div>;
    }

    return (
        <section className="perfil-pagina">
            <div className="perfil-cabecera">
                <button type="button" className="perfil-volver" onClick={() => navigate(-1)} aria-label="Volver">
                    ← Volver
                </button>
                <div className="perfil-avatar" aria-hidden="true">{iniciales}</div>
                <div className="perfil-identidad">
                    <span className="perfil-etiqueta">MI CUENTA</span>
                    <h1>{perfil.name} {perfil.lastname}</h1>
                    <p>{perfil.email}</p>
                    <span className="perfil-rol">{rolVisible}</span>
                </div>
            </div>

            <div className="perfil-contenido">
                <aside className="perfil-menu" aria-label="Opciones de la cuenta">
                    <button type="button" className="perfil-menu-activo">👤 Información personal</button>
                    <button type="button" onClick={() => navigate("/favoritos")}>♥ Mis favoritos</button>
                    <button type="button" onClick={() => navigate("/carrito")}>🛒 Mi carrito</button>
                    {perfil.role === "ADMIN" && (
                        <button type="button" onClick={() => navigate("/admin")}>⚙ Panel administrador</button>
                    )}
                    <button type="button" className="perfil-cerrar-sesion" onClick={cerrarSesion}>Cerrar sesión</button>
                </aside>

                <div className="perfil-tarjeta">
                    <div className="perfil-tarjeta-titulo">
                        <div>
                            <h2>Información personal</h2>
                            <p>Revisa y mantén actualizados tus datos de contacto y despacho.</p>
                        </div>
                        {!editando && (
                            <button type="button" className="perfil-editar" onClick={() => { setEditando(true); setMensaje(""); }}>
                                Editar datos
                            </button>
                        )}
                    </div>

                    {mensaje && (
                        <div className={esError ? "perfil-mensaje perfil-mensaje-error" : "perfil-mensaje perfil-mensaje-exito"} role="status">
                            {mensaje}
                        </div>
                    )}

                    <form onSubmit={guardarCambios} className="perfil-formulario">
                        <div className="perfil-campo">
                            <label htmlFor="perfil-name">Nombre</label>
                            <input id="perfil-name" name="name" value={perfil.name || ""} onChange={handleChange} disabled={!editando} maxLength={50} required />
                        </div>
                        <div className="perfil-campo">
                            <label htmlFor="perfil-lastname">Apellido</label>
                            <input id="perfil-lastname" name="lastname" value={perfil.lastname || ""} onChange={handleChange} disabled={!editando} maxLength={50} required />
                        </div>
                        <div className="perfil-campo perfil-campo-completo">
                            <label htmlFor="perfil-email">Correo electrónico</label>
                            <input id="perfil-email" type="email" value={perfil.email || ""} disabled />
                            <small>El correo es el identificador utilizado para iniciar sesión.</small>
                        </div>
                        <div className="perfil-campo">
                            <label htmlFor="perfil-phone">Teléfono</label>
                            <input id="perfil-phone" name="phone" value={perfil.phone || ""} onChange={handleChange} disabled={!editando} maxLength={50} required />
                        </div>
                        <div className="perfil-campo">
                            <label htmlFor="perfil-commune">Comuna</label>
                            <input id="perfil-commune" name="commune" value={perfil.commune || ""} onChange={handleChange} disabled={!editando} maxLength={50} required />
                        </div>
                        <div className="perfil-campo perfil-campo-completo">
                            <label htmlFor="perfil-address">Dirección</label>
                            <input id="perfil-address" name="address" value={perfil.address || ""} onChange={handleChange} disabled={!editando} maxLength={50} required />
                        </div>

                        {editando && (
                            <div className="perfil-acciones">
                                <button type="button" className="perfil-cancelar" onClick={cancelarEdicion} disabled={guardando}>Cancelar</button>
                                <button type="submit" className="perfil-guardar" disabled={guardando}>
                                    {guardando ? "Guardando..." : "Guardar cambios"}
                                </button>
                            </div>
                        )}
                    </form>
                </div>
            </div>
        </section>
    );
}

export default Perfil;
