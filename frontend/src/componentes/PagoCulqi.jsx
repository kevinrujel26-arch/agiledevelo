import { useEffect, useRef, useState } from 'react';
import { api } from '../api/cliente';
import { formatearMoneda } from '../utils/formato';
import { Alerta } from './comunes';

const URL_CHECKOUT = 'https://js.culqi.com/checkout-js';
const URL_3DS = 'https://3ds.culqi.com';
const cargas = {};

function cargarScript(url) {
  if (!cargas[url]) {
    cargas[url] = new Promise((resolver, rechazar) => {
      const script = document.createElement('script');
      script.src = url;
      script.async = true;
      script.onload = resolver;
      script.onerror = () => {
        delete cargas[url];
        rechazar(new Error('No se pudo cargar el formulario de pago. Revisa tu conexión'));
      };
      document.head.appendChild(script);
    });
  }
  return cargas[url];
}

export default function PagoCulqi({ reservaId, total, onPagado }) {
  const [error, setError] = useState('');
  const [aviso, setAviso] = useState('');
  const [procesando, setProcesando] = useState(false);
  const checkout = useRef(null);
  const enCurso = useRef(false);
  const clave = useRef('');
  const alPagar = useRef(onPagado);
  alPagar.current = onPagado;

  useEffect(() => {
    let cancelado = false;

    function terminar(estado) {
      if (estado.estadoReserva === 'PAGADA') {
        alPagar.current(estado);
        return;
      }
      if (estado.estadoReserva === 'CANCELADA') {
        setError('La reserva fue cancelada porque pasaron más de 2 horas sin pagar');
        return;
      }
      if (estado.estadoPago === 'RECHAZADO') {
        setError(estado.motivo || 'El pago fue rechazado. Prueba con otra tarjeta');
        return;
      }
      setAviso('Culqi está revisando tu pago. Cuando lo confirme, tu reserva quedará pagada.');
    }

    async function cobrar(token, correo, dispositivoId, parametros3ds) {
      const r = await api.post(`/reservas/${reservaId}/pagar-con-tarjeta`, {
        token,
        correo,
        dispositivoId,
        parametros3ds,
      });
      return r.datos;
    }

    function autenticar(token, correo, dispositivoId) {
      const Culqi3DS = window.Culqi3DS;
      Culqi3DS.publicKey = clave.current;
      Culqi3DS.settings = {
        charge: { totalAmount: Math.round(Number(total) * 100), returnUrl: window.location.href, currency: 'PEN' },
        card: { email: correo },
      };
      Culqi3DS.options = {
        showModal: true,
        showLoading: true,
        showIcon: true,
        closeModalAction: () => {
          window.removeEventListener('message', alResponder);
          setProcesando(false);
          enCurso.current = false;
          setAviso('');
          setError('Cancelaste la verificación con tu banco. Intenta nuevamente');
        },
      };

      async function alResponder(evento) {
        if (evento.origin !== window.location.origin) return;
        if (evento.data?.error) {
          window.removeEventListener('message', alResponder);
          Culqi3DS.reset();
          setProcesando(false);
          enCurso.current = false;
          setAviso('');
          setError('No se pudo verificar tu identidad con el banco. Intenta nuevamente o usa otra tarjeta');
          return;
        }
        if (!evento.data?.parameters3DS) return;
        window.removeEventListener('message', alResponder);
        Culqi3DS.reset();
        try {
          terminar(await cobrar(token, correo, dispositivoId, evento.data.parameters3DS));
        } catch (e) {
          setError(e.message || 'No se pudo procesar el pago. Intenta nuevamente');
        } finally {
          setProcesando(false);
          enCurso.current = false;
          setAviso('');
        }
      }

      window.addEventListener('message', alResponder);
      setAviso('Confirmando tu pago con el banco…');
      Culqi3DS.initAuthentication(token);
    }

    async function alTokenizar() {
      const culqi = checkout.current;
      if (!culqi?.token) {
        if (culqi?.error) setError(culqi.error.user_message || 'No se pudo validar la tarjeta. Revisa los datos');
        return;
      }
      if (enCurso.current) return;
      enCurso.current = true;
      const token = culqi.token.id;
      const correo = culqi.token.email;
      setError('');
      setAviso('');
      setProcesando(true);
      try {
        const generado = await window.Culqi3DS.generateDevice();
        const dispositivoId = typeof generado === 'string' ? generado : crypto.randomUUID();
        const estado = await cobrar(token, correo, dispositivoId);
        if (estado.urlDesafio) {
          autenticar(token, correo, dispositivoId);
          return;
        }
        terminar(estado);
      } catch (e) {
        setError(e.message || 'No se pudo procesar el pago. Intenta nuevamente');
      }
      setProcesando(false);
      enCurso.current = false;
    }

    (async () => {
      try {
        const [config] = await Promise.all([api.get('/pagos/config'), cargarScript(URL_CHECKOUT), cargarScript(URL_3DS)]);
        if (cancelado) return;
        clave.current = config.datos.publicKey;
        const culqi = new window.CulqiCheckout(clave.current, {
          settings: { title: 'MaquiRenta', currency: 'PEN', amount: Math.round(Number(total) * 100) },
          options: {
            lang: 'es',
            installments: false,
            modal: false,
            container: '#contenedor-pago-culqi',
            paymentMethods: { tarjeta: true, yape: false, billetera: false, bancaMovil: false, agente: false, cuotealo: false },
          },
          appearance: {
            theme: 'default',
            hiddenCulqiLogo: false,
            hiddenBannerContent: true,
            hiddenBanner: true,
            hiddenToolBarAmount: true,
            menuType: 'sidebar',
            buttonCardPayText: 'Pagar',
            variables: {
              fontFamily: 'Inter, system-ui, sans-serif',
              borderRadius: '10px',
              colorBackground: '#171b22',
              colorPrimary: '#ff8a1f',
              colorPrimaryText: '#1a1206',
              colorText: '#e8eaef',
              colorTextSecondary: '#9aa3b2',
              colorTextPlaceholder: '#6b7380',
            },
            rules: {
              '.Culqi-Main-Container': { background: '#171b22', color: '#e8eaef' },
              '.Culqi-Main-Method': { background: '#171b22', color: '#e8eaef' },
              '.Culqi-Label': { color: '#9aa3b2' },
              '.Culqi-Input': { background: '#0e1117', border: '1px solid #3a404c', color: '#e8eaef' },
              '.Culqi-Input:focus': { border: '1px solid #ff8a1f' },
              '.Culqi-Input.input-valid': { background: '#0e1117', border: '1px solid #3ddc97', color: '#e8eaef' },
              '.Culqi-Input.input-invalid': { background: '#0e1117', border: '1px solid #ff6b6b', color: '#e8eaef' },
              '.Culqi-Button': { background: '#ff8a1f', color: '#1a1206', fontWeight: '700' },
              '.Culqi-message-error': { background: '#3a1d1f', color: '#ff9b9b' },
              '.Culqi-Text-Link': { color: '#ffb24a' },
            },
          },
        });
        culqi.culqi = alTokenizar;
        checkout.current = culqi;
        culqi.open();
      } catch (e) {
        if (!cancelado) setError(e.message || 'No se pudo cargar el formulario de pago');
      }
    })();

    return () => {
      cancelado = true;
    };
  }, [reservaId, total]);

  return (
    <div style={{ textAlign: 'left', marginBottom: 24 }}>
      <h3 style={{ marginBottom: 4 }}>Paga con tarjeta</h3>
      <p className="texto-suave" style={{ marginTop: 0 }}>
        Total a pagar: <strong>{formatearMoneda(total)}</strong> en 1 cuota. Los datos de tu tarjeta van directo a
        Culqi: esta página no los guarda.
      </p>
      <Alerta>{error}</Alerta>
      <Alerta tipo="aviso">{aviso}</Alerta>
      <div style={{ position: 'relative' }}>
        <div id="contenedor-pago-culqi" />
        {procesando && (
          <div
            style={{
              position: 'absolute',
              inset: 0,
              zIndex: 5,
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
              borderRadius: 12,
              background: 'rgba(14, 17, 23, 0.85)',
              color: '#fff',
              fontWeight: 600,
              fontSize: '1.1rem',
              textAlign: 'center',
              padding: 24,
            }}
          >
            Procesando tu pago…
            <br />
            No cierres ni recargues la página
          </div>
        )}
      </div>
    </div>
  );
}
