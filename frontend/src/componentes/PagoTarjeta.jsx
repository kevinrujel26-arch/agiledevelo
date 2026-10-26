// HU-07 Formulario de tarjeta de Mercado Pago (Card Payment Brick) dentro de la página.
// La tarjeta se escribe en campos seguros de Mercado Pago: a nuestro servidor solo llega un token de un solo uso.
import { useEffect, useRef, useState } from 'react';
import { api } from '../api/cliente';
import { formatearMoneda } from '../utils/formato';
import { Alerta } from './comunes';

const URL_SDK = 'https://sdk.mercadopago.com/js/v2';
let sdkPendiente = null;

// Aspecto del formulario de Mercado Pago: mismos colores que la app (tema oscuro o claro)
function visualBrick(total) {
  const claro = true; // siempre claro: los campos seguros de Mercado Pago son blancos y así no desentonan
  const colores = claro
    ? {
        formBackgroundColor: '#ffffff',
        inputBackgroundColor: '#ffffff',
        textPrimaryColor: '#1b1d22',
        textSecondaryColor: '#5b6270',
        outlineSecondaryColor: '#cfc9bd',
      }
    : {
        formBackgroundColor: '#171b22',
        inputBackgroundColor: '#0e1117',
        textPrimaryColor: '#e8eaef',
        textSecondaryColor: '#9aa3b2',
        outlineSecondaryColor: '#3a404c',
      };
  return {
    hideFormTitle: true,
    texts: {
      emailSectionTitle: 'Tus datos de contacto',
      cardholderName: { label: 'Nombre del titular', placeholder: 'Como aparece en la tarjeta' },
      formSubmit: `Pagar ${formatearMoneda(total)}`,
    },
    style: {
      theme: claro ? 'default' : 'dark',
      customVariables: {
        ...colores,
        baseColor: '#ff8a1f',
        baseColorFirstVariant: '#e07a12',
        baseColorSecondVariant: '#ffb24a',
        buttonTextColor: '#1a1206',
        outlinePrimaryColor: '#ff8a1f',
        errorColor: '#ff6b6b',
        successColor: '#3ddc97',
        borderRadiusMedium: '10px',
        borderRadiusLarge: '14px',
      },
    },
  };
}

function cargarSdk() {
  if (window.MercadoPago) return Promise.resolve();
  if (!sdkPendiente) {
    sdkPendiente = new Promise((resolver, rechazar) => {
      const script = document.createElement('script');
      script.src = URL_SDK;
      script.async = true;
      script.onload = resolver;
      script.onerror = () => {
        sdkPendiente = null;
        rechazar(new Error('No se pudo cargar el formulario de pago de Mercado Pago. Revisa tu conexión'));
      };
      document.head.appendChild(script);
    });
  }
  return sdkPendiente;
}

export default function PagoTarjeta({ reservaId, total, onPagado }) {
  const [error, setError] = useState('');
  const [aviso, setAviso] = useState('');
  const [cargando, setCargando] = useState(true);
  const alPagar = useRef(onPagado);
  alPagar.current = onPagado;

  useEffect(() => {
    let controlador = null;
    let cancelado = false;

    // Se llama cuando el cliente pulsa "Pagar" en el formulario de Mercado Pago
    async function pagar(datos) {
      setError('');
      setAviso('');
      let mostrado = false;
      try {
        const r = await api.post(`/reservas/${reservaId}/pagar-con-tarjeta`, {
          token: datos.token,
          metodoPagoId: datos.payment_method_id,
          emisorId: datos.issuer_id ? String(datos.issuer_id) : undefined,
          correo: datos.payer?.email,
          tipoDocumento: datos.payer?.identification?.type,
          numeroDocumento: datos.payer?.identification?.number ? String(datos.payer.identification.number) : undefined,
        });
        const estado = r.datos;
        if (estado.estadoReserva === 'PAGADA') {
          alPagar.current(estado);
          return;
        }
        if (estado.estadoReserva === 'CANCELADA') {
          mostrado = true;
          setError('La reserva fue cancelada porque pasaron más de 2 horas sin pagar');
          throw new Error('cancelada');
        }
        if (estado.estadoPago === 'RECHAZADO') {
          mostrado = true;
          setError(estado.motivo || 'El pago fue rechazado. Prueba con otra tarjeta');
          throw new Error('rechazado');
        }
        setAviso('Mercado Pago está revisando tu pago. Cuando lo confirme, tu reserva quedará pagada.');
      } catch (e) {
        if (!mostrado) setError(e.message || 'No se pudo procesar el pago. Intenta nuevamente');
        throw e; // el formulario de Mercado Pago queda listo para reintentar
      }
    }

    (async () => {
      try {
        const [config] = await Promise.all([api.get('/pagos/config'), cargarSdk()]);
        if (cancelado) return;
        const mp = new window.MercadoPago(config.datos.publicKey, { locale: 'es-PE' });
        const brick = await mp.bricks().create('cardPayment', 'contenedor-pago-tarjeta', {
          initialization: { amount: Number(total) },
          customization: {
            visual: visualBrick(total),
            paymentMethods: { minInstallments: 1, maxInstallments: 1 },
          },
          callbacks: {
            onReady: () => setCargando(false),
            onSubmit: pagar,
            onError: (e) => console.error('Mercado Pago:', e),
          },
        });
        if (cancelado) brick.unmount();
        else controlador = brick;
      } catch (e) {
        if (!cancelado) {
          setCargando(false);
          setError(e.message || 'No se pudo cargar el formulario de pago');
        }
      }
    })();

    return () => {
      cancelado = true;
      if (controlador) controlador.unmount();
    };
  }, [reservaId, total]);

  return (
    <div style={{ textAlign: 'left', marginBottom: 24 }}>
      <h3 style={{ marginBottom: 4 }}>Paga con tarjeta</h3>
      <p className="texto-suave" style={{ marginTop: 0 }}>
        Total a pagar: <strong>{formatearMoneda(total)}</strong> en 1 cuota. Los datos de tu tarjeta van directo a
        Mercado Pago: esta página no los guarda.
      </p>
      {cargando && !error && <p className="texto-suave">Cargando formulario de pago…</p>}
      <Alerta>{error}</Alerta>
      <Alerta tipo="aviso">{aviso}</Alerta>
      <div id="contenedor-pago-tarjeta" />
    </div>
  );
}