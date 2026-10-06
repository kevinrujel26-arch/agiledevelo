// "Continuar con Google" con el botón oficial de Google Identity Services.
// Sin VITE_GOOGLE_CLIENT_ID no se muestra nada (ni el botón ni el separador).
import { useEffect, useRef, useState } from 'react';

const CLIENT_ID = import.meta.env.VITE_GOOGLE_CLIENT_ID || '';
const URL_SCRIPT = 'https://accounts.google.com/gsi/client';

// El script de Google se carga una sola vez, la primera vez que se muestra el botón
let cargaScript = null;
function cargarGoogle() {
  if (window.google?.accounts?.id) return Promise.resolve(window.google);
  if (!cargaScript) {
    cargaScript = new Promise((resolver, rechazar) => {
      const script = document.createElement('script');
      script.src = URL_SCRIPT;
      script.async = true;
      script.onload = () => (window.google?.accounts?.id ? resolver(window.google) : rechazar(new Error('gsi')));
      script.onerror = () => {
        script.remove();
        cargaScript = null; // se puede reintentar al volver a la página
        rechazar(new Error('gsi'));
      };
      document.head.appendChild(script);
    });
  }
  return cargaScript;
}

// Google pide llamar a initialize() una sola vez; la respuesta se pasa al botón que esté en pantalla
let inicializado = false;
let alResponder = null;
function inicializar(google) {
  if (inicializado) return;
  google.accounts.id.initialize({
    client_id: CLIENT_ID,
    callback: (respuesta) => alResponder?.(respuesta),
    ux_mode: 'popup',
    cancel_on_tap_outside: true,
  });
  inicializado = true;
}

/** Tema actual (data-theme de <html>); cambia cuando se pulsa el botón sol/luna */
function useTemaActual() {
  const leer = () => document.documentElement.getAttribute('data-theme') || 'dark';
  const [tema, setTema] = useState(leer);
  useEffect(() => {
    const observador = new MutationObserver(() => setTema(leer()));
    observador.observe(document.documentElement, { attributes: true, attributeFilter: ['data-theme'] });
    return () => observador.disconnect();
  }, []);
  return tema;
}

/**
 * @param texto      'continue_with' | 'signup_with' | 'signin_with' (texto que pone Google en el botón)
 * @param alIngresar recibe el ID token ("credential") que hay que enviar al backend
 * @param ocupado    mientras el backend responde, el botón no se puede volver a pulsar
 */
export default function BotonGoogle({ texto = 'continue_with', alIngresar, ocupado = false }) {
  const contenedor = useRef(null);
  const manejador = useRef(alIngresar);
  manejador.current = alIngresar;
  const tema = useTemaActual();
  const [estado, setEstado] = useState('cargando'); // cargando | listo | error

  useEffect(() => {
    if (!CLIENT_ID) return undefined;
    let cancelado = false;
    cargarGoogle()
      .then((google) => {
        if (cancelado || !contenedor.current) return;
        inicializar(google);
        alResponder = (respuesta) => {
          if (respuesta?.credential) manejador.current?.(respuesta.credential);
        };
        contenedor.current.innerHTML = '';
        google.accounts.id.renderButton(contenedor.current, {
          type: 'standard',
          theme: tema === 'dark' ? 'filled_black' : 'outline',
          size: 'large',
          shape: 'pill',
          text: texto,
          logo_alignment: 'center',
          locale: 'es',
          width: Math.max(200, Math.min(400, contenedor.current.offsetWidth || 320)),
        });
        setEstado('listo');
      })
      .catch(() => {
        if (!cancelado) setEstado('error');
      });
    return () => {
      cancelado = true;
    };
  }, [tema, texto]);

  // Al salir de la página, la respuesta de Google ya no tiene a quién avisar
  useEffect(() => () => {
    alResponder = null;
  }, []);

  if (!CLIENT_ID) return null;

  return (
    <div className="acceso-google">
      <div className="separador-o" role="separator" aria-label="o">
        <span aria-hidden="true">o</span>
      </div>
      <div className={`boton-google${ocupado ? ' ocupado' : ''}`} aria-busy={estado === 'cargando' || ocupado}>
        <div ref={contenedor} className="boton-google-marco" />
        {estado === 'cargando' && <span className="texto-suave">Cargando Google…</span>}
      </div>
      {estado === 'error' && (
        <p className="mensaje-error boton-google-error" role="alert">
          No se pudo cargar el acceso con Google. Revisa tu conexión o usa tu correo y contraseña.
        </p>
      )}
    </div>
  );
}
