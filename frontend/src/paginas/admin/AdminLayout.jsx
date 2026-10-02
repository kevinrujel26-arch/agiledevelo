import { NavLink, Outlet } from 'react-router-dom';

export default function AdminLayout() {
  return (
    <div className="contenedor admin">
      <aside className="admin-menu">
        <h2>Administración</h2>
        <nav>
          <NavLink to="/admin/maquinas">Máquinas</NavLink>
          <NavLink to="/admin/categorias">Categorías</NavLink>
          <span className="deshabilitado" title="Sprint 4">Reservas y pagos</span>
          <span className="deshabilitado" title="Sprint 4">Clientes</span>
        </nav>
      </aside>
      <section className="admin-contenido">
        <Outlet />
      </section>
    </div>
  );
}
