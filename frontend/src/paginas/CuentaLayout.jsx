import { useEffect, useState } from 'react';
import { NavLink, Outlet, useLocation } from 'react-router-dom';
import { api } from '../api/cliente';
import { useAuth } from '../contexto/AuthContext';

export default function CuentaLayout() {
  const { usuario } = useAuth();
  const { pathname } = useLocation();
  const [pendientes, setPendientes] = useState(0);

  useEffect(() => {
    api
      .get('/reservas/mias', { estado: 'PENDIENTE_PAGO' })
      .then((r) => setPendientes(r.datos.filter((x) => x.expiraPagoEn && new Date(x.expiraPagoEn) > new Date()).length))
      .catch(() => setPendientes(0));
  }, [pathname]);

  return (
    <div className="contenedor admin">
      <aside className="admin-menu" aria-label="Menú de mi cuenta">
        <h2>Mi cuenta</h2>
        <nav>
          <NavLink to="/mi-cuenta" end>Mi perfil</NavLink>
          <NavLink to="/mi-cuenta/reservas">
            Mis reservas
            {pendientes > 0 && <span className="contador-pendiente" title="Reservas pendientes de pago">{pendientes}</span>}
          </NavLink>
          {usuario.tieneContrasena && <NavLink to="/mi-cuenta/seguridad">Seguridad</NavLink>}
        </nav>
      </aside>
      <section className="admin-contenido">
        <Outlet />
      </section>
    </div>
  );
}