import { Link, NavLink, useNavigate } from 'react-router-dom';
import { useAuth, panelSegunRol } from '../contexto/AuthContext';
import { NOMBRE_APP } from '../utils/formato';

export default function Encabezado() {
  const { usuario, esAdmin, cerrarSesion } = useAuth();
  const navegar = useNavigate();

  async function salir() {
    await cerrarSesion();
    navegar('/login', { state: { mensaje: 'Cerraste sesión correctamente' } });
  }

  return (
    <header className="encabezado">
      <div className="contenedor encabezado-interior">
        <Link to="/" className="marca">
          <img src="/favicon.svg" alt="" width="28" height="28" />
          {NOMBRE_APP}
        </Link>
        <nav className="nav-principal">
          <NavLink to="/" end>
            Catálogo
          </NavLink>
          {esAdmin && <NavLink to="/admin">Administración</NavLink>}
          {usuario && !esAdmin && <NavLink to="/mi-cuenta">Mi cuenta</NavLink>}
        </nav>
        <div className="nav-usuario">
          {usuario ? (
            <>
              <Link to={panelSegunRol(usuario)} className="nombre-usuario" title={usuario.correo}>
                {usuario.nombre}
                {esAdmin && <span className="insignia insignia-admin">Admin</span>}
              </Link>
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
