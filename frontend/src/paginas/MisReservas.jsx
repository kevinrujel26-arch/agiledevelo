import { useEffect, useState } from 'react';
import { api, descargarArchivo } from '../api/cliente';
import { Alerta, Cargando } from '../componentes/comunes';
import PagoTarjeta from '../componentes/PagoTarjeta';
import { formatearMoneda } from '../utils/formato';

const ESTADOS = [
  ['', 'Todos'],
  ['PENDIENTE_PAGO', 'Pendiente de pago'],
  ['PAGADA', 'Pagada'],
  ['EN_CURSO', 'En curso'],
  ['FINALIZADA', 'Finalizada'],
  ['CANCELADA', 'Cancelada'],
];

const ETIQUETA = Object.fromEntries(ESTADOS.filter(([valor]) => valor));
const ZONA = 'America/Lima';

const fecha = (instante) =>
  new Date(instante).toLocaleDateString('es-PE', { timeZone: ZONA, day: '2-digit', month: '2-digit', year: 'numeric' });

const hora = (instante) =>
  new Date(instante).toLocaleTimeString('es-PE', { timeZone: ZONA, hour: '2-digit', minute: '2-digit', hour12: false });

const puedePagar = (reserva) =>
  reserva.estado === 'PENDIENTE_PAGO' && reserva.expiraPagoEn && new Date(reserva.expiraPagoEn) > new Date();

export default function MisReservas() {
  const [estado, setEstado] = useState('');
  const [reservas, setReservas] = useState(null);
  const [error, setError] = useState('');
  const [procesandoId, setProcesandoId] = useState(null);
  const [tarjetaId, setTarjetaId] = useState(null);
  const [recarga, setRecarga] = useState(0);

  useEffect(() => {
    let vigente = true;
    setReservas(null);
    setError('');
    api
      .get('/reservas/mias', estado ? { estado } : {})
      .then((r) => {
        if (vigente) setReservas(r.datos);
      })
      .catch((e) => {
        if (vigente) setError(e.message);
      });
    return () => {
      vigente = false;
    };
  }, [estado, recarga]);

  async function pagar(reserva) {
    setProcesandoId(reserva.id);
    setError('');
    try {
      let enPagina = false;
      try {
        const c = await api.get('/pagos/config');
        enPagina = c.datos?.pagoEnPagina === true;
      } catch {
        enPagina = false;
      }
      if (enPagina) {
        setTarjetaId(reserva.id);
        setProcesandoId(null);
        return;
      }
      const r = await api.post(`/reservas/${reserva.id}/pagar`, {});
      window.location.assign(r.datos.urlPago);
    } catch (e) {
      setError(e.message);
      setProcesandoId(null);
    }
  }

  async function descargar(reserva, formato) {
    setError('');
    try {
      await descargarArchivo(`/reservas/${reserva.id}/comprobante?formato=${formato}`, `${reserva.comprobante}.${formato}`);
    } catch (e) {
      setError(e.message);
    }
  }

  return (
    <div>
      <div className="panel">
        <h1>Mis reservas</h1>
        <label htmlFor="filtro-estado">Estado</label>
        <select id="filtro-estado" value={estado} onChange={(e) => setEstado(e.target.value)}>
          {ESTADOS.map(([valor, texto]) => (
            <option key={valor} value={valor}>
              {texto}
            </option>
          ))}
        </select>
        <Alerta>{error}</Alerta>
        {reservas === null && !error && <Cargando texto="Cargando tus reservas…" />}
        {reservas && reservas.length === 0 && <p className="texto-suave">No tienes reservas en este estado.</p>}
        {reservas && reservas.length > 0 && (
          <ul style={{ listStyle: 'none', padding: 0 }}>
            {reservas.map((r) => (
              <li key={r.id} className="panel">
                <strong>{r.codigo}</strong> · {r.maquinaNombre}
                <p>
                  {fecha(r.inicio)} · {hora(r.inicio)} - {hora(r.fin)}
                </p>
                <p>
                  {formatearMoneda(r.total)} · {ETIQUETA[r.estado]}
                </p>
                {puedePagar(r) && tarjetaId !== r.id && (
                  <button
                    type="button"
                    className="boton boton-primario"
                    disabled={procesandoId === r.id}
                    onClick={() => pagar(r)}
                  >
                    {procesandoId === r.id ? 'Procesando…' : 'Pagar ahora'}
                  </button>
                )}
                {r.estado === 'PENDIENTE_PAGO' && !puedePagar(r) && <p className="texto-suave">El plazo de pago venció.</p>}
                {r.comprobante && (
                  <p>
                    Comprobante {r.comprobante}{' '}
                    <button type="button" className="boton" onClick={() => descargar(r, 'pdf')}>
                      PDF
                    </button>{' '}
                    <button type="button" className="boton" onClick={() => descargar(r, 'xml')}>
                      XML
                    </button>
                  </p>
                )}
                {tarjetaId === r.id && (
                  <PagoTarjeta
                    reservaId={r.id}
                    total={r.total}
                    onPagado={() => {
                      setTarjetaId(null);
                      setRecarga((n) => n + 1);
                    }}
                  />
                )}
              </li>
            ))}
          </ul>
        )}
      </div>
    </div>
  );
}