import React, { useState } from 'react';
import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom';
import Header from './components/Header';
import Footer from './components/Footer';
import Catalogo from './components/Catalogo';
import Login from './components/Login';
import Registro from './components/Registro';
import Carrito from './components/Carrito';
import Favoritos from './components/Favoritos';
import ProductoDetalle from "./components/ProductoDetalle";
import AdminDashboard from './components/AdminDashboard';
import AuthService from './services/AuthService';
import Perfil from "./components/Perfil";
import './App.css';
import { CarritoProvider } from './context/CarritoContext';
import { NotificacionProvider } from './context/NotificacionContext';

const AdminRoute = ({ children }) => {
    return AuthService.isAdmin() ? children : <Navigate to="/login" replace />;
};

function App() {
    const [search, setSearch] = useState("");
  return (
     <CarritoProvider>
      <NotificacionProvider>
      <BrowserRouter>
          <Header search={search} setSearch={setSearch} />

        <main className="main-content">
          <Routes>
              <Route path="/" element={<Catalogo search={search} />} />
              <Route path="/catalogo" element={<Catalogo search={search} />} />
              <Route path="/home" element={<Catalogo search={search} />} />
              <Route path="/login" element={<Login />} />
              <Route path="/registro" element={<Registro />} />
              <Route path="/carrito" element={<Carrito />} />
              <Route path="/favoritos" element={<Favoritos />} />
              <Route path="/producto/:id" element={<ProductoDetalle />} />
              <Route path="/perfil" element={<Perfil />} />
              <Route path="/admin" element={<AdminRoute><AdminDashboard /></AdminRoute>} />
          </Routes>
        </main>
        <Footer />
      </BrowserRouter>
      </NotificacionProvider>
    </CarritoProvider>
  );
}

export default App;
