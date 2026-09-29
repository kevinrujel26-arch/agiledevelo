// HU-09 Gestionar disponibilidad
import { useCallback, useEffect, useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import { api } from '../../api/cliente';
import { Alerta, Cargando } from '../../componentes/comunes';
import { formatearFecha, formatearRango, hoyISO } from '../../utils/formato';
import { ANIOS_BLOQUEO, MAX_RANGOS, erroresRango, sumarAnios, validarMotivo } from '../../utils/validaciones';

const rangoVacio = () => ({ fechaInicio: '', fechaFin: '' });

export default function AdminDisponibilidad() {
  const { id } = useParams();
  const [maquina, setMaquina] = useState(null);
  const [bloqueos, setBloqueos] = useState(null);
  const [rangos, setRangos] = useState([rangoVacio()]);
  const [motivo, setMotivo] = useState('');
  const [error, setError] = useState('');
  const [exito, setExito] = useState('');
  const [ocupado, setOcupado] = useState(false);
  // Errores por campo que devolvió el servidor ({ 'rangos.0.fechaFin': '...', motivo: '...' }), con índices de la UI
  const [erroresServidor, setErroresServidor] = useState({});

  const hoy = hoyISO();
  // Validación en vivo: cada rango con alguna fecha elegida y el motivo mientras se escribe
  const erroresRangos = rangos.map((r) => erroresRango(r, hoy));
  const errorMotivo = validarMotivo(motivo);
  const errorCantidad = rangos.length > MAX_RANGOS ? `Puedes bloquear como máximo ${MAX_RANGOS} rangos a la vez` : '';
  const hayErrores = Boolean(errorMotivo || errorCantidad) || erroresRangos.some((e) => Object.keys(e).length > 0);
  const errorDeRango = (i, campo) => erroresServidor[`rangos.${i}.${campo}`] || erroresRangos[i][campo] || '';

  const cargar = useCallback(() => {
    api.get(`/admin/maquinas/${id}/bloqueos`).then((r) => setBloqueos(r.datos)).catch((e) => setError(e.message));
  }, [id]);

  useEffect(() => {
    api.get(`/admin/maquinas/${id}`).then(setMaquina).catch((e) => setError(e.message));
    cargar();
  }, [id, cargar]);

  const cambiarRango = (i, campo, valor) => {
    setErroresServidor({});
    setRangos(rangos.map((r, j) => {
      if (j !== i) return r;
      const nuevo = { ...r, [campo]: valor };
      // Comodidad: si solo se elige el inicio, el fin es el mismo día
      if (campo === 'fechaInicio' && (!r.fechaFin || r.fechaFin < valor)) nuevo.fechaFin = valor;
      return nuevo;
    }));
  };

  async function bloquear(e) {
    e.preventDefault();
    setError('');
    setExito('');
    if (hayErrores) return;
    // Se envían los rangos con alguna fecha; se recuerda su fila para ubicar los errores del servidor
    const filas = rangos.map((r, i) => ({ ...r, i })).filter((r) => r.fechaInicio || r.fechaFin);
    if (filas.length === 0) return setError('Agrega al menos un rango de fechas');

    setOcupado(true);
    try {
      const enviados = filas.map(({ fechaInicio, fechaFin }) => ({ fechaInicio, fechaFin }));
      await api.post(`/admin/maquinas/${id}/bloqueos`, { rangos: enviados, motivo: motivo.trim() || null });
      setExito(`${filas.length} rango(s) bloqueado(s). Ya no se pueden reservar esas fechas`);
      setRangos([rangoVacio()]);
      setMotivo('');
      setErroresServidor({});
      cargar();
    } catch (err) {
      setError(err.message);
      // 'rangos.K.campo' del servidor se refiere al K-ésimo rango enviado: se traduce a su fila en pantalla
      const traducidos = Object.entries(err.porCampo || {}).map(([campo, mensaje]) => {
        const m = /^rangos\.(\d+)\.(.+)$/.exec(campo);
        return [m && filas[m[1]] ? `rangos.${filas[m[1]].i}.${m[2]}` : campo, mensaje];
      });
      setErroresServidor(Object.fromEntries(traducidos));
    } finally {
      setOcupado(false);
    }
  }

  async function desbloquear(b) {
    if (!window.confirm(`¿Desbloquear ${formatearRango(b.fechaInicio, b.fechaFin)}?`)) return;
    setError('');
    setExito('');
    try {
      await api.delete(`/admin/maquinas/${id}/bloqueos/${b.id}`);
      setExito('Fechas desbloqueadas');
      cargar();
    } catch (err) {
      setError(err.message);
    }
  }

  const limite = sumarAnios(hoy, ANIOS_BLOQUEO);
  const errorMotivoVisible = erroresServidor.motivo || errorMotivo;
  const errorRangosGeneral = erroresServidor.rangos || errorCantidad;

  return (
    <>
      <Link to="/admin/maquinas" className="enlace-volver">← Volver a máquinas</Link>
      <div className="cabecera-seccion">
        <h1>Disponibilidad{maquina ? `: ${maquina.nombre}` : ''}</h1>
      </div>
      <Alerta alCerrar={() => setError('')}>{error}</Alerta>
      <Alerta tipo="exito" alCerrar={() => setExito('')}>{exito}</Alerta>

      <form className="panel" onSubmit={bloquear} noValidate>
        <h2 className="subtitulo">Bloquear fechas</h2>
        <p className="texto-suave">
          Usa esto para mantenimiento o compromisos fuera de la plataforma. No se pueden bloquear fechas con una reserva pagada.
        </p>
        {rangos.map((r, i) => {
          const errInicio = errorDeRango(i, 'fechaInicio');
          const errFin = errorDeRango(i, 'fechaFin');
          const idInicio = `rango-${i}-inicio-mensaje`;
          const idFin = `rango-${i}-fin-mensaje`;
          return (
            <div key={i} className={errInicio || errFin ? 'campo-error' : ''}>
              <div className="fila-rango">
                <label>
                  Desde
                  <input
                    type="date"
                    min={hoy}
                    max={limite}
                    value={r.fechaInicio}
                    onChange={(e) => cambiarRango(i, 'fechaInicio', e.target.value)}
                    aria-invalid={Boolean(errInicio)}
                    aria-describedby={errInicio ? idInicio : undefined}
                  />
                </label>
                <label>
                  Hasta
                  <input
                    type="date"
                    min={r.fechaInicio || hoy}
                    max={limite}
                    value={r.fechaFin}
                    onChange={(e) => cambiarRango(i, 'fechaFin', e.target.value)}
                    aria-invalid={Boolean(errFin)}
                    aria-describedby={errFin ? idFin : undefined}
                  />
                </label>
                {rangos.length > 1 && (
                  <button type="button" className="boton boton-secundario boton-chico" onClick={() => setRangos(rangos.filter((_, j) => j !== i))} aria-label="Quitar rango">
                    ×
                  </button>
                )}
              </div>
              {errInicio && <small className="mensaje-error mensaje-fila" id={idInicio}>Desde: {errInicio}</small>}
              {errFin && <small className="mensaje-error mensaje-fila" id={idFin}>Hasta: {errFin}</small>}
            </div>
          );
        })}
        {errorRangosGeneral && <p className="mensaje-error">{errorRangosGeneral}</p>}
        <button
          type="button"
          className="boton boton-secundario boton-chico"
          onClick={() => setRangos([...rangos, rangoVacio()])}
          disabled={rangos.length >= MAX_RANGOS}
          title={rangos.length >= MAX_RANGOS ? `Máximo ${MAX_RANGOS} rangos a la vez` : undefined}
        >
          + Otro rango
        </button>
        <div className={`campo ${errorMotivoVisible ? 'campo-error' : ''}`}>
          <label htmlFor="motivo">Motivo (opcional)</label>
          <input
            id="motivo"
            value={motivo}
            onChange={(e) => {
              setMotivo(e.target.value);
              setErroresServidor(({ motivo: _descartado, ...resto }) => resto);
            }}
            placeholder="Ej. Mantenimiento preventivo"
            aria-invalid={Boolean(errorMotivoVisible)}
            aria-describedby={errorMotivoVisible ? 'motivo-mensaje' : 'motivo-ayuda'}
          />
          {errorMotivoVisible ? (
            <small className="mensaje-error" id="motivo-mensaje">{errorMotivoVisible}</small>
          ) : (
            <small className="ayuda" id="motivo-ayuda">Hasta 160 caracteres</small>
          )}
        </div>
        <div className="acciones-form">
          <button type="submit" className="boton boton-primario" disabled={ocupado || hayErrores}>
            {ocupado ? 'Bloqueando…' : 'Bloquear fechas'}
          </button>
        </div>
      </form>

      <div className="panel">
        <h2 className="subtitulo">Fechas bloqueadas</h2>
        {!bloqueos ? (
          <Cargando />
        ) : bloqueos.length === 0 ? (
          <p className="texto-suave">No hay fechas bloqueadas desde hoy en adelante.</p>
        ) : (
          <div className="tabla-contenedor">
            <table className="tabla">
              <thead>
                <tr>
                  <th>Fechas</th>
                  <th>Motivo</th>
                  <th>Registrado por</th>
                  <th aria-label="Acciones" />
                </tr>
              </thead>
              <tbody>
                {bloqueos.map((b) => (
                  <tr key={b.id}>
                    <td><strong>{formatearRango(b.fechaInicio, b.fechaFin)}</strong></td>
                    <td>{b.motivo || <span className="texto-suave">—</span>}</td>
                    <td>
                      {b.creadoPor} <span className="texto-suave">{formatearFecha(b.creadoEn)}</span>
                    </td>
                    <td className="acciones">
                      <button type="button" className="boton boton-secundario boton-chico" onClick={() => desbloquear(b)}>
                        Desbloquear
                      </button>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </div>
    </>
  );
}
