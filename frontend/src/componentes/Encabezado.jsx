import { Link, NavLink, useNavigate } from 'react-router-dom';
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
      className="boton-tema"
      onClick={alternar}
      aria-label={oscuro ? 'Cambiar a modo claro' : 'Cambiar a modo oscuro'}
      title={oscuro ? 'Modo claro' : 'Modo oscuro'}
    >
      <Icono nombre={oscuro ? 'sol' : 'luna'} tamanio={18} />
    </button>
  );
}

export default function Encabezado() {
  const { usuario, esAdmin, cerrarSesion } = useAuth();
  const navegar = useNavigate();
  const esPaginaCompletarRegistro = window.location.pathname === '/completar-registro';

  async function salir() {
    await cerrarSesion();
    navegar('/login', { state: { mensaje: 'Cerraste sesión correctamente' } });
  }

  return (
    <header className="encabezado">
      <div className="contenedor encabezado-interior">
        <Link to={esPaginaCompletarRegistro ? '#' : '/'} className="marca" aria-label={esPaginaCompletarRegistro ? NOMBRE_APP : `${NOMBRE_APP}, ir al inicio`}>
          <LogoMarca />
          <span className="marca-texto">{NOMBRE_APP}</span>
        </Link>
        {!esPaginaCompletarRegistro && (
          <nav className="nav-principal" aria-label="Principal">
            <NavLink to="/" end>
              Inicio
            </NavLink>
            <NavLink to="/catalogo">Catálogo</NavLink>
            {esAdmin && <NavLink to="/admin">Administración</NavLink>}
            {usuario && !esAdmin && <NavLink to="/mi-cuenta">Mi cuenta</NavLink>}
          </nav>
        )}
        <div className="nav-usuario">
          <BotonTema />
          {usuario ? (
            <>
              {!esPaginaCompletarRegistro && (
                <Link to={panelSegunRol(usuario)} className="nombre-usuario" title={usuario.correo}>
                  <span className="avatar" aria-hidden="true">{usuario.nombre.trim().charAt(0).toUpperCase()}</span>
                  <span className="nombre-texto">{usuario.nombre.split(' ')[0]}</span>
                  {esAdmin && <span className="insignia insignia-admin">Admin</span>}
                </Link>
              )}
              <button type="button" className="boton boton-secundario boton-chico" onClick={salir}>
                Cerrar sesión
              </button>
            </>
          ) : (
            <>
              <Link to="/login" className="boton boton-secundario boton-chico">
                Iniciar sesión
              </Link>
              <Link to="/registro" className="boton boton-primario boton-chico">
                Registrarme
              </Link>
            </>
          )}
        </div>
      </div>
    </header>
  );
}
