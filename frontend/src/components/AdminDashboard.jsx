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
    const [stockId, setStockId] = useState("");
    const [stockCant, setStockCant] = useState("");
    const [newPlanta, setNewPlanta] = useState(initialPlanta);
    const [editandoPlantaId, setEditandoPlantaId] = useState(null);
    const [newUsuario, setNewUsuario] = useState(initialUsuario);
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
        const data = error?.response?.data;
        if (typeof data === "string") return data;
        return data?.message || data?.error || fallback;
    };

    const isUnauthorized = (error) => {
        return error?.response?.status === 401 || error?.response?.status === 403;
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

    const handleAddStock = async (e) => {
        e.preventDefault();
        try {
            await PlantaService.addStock(stockId, stockCant);
            showSuccess("Stock actualizado correctamente.");
            await cargarDatos();
            setStockId("");
            setStockCant("");
        } catch (error) {
            if (isUnauthorized(error)) {
                const mensajeNoAutorizado = "No estás autorizado. Solo un ADMIN puede ajustar stock.";
                alert(mensajeNoAutorizado);
                showError(mensajeNoAutorizado);
                return;
            }

            showError(getErrorMessage(error, "Error al actualizar stock."));
        }
    };

    const handleDelete = async (id) => {
        if(window.confirm("¿Seguro que quieres eliminar esta planta?")) {
            try {
                await PlantaService.deletePlanta(id);
                showSuccess("Planta eliminada.");
                await cargarDatos();
            } catch (error) {
                if (isUnauthorized(error)) {
                    const mensajeNoAutorizado = "No estás autorizado. Solo un ADMIN puede eliminar productos.";
                    alert(mensajeNoAutorizado);
                    showError(mensajeNoAutorizado);
                    return;
                }

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
            cancelarEdicion();
        } catch (error) {
            if (isUnauthorized(error)) {
                const mensajeNoAutorizado = "No estás autorizado. Solo un ADMIN puede agregar o editar productos.";
                alert(mensajeNoAutorizado);
                showError(mensajeNoAutorizado);
                return;
            }

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

    const cancelarEdicion = () => {
        setEditandoPlantaId(null);
        setNewPlanta(initialPlanta);
        setSelectedFile(null);
    };

    const handleCreateUsuario = async (e) => {
        e.preventDefault();
        try {
            await AuthService.createUser(newUsuario);
            showSuccess("Usuario creado correctamente.");
            setNewUsuario(initialUsuario);
            await cargarUsuarios();
        } catch (error) {
            if (isUnauthorized(error)) {
                const mensajeNoAutorizado = "No estás autorizado. Solo un ADMIN puede crear usuarios.";
                alert(mensajeNoAutorizado);
                showError(mensajeNoAutorizado);
                return;
            }

            showError(getErrorMessage(error, "Error al crear usuario."));
        }
    };

    const handleRoleChange = async (id, role) => {
        try {
            await AuthService.updateRole(id, role);
            showSuccess("Rol actualizado correctamente.");
            await cargarUsuarios();
        } catch (error) {
            if (isUnauthorized(error)) {
                const mensajeNoAutorizado = "No estás autorizado. Solo un ADMIN puede cambiar roles.";
                alert(mensajeNoAutorizado);
                showError(mensajeNoAutorizado);
                return;
            }

            showError(getErrorMessage(error, "Error al cambiar rol."));
        }
    };

    return (
        <div className="admin-container">
            <div className="admin-sidebar">
                <h2>Panel Admin</h2>
                <button onClick={() => setActiveTab('resumen')} className={activeTab === 'resumen' ? 'active' : ''}>Resumen</button>
                <button onClick={() => setActiveTab('inventario')} className={activeTab === 'inventario' ? 'active' : ''}>Inventario & Stock</button>
                <button onClick={() => setActiveTab('crear')} className={activeTab === 'crear' ? 'active' : ''}>Producto</button>
                <button onClick={() => setActiveTab('usuarios')} className={activeTab === 'usuarios' ? 'active' : ''}>Usuarios</button>
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

                        <div className="card-admin">
                            <h3>Ajustar Stock Rápido</h3>
                            <form onSubmit={handleAddStock} className="inline-admin-form">
                                <input type="number" placeholder="ID Planta" value={stockId} onChange={e=>setStockId(e.target.value)} required />
                                <input type="number" placeholder="Ajuste (+/-)" value={stockCant} onChange={e=>setStockCant(e.target.value)} required />
                                <button type="submit" className="btn-green">Actualizar</button>
                            </form>
                        </div>

                        <table className="admin-table">
                            <thead>
                            <tr>
                                <th>ID</th>
                                <th>Nombre</th>
                                <th>Stock</th>
                                <th>Precio</th>
                                <th>Acciones</th>
                            </tr>
                            </thead>
                            <tbody>
                            {plantas.map(p => (
                                <tr key={p.id}>
                                    <td>{p.id}</td>
                                    <td>{p.name}</td>
                                    <td style={{fontWeight:'bold', color: p.stock < 5 ? 'red' : 'green'}}>{p.stock}</td>
                                    <td>${Number(p.price || 0).toLocaleString('es-CL')}</td>
                                    <td className="table-actions">
                                        <button className="btn-green" onClick={() => iniciarEdicion(p)}>Editar</button>
                                        <button className="btn-red" onClick={() => handleDelete(p.id)}>Eliminar</button>
                                    </td>
                                </tr>
                            ))}
                            </tbody>
                        </table>
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
                                {editandoPlantaId && <button type="button" className="btn-secondary" onClick={cancelarEdicion}>Cancelar</button>}
                            </div>
                        </form>
                    </div>
                )}

                {activeTab === 'usuarios' && (
                    <div>
                        <h1>Gestión de Usuarios</h1>

                        <div className="card-admin">
                            <h3>Crear Usuario</h3>
                            <form className="user-form" onSubmit={handleCreateUsuario}>
                                <input type="text" placeholder="Nombre" value={newUsuario.name} onChange={e => setNewUsuario({...newUsuario, name: e.target.value})} required />
                                <input type="text" placeholder="Apellido" value={newUsuario.lastname} onChange={e => setNewUsuario({...newUsuario, lastname: e.target.value})} required />
                                <input type="email" placeholder="Email" value={newUsuario.email} onChange={e => setNewUsuario({...newUsuario, email: e.target.value})} required />
                                <input type="password" placeholder="Contraseña" value={newUsuario.password} onChange={e => setNewUsuario({...newUsuario, password: e.target.value})} required />
                                <input type="text" placeholder="Teléfono" value={newUsuario.phone} onChange={e => setNewUsuario({...newUsuario, phone: e.target.value})} required />
                                <input type="text" placeholder="Dirección" value={newUsuario.address} onChange={e => setNewUsuario({...newUsuario, address: e.target.value})} required />
                                <input type="text" placeholder="Comuna" value={newUsuario.commune} onChange={e => setNewUsuario({...newUsuario, commune: e.target.value})} required />
                                <select value={newUsuario.role} onChange={e => setNewUsuario({...newUsuario, role: e.target.value})}>
                                    <option value="CLIENTE">CLIENTE</option>
                                    <option value="ADMIN">ADMIN</option>
                                </select>
                                <button type="submit" className="btn-green">Crear Usuario</button>
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
