import { cloneElement, isValidElement } from 'react';
import { Link, Navigate, useLocation } from 'react-router-dom';
import { useAuth } from '../contexto/AuthContext';
import { urlArchivo } from '../api/cliente';
import { formatearMoneda } from '../utils/formato';
import { Icono } from './Ilustracion';

export function Alerta({ tipo = 'error', children, alCerrar }) {
  if (!children) return null;
  return (
    <div className={`alerta alerta-${tipo}`} role={tipo === 'error' ? 'alert' : 'status'}>
      <Icono nombre={tipo === 'exito' ? 'check' : 'info'} />
      <span className="alerta-texto">{children}</span>
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

/** Tarjetas de catálogo "fantasma" mientras llegan los datos */
export function EsqueletoTarjetas({ cantidad = 3, texto = 'Cargando equipos…' }) {
  return (
    <div className="rejilla-catalogo" role="status" aria-label={texto}>
      {Array.from({ length: cantidad }, (_, i) => (
        <div className="tarjeta-maquina esqueleto" key={i} aria-hidden="true">
          <div className="tarjeta-foto esqueleto-bloque" />
          <div className="tarjeta-cuerpo">
            <span className="esqueleto-linea ancho-70" />
            <span className="esqueleto-linea ancho-50" />
            <span className="esqueleto-linea ancho-40" />
            <div className="tarjeta-pie">
              <span className="esqueleto-linea ancho-30 alta" />
            </div>
          </div>
        </div>
      ))}
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
        <Link to="/catalogo">Volver al catálogo</Link>
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
        <span aria-hidden="true">←</span> Anterior
      </button>
      <span aria-current="page">
        Página {pagina} de {totalPaginas}
      </span>
      <button type="button" className="boton boton-secundario" disabled={pagina >= totalPaginas} onClick={() => alCambiar(pagina + 1)}>
        Siguiente <span aria-hidden="true">→</span>
      </button>
    </nav>
  );
}

export function FotoMaquina({ ruta, alt, className = '' }) {
  if (!ruta) {
    return (
      <div className={`foto-vacia ${className}`} role="img" aria-label="Sin foto">
        <svg viewBox="0 0 64 40" width="72" aria-hidden="true">
          <path d="M6 30h30l-4-12H20l-2-8H10zM36 18l8-12 4 2-6 14" fill="none" stroke="currentColor" strokeWidth="3" strokeLinejoin="round" />
          <circle cx="14" cy="34" r="4" fill="currentColor" />
          <circle cx="28" cy="34" r="4" fill="currentColor" />
        </svg>
        <span>Foto próximamente</span>
      </div>
    );
  }
  return <img src={urlArchivo(ruta)} alt={alt} className={className} loading="lazy" />;
}

/** HU-03 criterio 2: foto, nombre, categoría y tarifa por hora */
export function TarjetaMaquina({ maquina }) {
  return (
    <Link to={`/maquinas/${maquina.id}`} className="tarjeta-maquina">
      <div className="tarjeta-foto">
        <FotoMaquina ruta={maquina.fotoPrincipal} alt={maquina.nombre} />
        <span className="insignia insignia-sobre-foto tarjeta-categoria">{maquina.categoria.nombre}</span>
        {maquina.enMantenimiento && <span className="insignia insignia-aviso flotante">En mantenimiento</span>}
      </div>
      <div className="tarjeta-cuerpo">
        <h3>{maquina.nombre}</h3>
        <p className="texto-suave">
          {maquina.marca} · {maquina.modelo}
        </p>
        <p className="texto-suave con-icono">
          <Icono nombre="ubicacion" tamanio={15} /> {maquina.ubicacion}
        </p>
        <div className="tarjeta-pie">
          <p className="tarifa">
            {formatearMoneda(maquina.tarifaHoraria)} <span>/ hora</span>
          </p>
          <span className="ver-mas" aria-hidden="true"><Icono nombre="flecha" /></span>
        </div>
      </div>
    </Link>
  );
}

/**
 * Campo de formulario con etiqueta, ayuda y mensaje de error.
 * Con `valido` muestra una marca verde. Si el hijo es el control con el mismo `id`,
 * recibe aria-invalid y aria-describedby apuntando a la ayuda y al mensaje.
 * `mantenerAyuda` deja la ayuda visible aunque haya error (p. ej. requisitos de contraseña).
 */
export function Campo({ etiqueta, error, ayuda, valido, mantenerAyuda, children, id }) {
  const verAyuda = ayuda && (mantenerAyuda || !error);
  const idAyuda = `${id}-ayuda`;
  const idMensaje = `${id}-mensaje`;
  const describe = [verAyuda && idAyuda, (error || valido) && idMensaje].filter(Boolean).join(' ');
  const control =
    isValidElement(children) && children.props.id === id
      ? cloneElement(children, { 'aria-invalid': Boolean(error), 'aria-describedby': describe || undefined })
      : children;
  const estado = error ? 'campo-error' : valido ? 'campo-valido' : '';
  return (
    <div className={`campo ${estado}`}>
      <label htmlFor={id}>{etiqueta}</label>
      {control}
      {verAyuda && (typeof ayuda === 'string' ? <small className="ayuda" id={idAyuda}>{ayuda}</small> : <div id={idAyuda}>{ayuda}</div>)}
      {error && <small className="mensaje-error" id={idMensaje}>{error}</small>}
      {!error && valido && (
        <small className="mensaje-exito" id={idMensaje}>
          <Icono nombre="check" tamanio={15} /> Correcto
        </small>
      )}
    </div>
  );
}
