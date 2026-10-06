import { useEffect } from 'react';
import { Link, Navigate, Route, Routes, useLocation } from 'react-router-dom';
import Encabezado from './componentes/Encabezado';
import { RutaProtegida, Cargando } from './componentes/comunes';
import { useAuth } from './contexto/AuthContext';
import Inicio from './paginas/Inicio';
import Catalogo from './paginas/Catalogo';
import DetalleMaquina from './paginas/DetalleMaquina';
import Registro from './paginas/Registro';
import Login from './paginas/Login';
import CompletarRegistro from './paginas/CompletarRegistro';
import MiCuenta from './paginas/MiCuenta';
import NoEncontrado from './paginas/NoEncontrado';
import AdminLayout from './paginas/admin/AdminLayout';
import AdminCategorias from './paginas/admin/AdminCategorias';
import AdminMaquinas from './paginas/admin/AdminMaquinas';
import AdminMaquinaForm from './paginas/admin/AdminMaquinaForm';
import AdminDisponibilidad from './paginas/admin/AdminDisponibilidad';
import { NOMBRE_APP, CONTACTO } from './utils/formato';
import { LogoMarca } from './componentes/Ilustracion';

function GuardGlobal() {
  const { usuario, cargando } = useAuth();
  const ubicacion = useLocation();

  // Esperar a que AuthContext cargue
  if (cargando) {
    return <Cargando />;
  }

  // Excepciones: completar-registro, privacidad (si existe), logout no aplica aquí
  const esExcepcion = ubicacion.pathname === '/completar-registro' || ubicacion.pathname === '/privacidad';

  // Guard: si hay sesión, sin celular y no es admin, redirige a completar-registro
  if (usuario && !usuario.tieneCelular && usuario.rol !== 'ADMINISTRADOR' && !esExcepcion) {
    return <Navigate to="/completar-registro" replace />;
  }

  return null; // No hay redirección, continúa normalmente
}

export default function App() {
  return (
    <>
      <a href="#contenido" className="saltar-contenido">Saltar al contenido</a>
      <Encabezado />
      <GuardGlobal />
      <main id="contenido" tabIndex={-1}>
        <Routes>
          {/* Públicas */}
          <Route path="/" element={<Inicio />} />
          <Route path="/catalogo" element={<Catalogo />} />
          <Route path="/maquinas/:id" element={<DetalleMaquina />} />
          <Route path="/registro" element={<Registro />} />
          <Route path="/login" element={<Login />} />
          <Route path="/completar-registro" element={<CompletarRegistro />} />

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
                Alquiler de maquinaria pesada por horas. Compara, revisa la disponibilidad y reserva en línea.
              </p>
            </div>
            <div>
              <h4>Explorar</h4>
              <ul>
                <li><Link to="/">Inicio</Link></li>
                <li><Link to="/catalogo">Catálogo</Link></li>
                <li><a href="/#contacto">Contacto</a></li>
              </ul>
            </div>
            <div>
              <h4>Cuenta</h4>
              <ul>
                <li><Link to="/login">Iniciar sesión</Link></li>
                <li><Link to="/registro">Crear cuenta</Link></li>
              </ul>
            </div>
            {/* HU-01 criterio 5: datos de contacto de la empresa */}
            <div>
              <h4>Contacto</h4>
              <ul>
                <li><a href={`mailto:${CONTACTO.correo}`}>{CONTACTO.correo}</a></li>
                <li><a href={`tel:${CONTACTO.telefono.replace(/\s+/g, '')}`}>{CONTACTO.telefono}</a></li>
                <li>{CONTACTO.direccion}</li>
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
