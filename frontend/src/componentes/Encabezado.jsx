import { useEffect, useState } from 'react';
import { Link, NavLink, useLocation, useNavigate } from 'react-router-dom';
import { useAuth, panelSegunRol } from '../contexto/AuthContext';
import { NOMBRE_APP } from '../utils/formato';
import { useTema } from '../utils/tema';
import { Icono, LogoMarca } from './Ilustracion';

function BotonTema() {
  const { tema, alternar } = useTema();
  const oscuro = tema === 'dark';
  return (
    <button
      type="button"
      className="boton-icono"
      onClick={alternar}
      aria-label={oscuro ? 'Cambiar a modo claro' : 'Cambiar a modo oscuro'}
      title={oscuro ? 'Modo claro' : 'Modo oscuro'}
    >
      <Icono nombre={oscuro ? 'sol' : 'luna'} tamanio={20} />
    </button>
  );
}

export default function Encabezado() {
  const { usuario, esAdmin, cerrarSesion } = useAuth();
  const navegar = useNavigate();
  const { pathname } = useLocation();
  const [menuAbierto, setMenuAbierto] = useState(false);

  // El menú móvil se cierra al navegar
  useEffect(() => setMenuAbierto(false), [pathname]);

  async function salir() {
    await cerrarSesion();
    navegar('/login', { state: { mensaje: 'Cerraste sesión correctamente' } });
  }

  return (
    <header className="encabezado">
      <div className="contenedor encabezado-interior">
        <Link to="/" className="marca" aria-label={`${NOMBRE_APP}, ir al inicio`}>
          <LogoMarca />
          <span className="marca-texto">{NOMBRE_APP}</span>
        </Link>
        <nav id="nav-principal" className={`nav-principal ${menuAbierto ? 'abierto' : ''}`} aria-label="Principal">
          <NavLink to="/" end>
            Inicio
          </NavLink>
          <NavLink to="/catalogo">Catálogo</NavLink>
          {esAdmin && <NavLink to="/admin">Administración</NavLink>}
          {usuario && !esAdmin && <NavLink to="/mi-cuenta">Mi cuenta</NavLink>}
          {!usuario && (
            <Link to="/registro" className="solo-movil">
              Crear cuenta
            </Link>
          )}
        </nav>
        <div className="nav-usuario">
          <BotonTema />
          {usuario ? (
            <>
              <Link to={panelSegunRol(usuario)} className="nombre-usuario" title={usuario.correo}>
                <span className="avatar" aria-hidden="true">{usuario.nombre.trim().charAt(0).toUpperCase()}</span>
                <span className="nombre-texto">{usuario.nombre.split(' ')[0]}</span>
                {esAdmin && <span className="insignia insignia-info">Admin</span>}
              </Link>
              <button type="button" className="boton boton-secundario boton-chico" onClick={salir}>
                <span className="solo-escritorio">Cerrar sesión</span>
                <span className="solo-movil">Salir</span>
              </button>
            </>
          ) : (
            <>
              <Link to="/login" className="boton boton-secundario boton-chico">
                Iniciar sesión
              </Link>
              <Link to="/registro" className="boton boton-primario boton-chico solo-escritorio">
                Registrarme
              </Link>
            </>
          )}
          <button
            type="button"
            className="boton-icono boton-menu"
            onClick={() => setMenuAbierto((a) => !a)}
            aria-expanded={menuAbierto}
            aria-controls="nav-principal"
            aria-label={menuAbierto ? 'Cerrar menú' : 'Abrir menú'}
          >
            <Icono nombre={menuAbierto ? 'cerrar' : 'menu'} tamanio={20} />
          </button>
        </div>
      </div>
    </header>
  );
}
