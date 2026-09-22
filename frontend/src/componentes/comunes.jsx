import { Link, Navigate, useLocation } from 'react-router-dom';
import { useAuth } from '../contexto/AuthContext';
import { urlArchivo } from '../api/cliente';
import { formatearMoneda } from '../utils/formato';

export function Alerta({ tipo = 'error', children, alCerrar }) {
  if (!children) return null;
  return (
    <div className={`alerta alerta-${tipo}`} role={tipo === 'error' ? 'alert' : 'status'}>
      <span>{children}</span>
      {alCerrar && (
        <button type="button" className="alerta-cerrar" onClick={alCerrar} aria-label="Cerrar">
          ×
        </button>
      )}
    </div>
  );
}

export function Cargando({ texto = 'Cargando…' }) {
  return (
    <div className="cargando" role="status">
      <span className="spinner" aria-hidden="true" />
      {texto}
    </div>
  );
}

/** Protege rutas: exige sesión y, opcionalmente, un rol (EN-05) */
export function RutaProtegida({ roles, children }) {
  const { usuario, cargando } = useAuth();
  const ubicacion = useLocation();

  if (cargando) return <Cargando />;
  if (!usuario) {
    return <Navigate to="/login" replace state={{ desde: ubicacion.pathname + ubicacion.search }} />;
  }
  if (roles && !roles.includes(usuario.rol)) {
    return (
      <div className="contenedor">
        <Alerta>No tienes permiso para ver esta página.</Alerta>
        <Link to="/">Volver al catálogo</Link>
      </div>
    );
  }
  return children;
}

export function Paginacion({ paginacion, alCambiar }) {
  if (!paginacion || paginacion.totalPaginas <= 1) return null;
  const { pagina, totalPaginas } = paginacion;
  return (
    <nav className="paginacion" aria-label="Paginación">
      <button type="button" className="boton boton-secundario" disabled={pagina <= 1} onClick={() => alCambiar(pagina - 1)}>
        ← Anterior
      </button>
      <span>
        Página {pagina} de {totalPaginas}
      </span>
      <button type="button" className="boton boton-secundario" disabled={pagina >= totalPaginas} onClick={() => alCambiar(pagina + 1)}>
        Siguiente →
      </button>
    </nav>
  );
}

export function FotoMaquina({ ruta, alt, className = '' }) {
  if (!ruta) {
    return (
      <div className={`foto-vacia ${className}`} aria-label="Sin foto">
        <svg viewBox="0 0 64 40" width="64" aria-hidden="true">
          <path d="M6 30h30l-4-12H20l-2-8H10zM36 18l8-12 4 2-6 14" fill="none" stroke="currentColor" strokeWidth="3" strokeLinejoin="round" />
          <circle cx="14" cy="34" r="4" fill="currentColor" />
          <circle cx="28" cy="34" r="4" fill="currentColor" />
        </svg>
        <span>Sin foto</span>
      </div>
    );
  }
  return <img src={urlArchivo(ruta)} alt={alt} className={className} loading="lazy" />;
}

/** HU-03 criterio 2: foto, nombre, categoría y tarifa diaria */
export function TarjetaMaquina({ maquina }) {
  return (
    <Link to={`/maquinas/${maquina.id}`} className="tarjeta-maquina">
      <div className="tarjeta-foto">
        <FotoMaquina ruta={maquina.fotoPrincipal} alt={maquina.nombre} />
        {maquina.enMantenimiento && <span className="insignia insignia-aviso flotante">En mantenimiento</span>}
      </div>
      <div className="tarjeta-cuerpo">
        <span className="insignia">{maquina.categoria.nombre}</span>
        <h3>{maquina.nombre}</h3>
        <p className="texto-suave">
          {maquina.marca} · {maquina.modelo} · {maquina.ubicacion}
        </p>
        <p className="tarifa">
          {formatearMoneda(maquina.tarifaDiaria)} <span>/ día</span>
        </p>
      </div>
    </Link>
  );
}

export function Campo({ etiqueta, error, ayuda, children, id }) {
  return (
    <div className={`campo ${error ? 'campo-error' : ''}`}>
      <label htmlFor={id}>{etiqueta}</label>
      {children}
      {ayuda && !error && <small className="ayuda">{ayuda}</small>}
      {error && <small className="mensaje-error">{error}</small>}
    </div>
  );
}
