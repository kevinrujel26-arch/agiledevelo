import { Navigate, Route, Routes } from 'react-router-dom';
import Encabezado from './componentes/Encabezado';
import { RutaProtegida } from './componentes/comunes';
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
import { NOMBRE_APP } from './utils/formato';

export default function App() {
  return (
    <>
      <Encabezado />
      <main>
        <Routes>
          {/* Públicas */}
          <Route path="/" element={<Catalogo />} />
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
      <footer className="pie">
        <div className="contenedor">
          © {new Date().getFullYear()} {NOMBRE_APP} · Alquiler de maquinaria pesada
        </div>
      </footer>
    </>
  );
}
