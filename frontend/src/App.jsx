import { Link, Navigate, Route, Routes } from 'react-router-dom';
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
import { LogoMarca } from './componentes/Ilustracion';

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
          <div className="pie-rejilla">
            <div>
              <Link to="/" className="marca" style={{ marginBottom: 12 }}>
                <LogoMarca />
                {NOMBRE_APP}
              </Link>
              <p style={{ maxWidth: 320, margin: '12px 0 0' }}>
                Alquiler de maquinaria pesada por días. Compara, revisa la disponibilidad y reserva en línea.
              </p>
            </div>
            <div>
              <h4>Explorar</h4>
              <ul>
                <li><Link to="/">Catálogo</Link></li>
                <li><a href="/#equipos">Equipos disponibles</a></li>
              </ul>
            </div>
            <div>
              <h4>Cuenta</h4>
              <ul>
                <li><Link to="/login">Iniciar sesión</Link></li>
                <li><Link to="/registro">Crear cuenta</Link></li>
              </ul>
            </div>
          </div>
          <div className="pie-legal">
            <span>© {new Date().getFullYear()} {NOMBRE_APP}. Todos los derechos reservados.</span>
            <span>Proyecto académico · Agile Development · UPAO</span>
          </div>
        </div>
      </footer>
    </>
  );
}
