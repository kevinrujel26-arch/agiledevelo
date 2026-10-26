import { Fragment, useEffect, useState } from 'react';
import { api } from '../../api/cliente';
import { Alerta, Cargando } from '../../componentes/comunes';
import { formatearMoneda, formatearFecha } from '../../utils/formato';
import { BUSQUEDA_MAX, normalizarTexto, validarBusqueda } from '../../utils/validaciones';

const ESTADOS = [
  ['', 'Todos'],
  ['PENDIENTE_PAGO', 'Pendiente de pago'],
  ['PAGADA', 'Pagada'],
  ['EN_CURSO', 'En curso'],
  ['FINALIZADA', 'Finalizada'],
  ['CANCELADA', 'Cancelada'],
];

const COLORES_ESTADO = {
  PENDIENTE_PAGO: 'aviso',
  PAGADA: 'exito',
  EN_CURSO: 'info',
  FINALIZADA: 'exito',
  CANCELADA: 'cancelada',
};

const ETIQUETA_ESTADO = {
  PENDIENTE_PAGO: 'Pendiente de pago',
  PAGADA: 'Pagada',
  EN_CURSO: 'En curso',
  FINALIZADA: 'Finalizada',
  CANCELADA: 'Cancelada',
};

const ESPERA_BUSQUEDA_MS = 400;

export default function AdminReservas() {
  const [filtroEstado, setFiltroEstado] = useState('');
  const [busqueda, setBusqueda] = useState('');
  const [busquedaRef, setBusquedaRef] = useState(null);

  const [reservas, setReservas] = useState(null);
  const [error, setError] = useState('');
  const [filaExpandida, setFilaExpandida] = useState(null);

  // Cargar reservas
  useEffect(() => {
    let vigente = true;
    setError('');
    api
      .get('/admin/reservas')
      .then((r) => {
        if (vigente) setReservas(r.datos);
      })
      .catch((e) => {
        if (vigente) setError(e.message);
      });
    return () => {
      vigente = false;
    };
  }, []);

  // Validar búsqueda
  const errorBusqueda = validarBusqueda(busqueda);

  // Busca al dejar de escribir
  useEffect(() => {
    if (errorBusqueda) return undefined;
    const temporizador = setTimeout(() => {
      setBusquedaRef(normalizarTexto(busqueda) || null);
    }, ESPERA_BUSQUEDA_MS);
    return () => clearTimeout(temporizador);
  }, [busqueda, errorBusqueda]);

  // Filtrar reservas
  const filtradas = reservas
    ? reservas.filter((r) => {
        if (filtroEstado && r.estado !== filtroEstado) return false;
        if (busquedaRef) {
          const texto = busquedaRef.toLowerCase();
          return (
            r.codigo.toLowerCase().includes(texto) ||
            r.nombreSolicitante.toLowerCase().includes(texto) ||
            r.ruc.includes(texto) ||
            (r.maquinaNombre && r.maquinaNombre.toLowerCase().includes(texto)) ||
            (r.clienteNombre && r.clienteNombre.toLowerCase().includes(texto))
          );
        }
        return true;
      })
    : null;

  const obtenerHorario = (reserva) => {
    const inicio = new Date(reserva.fechaInicio);
    const fin = new Date(reserva.fechaFin);
    const horaInicio = inicio.getHours().toString().padStart(2, '0');
    const horaFin = fin.getHours().toString().padStart(2, '0');
    return `${horaInicio}:00 – ${horaFin}:00`;
  };

  const obtenerFechaYHorario = (reserva) => {
    const fecha = formatearFecha(reserva.fechaInicio);
    const horario = obtenerHorario(reserva);
    return { fecha, horario };
  };

  return (
    <>
      <h1 className="cabecera-seccion">Reservas</h1>
      <Alerta>{error}</Alerta>

      <div className="filtros">
        <div className="pestanas" role="tablist">
          {ESTADOS.map(([valor, texto]) => (
            <button
              type="button"
              role="tab"
              aria-selected={filtroEstado === valor}
              key={valor || 'todos'}
              className={filtroEstado === valor ? 'activa' : ''}
              onClick={() => setFiltroEstado(valor)}
            >
              {texto}
            </button>
          ))}
        </div>
        <form
          onSubmit={(e) => {
            e.preventDefault();
            setBusquedaRef(normalizarTexto(busqueda) || null);
          }}
        >
          <input
            type="search"
            placeholder="Buscar por código, solicitante, DNI/RUC, máquina o cliente"
            value={busqueda}
            maxLength={BUSQUEDA_MAX}
            onChange={(e) => setBusqueda(e.target.value)}
            aria-label="Buscar reservas"
            aria-invalid={Boolean(errorBusqueda)}
            aria-describedby={errorBusqueda ? 'busqueda-mensaje' : undefined}
          />
        </form>
      </div>
      {errorBusqueda && <p className="mensaje-error" id="busqueda-mensaje">{errorBusqueda}</p>}

      {!filtradas ? (
        <Cargando />
      ) : filtradas.length === 0 ? (
        <div className="vacio">
          <p>No hay reservas con estos filtros.</p>
        </div>
      ) : (
        <div className="tabla-admin-contenedor">
          <table className="tabla-admin">
            <thead>
              <tr>
                <th className="col-codigo">Código</th>
                <th className="col-maquina">Máquina</th>
                <th className="col-cliente">Cliente</th>
                <th className="col-solicitante">Solicitante</th>
                <th className="col-fecha">Fecha y horario</th>
                <th className="col-total">Total</th>
                <th className="col-estado">Estado</th>
              </tr>
            </thead>
            <tbody>
              {filtradas.map((r) => {
                const { fecha, horario } = obtenerFechaYHorario(r);
                return (
                  <Fragment key={r.id}>
                    <tr
                      className="fila-expandible"
                      onClick={() => setFilaExpandida(filaExpandida === r.id ? null : r.id)}
                      role="button"
                      tabIndex={0}
                      onKeyDown={(e) => {
                        if (e.key === 'Enter' || e.key === ' ') {
                          e.preventDefault();
                          setFilaExpandida(filaExpandida === r.id ? null : r.id);
                        }
                      }}
                    >
                      <td className="col-codigo col-sticky"><span className="monoespaciado">{r.codigo}</span></td>
                      <td className="col-maquina">{r.maquinaNombre}</td>
                      <td className="col-cliente">
                        <strong>{r.clienteNombre}</strong>
                        <div className="texto-suave">{r.clienteCorreo}</div>
                      </td>
                      <td className="col-solicitante">
                        <strong>{r.nombreSolicitante}</strong>
                        <div className="texto-suave">{(r.ruc || "").length === 8 ? "DNI" : "RUC"} {r.ruc}</div>
                      </td>
                      <td className="col-fecha">
                        <div>{fecha}</div>
                        <div className="texto-suave">{horario} · {r.horas}h</div>
                      </td>
                      <td className="col-total">
                        <span>{formatearMoneda(r.montoTotal)}</span>
                        {Number(r.combustibleTotal) > 0 && (
                          <small className="texto-suave"> (incl. combustible {formatearMoneda(r.combustibleTotal)})</small>
                        )}
                      </td>
                      <td className="col-estado">
                        <span className={`insignia estado-${COLORES_ESTADO[r.estado].toLowerCase()}`}>
                          {ETIQUETA_ESTADO[r.estado]}
                        </span>
                      </td>
                    </tr>
                    {filaExpandida === r.id && (
                      <tr className="fila-detalle">
                        <td colSpan={7}>
                          <div className="detalle-reserva">
                            <div className="detalle-columna">
                              <h4>Contacto</h4>
                              <p><strong>Teléfono:</strong> {r.telefonoContacto}</p>
                              <p><strong>Correo:</strong> {r.emailContacto}</p>
                            </div>
                            <div className="detalle-columna">
                              <h4>Ubicación y detalles</h4>
                              <p><strong>Obra:</strong> {r.ubicacionObra}</p>
                              {r.detalle && <p><strong>Detalle:</strong> {r.detalle}</p>}
                            </div>
                            <div className="detalle-columna">
                              <h4>Reserva</h4>
                              <p><strong>Creada:</strong> {formatearFecha(r.creadoEn)}</p>
                            </div>
                            {r.estado === 'CANCELADA' && r.canceladaEn && (
                              <div className="detalle-columna">
                                <h4>Cancelación</h4>
                                <p><strong>Fecha:</strong> {formatearFecha(r.canceladaEn)}</p>
                              </div>
                            )}
                          </div>
                        </td>
                      </tr>
                    )}
                  </Fragment>
                );
              })}
            </tbody>
          </table>
        </div>
      )}
    </>
  );
}
