import React, { useState, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import PlantaService from '../services/PlantaService';
import CompraService from "../services/CompraService";
import AuthService from "../services/AuthService";
import { BarChart, Bar, XAxis, YAxis, CartesianGrid, Tooltip, Legend, ResponsiveContainer } from 'recharts';
import '../styles/AdminDashboard.css';

const initialPlanta = {
    name: "", price: "", description: "", image: "/image/default.jpg", stock: 10, planting: "", size: ""
};

const initialUsuario = {
    name: "", lastname: "", email: "", password: "", phone: "", address: "", commune: "", role: "CLIENTE"
};

const AdminDashboard = () => {
    const navigate = useNavigate();
    const [activeTab, setActiveTab] = useState('resumen');
    const [plantas, setPlantas] = useState([]);
    const [usuarios, setUsuarios] = useState([]);
    const [datosGrafico, setDatosGrafico] = useState([]);
    const [selectedFile, setSelectedFile] = useState(null);
    
    // Estado para acumular cambios de stock pendientes de confirmar
    const [cambiosStockPendientes, setCambiosStockPendientes] = useState({});

    // Estados para Plantas
    const [newPlanta, setNewPlanta] = useState(initialPlanta);
    const [editandoPlantaId, setEditandoPlantaId] = useState(null);

    // Estados para Usuarios
    const [newUsuario, setNewUsuario] = useState(initialUsuario);
    const [editandoUsuarioId, setEditandoUsuarioId] = useState(null);

    const [mensaje, setMensaje] = useState("");
    const [error, setError] = useState("");

    useEffect(() => {
        if (!AuthService.isAdmin()) {
            navigate("/login");
            return;
        }

        cargarDatos();
        cargarUsuarios();
    }, [navigate]);

    const getErrorMessage = (error, fallback) => {
        if (error?.response?.status === 401) return "Tu sesion no es valida. Inicia sesion nuevamente.";
        if (error?.response?.status === 403) return "No tienes permisos para esta operacion.";
        const data = error?.response?.data;
        if (typeof data === "string") return data;
        return data?.message || data?.error || fallback;
    };

    const showSuccess = (text) => {
        setMensaje(text);
        setError("");
    };

    const showError = (text) => {
        setError(text);
        setMensaje("");
    };

    const cargarDatos = async () => {
        try {
            const [resPlantas, resCompras] = await Promise.all([
                PlantaService.getAllPlanta(),
                CompraService.getAllPurchases()
            ]);

            setPlantas(resPlantas.data);

            const dataProcesada = resPlantas.data.map(planta => {
                const comprasDeEstaPlanta = resCompras.data.filter(c => c.idPlanta === planta.id);
                const totalVendido = comprasDeEstaPlanta.reduce((acc, compra) => acc + compra.quantity, 0);

                return {
                    name: planta.name.substring(0, 10),
                    ventas: totalVendido,
                    stock: planta.stock
                };
            });

            setDatosGrafico(dataProcesada);
        } catch (error) {
            showError(getErrorMessage(error, "Error cargando datos del dashboard."));
        }
    };

    const cargarPlantas = async () => {
        try {
            const res = await PlantaService.getAllPlanta();
            setPlantas(res.data);
        } catch (error) {
            showError(getErrorMessage(error, "Error cargando plantas."));
        }
    };

    const cargarUsuarios = async () => {
        try {
            const res = await AuthService.getAllUsers();
            setUsuarios(res.data);
        } catch (error) {
            showError(getErrorMessage(error, "Error cargando usuarios."));
        }
    };

    const handleModificarStockLocal = (id, nombrePlanta, stockActual, cantidad) => {
        setCambiosStockPendientes(prev => {
            const cambioActual = prev[id] ? prev[id].cambio : 0;
            const nuevoCambio = cambioActual + cantidad;
            
            if (stockActual + nuevoCambio < 0) {
                return prev;
            }

            if (nuevoCambio === 0) {
                const copia = { ...prev };
                delete copia[id];
                return copia;
            }

            return {
                ...prev,
                [id]: { nombre: nombrePlanta, stockActual, cambio: nuevoCambio }
            };
        });
    };

    const confirmarCambiosStock = async () => {
        try {
            const promesas = Object.entries(cambiosStockPendientes).map(([id, data]) => {
                return PlantaService.addStock(id, data.cambio);
            });

            await Promise.all(promesas);

            showSuccess("¡Stock actualizado masivamente con éxito!");
            setCambiosStockPendientes({});
            await cargarDatos();
        } catch (error) {
            showError("Error al guardar los cambios de stock.");
        }
    };

    const cancelarCambiosStock = () => {
        setCambiosStockPendientes({});
    };

    const handleDelete = async (id) => {
        if(window.confirm("¿Seguro que quieres eliminar esta planta?")) {
            try {
                await PlantaService.deletePlanta(id);
                showSuccess("Planta eliminada.");
                await cargarDatos();
            } catch (error) {
                showError(getErrorMessage(error, "Error al eliminar planta."));
            }
        }
    };

    const handleGuardarPlanta = async (e) => {
        e.preventDefault();
        try {
            let imageUrl = newPlanta.image;
            if (selectedFile) {
                const uploadRes = await PlantaService.uploadImage(selectedFile);
                imageUrl = uploadRes.data;
            }

            const plantaAGuardar = { ...newPlanta, image: imageUrl };

            if (editandoPlantaId) {
                await PlantaService.updatePlanta(editandoPlantaId, plantaAGuardar);
                showSuccess("Planta actualizada correctamente.");
            } else {
                await PlantaService.createPlanta(plantaAGuardar);
                showSuccess("Planta creada correctamente.");
            }

            await cargarPlantas();
            await cargarDatos();
            cancelarEdicionPlanta();
        } catch (error) {
            showError(getErrorMessage(error, editandoPlantaId ? "Error al editar planta." : "Error al crear planta."));
        }
    };

    const iniciarEdicion = (planta) => {
        setEditandoPlantaId(planta.id);
        setNewPlanta({
            name: planta.name || "",
            price: planta.price || "",
            description: planta.description || "",
            image: planta.image || "",
            stock: planta.stock || 0,
            planting: planta.planting || "",
            size: planta.size || ""
        });
        setSelectedFile(null);
        setActiveTab("crear");
        setMensaje("");
        setError("");
    };

    const cancelarEdicionPlanta = () => {
        setEditandoPlantaId(null);
        setNewPlanta(initialPlanta);
        setSelectedFile(null);
        setActiveTab("inventario");
    };

    const handleGuardarUsuario = async (e) => {
        e.preventDefault();
        try {
            if (editandoUsuarioId) {
                await AuthService.updateUser(editandoUsuarioId, newUsuario);
                showSuccess("Usuario actualizado correctamente.");
            } else {
                await AuthService.createUser(newUsuario);
                showSuccess("Usuario creado correctamente.");
            }
            setNewUsuario(initialUsuario);
            setEditandoUsuarioId(null);
            await cargarUsuarios();
        } catch (error) {
            showError(getErrorMessage(error, "Error al guardar usuario."));
        }
    };

    const iniciarEdicionUsuario = (usuario) => {
        setEditandoUsuarioId(usuario.id);
        setNewUsuario({
            name: usuario.name || "",
            lastname: usuario.lastname || "",
            email: usuario.email || "",
            password: "",
            phone: usuario.phone || "",
            address: usuario.address || "",
            commune: usuario.commune || "",
            role: usuario.role || "CLIENTE"
        });
    };

    const cancelarEdicionUsuario = () => {
        setEditandoUsuarioId(null);
        setNewUsuario(initialUsuario);
    };

    const handleDeleteUsuario = async (id) => {
        if(window.confirm("¿Seguro que quieres eliminar este usuario?")) {
            try {
                await AuthService.deleteUser(id);
                showSuccess("Usuario eliminado correctamente.");
                await cargarUsuarios();
            } catch (error) {
                showError(getErrorMessage(error, "Error al eliminar usuario."));
            }
        }
    };

    const handleRoleChange = async (id, role) => {
        try {
            await AuthService.updateRole(id, role);
            showSuccess("Rol actualizado correctamente.");
            await cargarUsuarios();
        } catch (error) {
            showError(getErrorMessage(error, "Error al cambiar rol."));
        }
    };

    // --- FUNCIÓN DE CERRAR SESIÓN ---
    const handleCerrarSesion = () => {
        AuthService.logout();
        navigate("/login");
        window.location.reload();
    };

    return (
        <div className="admin-container">
            <div className="admin-sidebar">
                <div className="sidebar-top">
                    <h2>Panel Admin</h2>
                    <button onClick={() => setActiveTab('resumen')} className={activeTab === 'resumen' ? 'active' : ''}>Resumen</button>
                    <button onClick={() => setActiveTab('inventario')} className={activeTab === 'inventario' ? 'active' : ''}>Inventario & Stock</button>
                    <button onClick={() => setActiveTab('crear')} className={activeTab === 'crear' ? 'active' : ''}>Producto</button>
                    <button onClick={() => setActiveTab('usuarios')} className={activeTab === 'usuarios' ? 'active' : ''}>Usuarios</button>
                </div>

                {/* 👇 BOTÓN DE CERRAR SESIÓN UBICADO AL FINAL DEL SIDEBAR */}
                <div className="sidebar-bottom">
                    <button onClick={handleCerrarSesion} className="btn-logout-sidebar">
                        Cerrar Sesión
                    </button>
                </div>
            </div>

            <div className="admin-content">
                {mensaje && <div className="admin-message success">{mensaje}</div>}
                {error && <div className="admin-message error">{error}</div>}

                {activeTab === 'resumen' && (
                    <div>
                        <h1>Rendimiento de Ventas</h1>
                        <p>Comparativa: Cuánto has vendido vs. Cuánto te queda.</p>
                        <div className="chart-panel">
                            <ResponsiveContainer>
                                <BarChart data={datosGrafico}>
                                    <CartesianGrid strokeDasharray="3 3" />
                                    <XAxis dataKey="name" />
                                    <YAxis />
                                    <Tooltip />
                                    <Legend />
                                    <Bar dataKey="ventas" fill="#27ae60" name="Total Vendidos" />
                                    <Bar dataKey="stock" fill="#95a5a6" name="Stock Restante" />
                                </BarChart>
                            </ResponsiveContainer>
                        </div>
                    </div>
                )}

                {activeTab === 'inventario' && (
                    <div>
                        <h1>Gestión de Inventario</h1>
                        <p>Usa las flechas para ajustar el stock. Los cambios se guardarán cuando confirmes en la barra flotante.</p>

                        <table className="admin-table">
                            <thead>
                            <tr>
                                <th>ID</th>
                                <th>Nombre</th>
                                <th>Stock Actual</th>
                                <th>Ajustar Stock</th>
                                <th>Precio</th>
                                <th>Acciones</th>
                            </tr>
                            </thead>
                            <tbody>
                            {plantas.map(p => {
                                const pendiente = cambiosStockPendientes[p.id];
                                const stockVisual = p.stock + (pendiente ? pendiente.cambio : 0);

                                return (
                                    <tr key={p.id}>
                                        <td>{p.id}</td>
                                        <td>{p.name}</td>
                                        <td style={{fontWeight:'bold', color: stockVisual < 5 ? 'red' : 'green'}}>
                                            {stockVisual} {pendiente && <span style={{color: '#e67e22', fontSize: '11px'}}>(Modificado)</span>}
                                        </td>
                                        <td>
                                            <div style={{display: 'flex', gap: '5px'}}>
                                                <button 
                                                    className="btn-green" 
                                                    style={{padding: '2px 8px'}} 
                                                    title="Aumentar stock en 1"
                                                    onClick={() => handleModificarStockLocal(p.id, p.name, p.stock, 1)}
                                                >
                                                    ▲
                                                </button>
                                                <button 
                                                    className="btn-red" 
                                                    style={{padding: '2px 8px'}} 
                                                    title="Reducir stock en 1"
                                                    onClick={() => handleModificarStockLocal(p.id, p.name, p.stock, -1)}
                                                >
                                                    ▼
                                                </button>
                                            </div>
                                        </td>
                                        <td>${Number(p.price || 0).toLocaleString('es-CL')}</td>
                                        <td className="table-actions">
                                            <button className="btn-green" onClick={() => iniciarEdicion(p)}>Editar</button>
                                            <button className="btn-red" onClick={() => handleDelete(p.id)}>Eliminar</button>
                                        </td>
                                    </tr>
                                );
                            })}
                            </tbody>
                        </table>

                        {Object.keys(cambiosStockPendientes).length > 0 && (
                            <div className="stock-batch-bar">
                                <h4>📋 Resumen de cambios pendientes:</h4>
                                <ul className="stock-batch-list">
                                    {Object.entries(cambiosStockPendientes).map(([id, data]) => (
                                        <li key={id}>
                                            <b>{data.nombre}</b>: {data.cambio > 0 ? `+${data.cambio}` : data.cambio} unidades (Total: {data.stockActual + data.cambio})
                                        </li>
                                    ))}
                                </ul>
                                <div className="stock-batch-actions">
                                    <button className="btn-cancel-batch" onClick={cancelarCambiosStock}>Cancelar</button>
                                    <button className="btn-confirm-batch" onClick={confirmarCambiosStock}>Confirmar y Guardar Stock</button>
                                </div>
                            </div>
                        )}
                    </div>
                )}

                {activeTab === 'crear' && (
                    <div>
                        <h1>{editandoPlantaId ? "Editar Planta" : "Registrar Nueva Planta"}</h1>
                        <form className="form-create" onSubmit={handleGuardarPlanta}>
                            <input type="text" placeholder="Nombre" value={newPlanta.name} onChange={e => setNewPlanta({...newPlanta, name: e.target.value})} required />
                            <input type="number" placeholder="Precio" value={newPlanta.price} onChange={e => setNewPlanta({...newPlanta, price: e.target.value})} required />
                            <input type="number" placeholder="Stock" value={newPlanta.stock} onChange={e => setNewPlanta({...newPlanta, stock: e.target.value})} required />
                            <label>Imagen de la Planta:</label>
                            <input type="file" onChange={(e) => setSelectedFile(e.target.files[0])} accept="image/*" />
                            <textarea placeholder="Descripción" value={newPlanta.description} onChange={e => setNewPlanta({...newPlanta, description: e.target.value})} required />
                            <input type="text" placeholder="Tamaño (ej: 10cm)" value={newPlanta.size} onChange={e => setNewPlanta({...newPlanta, size: e.target.value})} />
                            <input type="text" placeholder="Cuidados (Planting)" value={newPlanta.planting} onChange={e => setNewPlanta({...newPlanta, planting: e.target.value})} />

                            <div className="form-actions">
                                <button type="submit" className="btn-green">{editandoPlantaId ? "Guardar Cambios" : "Guardar Planta"}</button>
                                <button type="button" className="btn-secondary" onClick={cancelarEdicionPlanta}>Cancelar</button>
                            </div>
                        </form>
                    </div>
                )}

                {activeTab === 'usuarios' && (
                    <div>
                        <h1>Gestión de Usuarios</h1>
                        <div className="card-admin">
                            <h3>{editandoUsuarioId ? "Editar Usuario" : "Crear Usuario"}</h3>
                            <form className="user-form" onSubmit={handleGuardarUsuario}>
                                <input type="text" placeholder="Nombre" value={newUsuario.name} onChange={e => setNewUsuario({...newUsuario, name: e.target.value})} required />
                                <input type="text" placeholder="Apellido" value={newUsuario.lastname} onChange={e => setNewUsuario({...newUsuario, lastname: e.target.value})} required />
                                <input type="email" placeholder="Email" value={newUsuario.email} onChange={e => setNewUsuario({...newUsuario, email: e.target.value})} required />
                                <input type="password" placeholder={editandoUsuarioId ? "Nueva Contraseña (opcional)" : "Contraseña"} value={newUsuario.password} onChange={e => setNewUsuario({...newUsuario, password: e.target.value})} required={!editandoUsuarioId} />
                                <input type="text" placeholder="Teléfono" value={newUsuario.phone} onChange={e => setNewUsuario({...newUsuario, phone: e.target.value})} required />
                                <input type="text" placeholder="Dirección" value={newUsuario.address} onChange={e => setNewUsuario({...newUsuario, address: e.target.value})} required />
                                <input type="text" placeholder="Comuna" value={newUsuario.commune} onChange={e => setNewUsuario({...newUsuario, commune: e.target.value})} required />
                                <select value={newUsuario.role} onChange={e => setNewUsuario({...newUsuario, role: e.target.value})}>
                                    <option value="CLIENTE">CLIENTE</option>
                                    <option value="ADMIN">ADMIN</option>
                                </select>
                                <div className="form-actions">
                                    <button type="submit" className="btn-green">{editandoUsuarioId ? "Actualizar Usuario" : "Crear Usuario"}</button>
                                    {editandoUsuarioId && <button type="button" className="btn-secondary" onClick={cancelarEdicionUsuario}>Cancelar</button>}
                                </div>
                            </form>
                        </div>

                        <table className="admin-table">
                            <thead>
                            <tr>
                                <th>ID</th>
                                <th>Nombre</th>
                                <th>Email</th>
                                <th>Rol</th>
                                <th>Teléfono</th>
                                <th>Acciones</th>
                            </tr>
                            </thead>
                            <tbody>
                            {usuarios.map(usuario => (
                                <tr key={usuario.id}>
                                    <td>{usuario.id}</td>
                                    <td>{usuario.name} {usuario.lastname}</td>
                                    <td>{usuario.email}</td>
                                    <td>
                                        <select value={usuario.role} onChange={e => handleRoleChange(usuario.id, e.target.value)}>
                                            <option value="CLIENTE">CLIENTE</option>
                                            <option value="ADMIN">ADMIN</option>
                                        </select>
                                    </td>
                                    <td>{usuario.phone}</td>
                                    <td className="table-actions">
                                        <button className="btn-green" onClick={() => iniciarEdicionUsuario(usuario)}>Editar</button>
                                        <button className="btn-red" onClick={() => handleDeleteUsuario(usuario.id)}>Eliminar</button>
                                    </td>
                                </tr>
                            ))}
                            </tbody>
                        </table>
                    </div>
                )}
            </div>
        </div>
    );
};

export default AdminDashboard;