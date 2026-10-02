import { Navigate, Route, Routes } from 'react-router-dom';
import Encabezado from './componentes/Encabezado';
import PiePagina from './componentes/PiePagina';
import { RutaProtegida } from './componentes/comunes';
import Inicio from './paginas/Inicio';
import Catalogo from './paginas/Catalogo';
import DetalleMaquina from './paginas/DetalleMaquina';
import Registro from './paginas/Registro';
import Login from './paginas/Login';
import MiCuenta from './paginas/MiCuenta';
import NoEncontrado from './paginas/NoEncontrado';
import AdminLayout from './paginas/admin/AdminLayout';
import AdminCategorias from './paginas/admin/AdminCategorias';
import AdminMaquinas from './paginas/admin/AdminMaquinas';
import AdminMaquinaForm from './paginas/admin/AdminMaquinaForm';
import AdminDisponibilidad from './paginas/admin/AdminDisponibilidad';

export default function App() {
  return (
    <>
      <a href="#contenido" className="saltar-contenido">Saltar al contenido</a>
      <Encabezado />
      <main id="contenido" tabIndex={-1}>
        <Routes>
          {/* Públicas */}
          <Route path="/" element={<Inicio />} />
          <Route path="/catalogo" element={<Catalogo />} />
          <Route path="/maquinas/:id" element={<DetalleMaquina />} />
          <Route path="/registro" element={<Registro />} />
          <Route path="/login" element={<Login />} />

          {/* Cliente */}
          <Route
            path="/mi-cuenta"
            element={
              <RutaProtegida roles={['CLIENTE']}>
                <MiCuenta />
              </RutaProtegida>
            }
          />

          {/* Administrador (EN-05) */}
          <Route
            path="/admin"
            element={
              <RutaProtegida roles={['ADMINISTRADOR']}>
                <AdminLayout />
              </RutaProtegida>
            }
          >
            <Route index element={<Navigate to="maquinas" replace />} />
            <Route path="maquinas" element={<AdminMaquinas />} />
            <Route path="maquinas/nueva" element={<AdminMaquinaForm />} />
            <Route path="maquinas/:id" element={<AdminMaquinaForm />} />
            <Route path="maquinas/:id/disponibilidad" element={<AdminDisponibilidad />} />
            <Route path="categorias" element={<AdminCategorias />} />
          </Route>

          <Route path="*" element={<NoEncontrado />} />
        </Routes>
      </main>
      <PiePagina />
    </>
  );
}
