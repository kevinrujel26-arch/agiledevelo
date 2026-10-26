// HU-07 Pantalla a la que vuelve el cliente después de pagar en Mercado Pago
import { useCallback, useEffect, useState } from 'react';
import { Link, useSearchParams } from 'react-router-dom';
import { api } from '../api/cliente';
import { Alerta, Cargando } from '../componentes/comunes';
import { formatearMoneda } from '../utils/formato';

export default function PagoResultado() {
  const [params] = useSearchParams();
  const reservaId = params.get('reserva');
  // Mercado Pago agrega payment_id (o collection_id) cuando el cliente llegó a pagar
  const idPago = params.get('payment_id') || params.get('collection_id');

  const [estado, setEstado] = useState(null);
  const [error, setError] = useState('');
  const [yendo, setYendo] = useState(false);

  // Consulta el pago de inmediato, sin esperar el aviso de Mercado Pago
  const verificar = useCallback(async () => {
    setError('');
    try {
      const cuerpo = idPago && /^\d+$/.test(idPago) ? { paymentId: idPago } : {};
      const r = await api.post(`/reservas/${reservaId}/pago/verificar`, cuerpo);
      setEstado(r.datos);
    } catch (e) {
      setError(e.message);
    }
  }, [reservaId, idPago]);

  useEffect(() => {
    if (reservaId && /^\d+$/.test(reservaId)) verificar();
    else setError('No encontramos la reserva que quieres pagar.');
  }, [reservaId, verificar]);

  async function reintentar() {
    setYendo(true);
    setError('');
    try {
      const r = await api.post(`/reservas/${reservaId}/pagar`, {});
      window.location.assign(r.datos.urlPago);
    } catch (e) {
      setError(e.message);
      setYendo(false);
    }
  }

  if (!estado && !error) return <Cargando texto="Confirmando tu pago…" />;

  const pagada = estado?.estadoReserva === 'PAGADA';
  const cancelada = estado?.estadoReserva === 'CANCELADA';
  const rechazada = estado?.estadoPago === 'RECHAZADO';

  let titulo = 'Estamos confirmando tu pago';
  let texto = 'Mercado Pago todavía no nos confirma el pago. Puede tardar unos segundos.';
  if (pagada) {
    titulo = '¡Pago recibido!';
    texto = 'Tu reserva quedó confirmada y la máquina está asegurada para ti.';
  } else if (cancelada) {
    titulo = 'La reserva fue cancelada';
    texto = 'Pasaron más de 2 horas sin pagar, así que se canceló y el horario quedó libre.';
  } else if (rechazada) {
    titulo = 'No se pudo completar el pago';
    texto = estado.motivo;
  }

  return (
    <div className="contenedor contenedor-angosto">
      <div className="panel centrado" role="status" aria-live="polite">
        <h1>{titulo}</h1>
        {estado && (
          <>
            <p className="texto-suave">{texto}</p>
            <p>
              Reserva <strong>{estado.codigo}</strong> · {formatearMoneda(estado.total)}
            </p>
          </>
        )}
        <Alerta>{error}</Alerta>

        <div className="hero-acciones" style={{ justifyContent: 'center', marginBottom: 0 }}>
          {estado && !pagada && !cancelada && (
            <button type="button" className="boton boton-primario" onClick={reintentar} disabled={yendo}>
              {yendo ? 'Redirigiendo…' : rechazada ? 'Reintentar el pago' : 'Pagar ahora'}
            </button>
          )}
          {estado && !pagada && !cancelada && !rechazada && (
            <button type="button" className="boton boton-secundario" onClick={verificar}>
              Actualizar estado
            </button>
          )}
          <Link to="/catalogo" className="boton boton-secundario">Ir al catálogo</Link>
        </div>
      </div>
    </div>
  );
}