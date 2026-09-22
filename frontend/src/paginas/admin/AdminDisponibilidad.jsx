// HU-09 Gestionar disponibilidad
import { useCallback, useEffect, useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import { api } from '../../api/cliente';
import { Alerta, Cargando } from '../../componentes/comunes';
import { formatearFecha, formatearRango, hoyISO } from '../../utils/formato';

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

  const cargar = useCallback(() => {
    api.get(`/admin/maquinas/${id}/bloqueos`).then((r) => setBloqueos(r.datos)).catch((e) => setError(e.message));
  }, [id]);

  useEffect(() => {
    api.get(`/admin/maquinas/${id}`).then(setMaquina).catch((e) => setError(e.message));
    cargar();
  }, [id, cargar]);

  const cambiarRango = (i, campo, valor) =>
    setRangos(rangos.map((r, j) => {
      if (j !== i) return r;
      const nuevo = { ...r, [campo]: valor };
      // Comodidad: si solo se elige el inicio, el fin es el mismo día
      if (campo === 'fechaInicio' && (!r.fechaFin || r.fechaFin < valor)) nuevo.fechaFin = valor;
      return nuevo;
    }));

  async function bloquear(e) {
    e.preventDefault();
    setError('');
    setExito('');
    const completos = rangos.filter((r) => r.fechaInicio && r.fechaFin);
    if (completos.length === 0) return setError('Elige al menos un rango de fechas');
    if (completos.some((r) => r.fechaFin < r.fechaInicio)) return setError('La fecha de fin no puede ser anterior a la de inicio');

    setOcupado(true);
    try {
      await api.post(`/admin/maquinas/${id}/bloqueos`, { rangos: completos, motivo: motivo.trim() || null });
      setExito(`${completos.length} rango(s) bloqueado(s). Ya no se pueden reservar esas fechas`);
      setRangos([rangoVacio()]);
      setMotivo('');
      cargar();
    } catch (err) {
      setError(err.message);
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

  const hoy = hoyISO();

  return (
    <>
      <Link to="/admin/maquinas" className="enlace-volver">← Volver a máquinas</Link>
      <div className="cabecera-seccion">
        <h1>Disponibilidad{maquina ? `: ${maquina.nombre}` : ''}</h1>
      </div>
      <Alerta alCerrar={() => setError('')}>{error}</Alerta>
      <Alerta tipo="exito" alCerrar={() => setExito('')}>{exito}</Alerta>

      <form className="panel" onSubmit={bloquear}>
        <h2 className="subtitulo">Bloquear fechas</h2>
        <p className="texto-suave">
          Usa esto para mantenimiento o compromisos fuera de la plataforma. No se pueden bloquear fechas con una reserva pagada.
        </p>
        {rangos.map((r, i) => (
          <div className="fila-rango" key={i}>
            <label>
              Desde
              <input type="date" min={hoy} value={r.fechaInicio} onChange={(e) => cambiarRango(i, 'fechaInicio', e.target.value)} />
            </label>
            <label>
              Hasta
              <input type="date" min={r.fechaInicio || hoy} value={r.fechaFin} onChange={(e) => cambiarRango(i, 'fechaFin', e.target.value)} />
            </label>
            {rangos.length > 1 && (
              <button type="button" className="boton boton-secundario boton-chico" onClick={() => setRangos(rangos.filter((_, j) => j !== i))} aria-label="Quitar rango">
                ×
              </button>
            )}
          </div>
        ))}
        <button type="button" className="boton boton-secundario boton-chico" onClick={() => setRangos([...rangos, rangoVacio()])}>
          + Otro rango
        </button>
        <label className="campo">
          Motivo (opcional)
          <input value={motivo} onChange={(e) => setMotivo(e.target.value)} maxLength={160} placeholder="Ej. Mantenimiento preventivo" />
        </label>
        <div className="acciones-form">
          <button type="submit" className="boton boton-primario" disabled={ocupado}>
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
