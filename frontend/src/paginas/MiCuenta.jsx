// Panel del cliente (HU-02: destino tras iniciar sesión con rol Cliente)
import { useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { useAuth } from '../contexto/AuthContext';
import { Alerta, Campo } from '../componentes/comunes';
import { CampoContrasena } from '../componentes/CampoContrasena';
import { formatearCelular } from '../utils/formato';
import { validarCelular, validarContrasenaNueva, validarConfirmacion } from '../utils/validaciones';
import { useValidacion } from '../utils/useValidacion';

const REGLAS_CELULAR = { telefono: validarCelular };
const REGLAS_CONTRASENA = { actual: (v) => v && v.length > 0 ? null : 'Ingresa tu contraseña actual', nueva: validarContrasenaNueva, confirmacion: (v, d) => validarConfirmacion(v, d.nueva) };

// Quien entró con Google (o una cuenta antigua) no tiene celular: se le pide aquí
function CompletarCelular() {
  const { guardarCelular } = useAuth();
  const [datos, setDatos] = useState({ telefono: '' });
  const { hayErrores, errorDe, esValido, tocar, editado, tocarTodos, setErroresServidor } = useValidacion(REGLAS_CELULAR, datos);
  const [error, setError] = useState('');
  const [enviando, setEnviando] = useState(false);

  async function enviar(e) {
    e.preventDefault();
    setError('');
    if (hayErrores) {
      tocarTodos();
      return;
    }
    setEnviando(true);
    try {
      await guardarCelular(datos.telefono);
    } catch (err) {
      setErroresServidor(err.porCampo || {});
      setError(err.message);
      setEnviando(false);
    }
  }

  return (
    <section className="completar-celular" aria-labelledby="titulo-celular">
      <h2 id="titulo-celular">Completa tu celular</h2>
      <p className="texto-suave">Lo necesitamos para coordinar la entrega de tus alquileres.</p>
      <Alerta>{error}</Alerta>
      <form onSubmit={enviar} noValidate>
        <Campo etiqueta="Celular" id="telefono" error={errorDe('telefono')} valido={esValido('telefono')} ayuda="9 dígitos, empieza con 9">
          <input
            id="telefono"
            name="telefono"
            type="tel"
            inputMode="tel"
            placeholder="987 654 321"
            maxLength={16}
            value={datos.telefono}
            onChange={(e) => {
              setDatos({ telefono: e.target.value });
              editado('telefono');
            }}
            onBlur={() => tocar('telefono')}
            autoComplete="tel-national"
          />
        </Campo>
        <button type="submit" className="boton boton-primario boton-bloque" disabled={enviando || hayErrores}>
          {enviando ? 'Guardando…' : 'Guardar celular'}
        </button>
      </form>
    </section>
  );
}

function CambiarContrasena() {
  const { cambiarContrasena } = useAuth();
  const navegar = useNavigate();
  const [datos, setDatos] = useState({ actual: '', nueva: '', confirmacion: '' });
  const { hayErrores, errorDe, esValido, tocar, editado, tocarTodos, setErroresServidor } = useValidacion(REGLAS_CONTRASENA, datos);
  const [error, setError] = useState('');
  const [enviando, setEnviando] = useState(false);

  const alSalir = (e) => tocar(e.target.name);
  const cambiar = (e) => {
    const { name, value } = e.target;
    setDatos((d) => ({ ...d, [name]: value }));
    editado(name);
  };

  async function enviar(e) {
    e.preventDefault();
    setError('');
    if (hayErrores) {
      tocarTodos();
      return;
    }
    setEnviando(true);
    try {
      await cambiarContrasena(datos.actual, datos.nueva, datos.confirmacion);
      // Limpiar el formulario tras cambiar con éxito
      setDatos({ actual: '', nueva: '', confirmacion: '' });
      setError('');
    } catch (err) {
      // Si es 401 (contraseña actual incorrecta), no es un error de sesión, así que la sesión sigue válida
      if (err.status !== 401) {
        setErroresServidor(err.porCampo || {});
      }
      setError(err.message);
      setEnviando(false);
    }
  }

  return (
    <section className="cambiar-contrasena" aria-labelledby="titulo-contrasena">
      <h2 id="titulo-contrasena">Cambiar contraseña</h2>
      <Alerta>{error}</Alerta>
      <form onSubmit={enviar} noValidate>
        <Campo etiqueta="Contraseña actual" id="actual" error={errorDe('actual')}>
          <CampoContrasena
            id="actual"
            name="actual"
            placeholder="••••••••"
            value={datos.actual}
            onChange={cambiar}
            onBlur={alSalir}
            autoComplete="current-password"
          />
        </Campo>
        <Campo etiqueta="Nueva contraseña" id="nueva" error={errorDe('nueva')} valido={esValido('nueva')}>
          <CampoContrasena
            id="nueva"
            name="nueva"
            placeholder="••••••••"
            value={datos.nueva}
            onChange={cambiar}
            onBlur={alSalir}
            autoComplete="new-password"
          />
        </Campo>
        <Campo etiqueta="Repite la contraseña" id="confirmacion" error={errorDe('confirmacion')} valido={esValido('confirmacion')}>
          <CampoContrasena
            id="confirmacion"
            name="confirmacion"
            placeholder="••••••••"
            value={datos.confirmacion}
            onChange={cambiar}
            onBlur={alSalir}
            autoComplete="new-password"
          />
        </Campo>
        <button type="submit" className="boton boton-primario boton-bloque" disabled={enviando || hayErrores}>
          {enviando ? 'Cambiando…' : 'Cambiar contraseña'}
        </button>
      </form>
    </section>
  );
}

export default function MiCuenta() {
  const { usuario } = useAuth();
  return (
    <div className="contenedor contenedor-angosto">
      <div className="panel">
        <h1>Hola, {usuario.nombre.split(' ')[0]} 👋</h1>
        <p className="texto-suave">{usuario.correo}</p>
        <p className="texto-suave">
          Celular: {usuario.telefono ? formatearCelular(usuario.telefono) : 'sin registrar'}
        </p>
        {!usuario.telefono && <CompletarCelular />}
        {usuario.tieneContrasena && <CambiarContrasena />}
        {!usuario.tieneContrasena && (
          <section className="sin-contrasena" aria-labelledby="titulo-sin-contrasena">
            <h2 id="titulo-sin-contrasena">Contraseña</h2>
            <p className="texto-suave">Tu cuenta usa Google para iniciar sesión. No tienes una contraseña asignada.</p>
          </section>
        )}
        <div className="tarjetas-accion">
          <Link to="/catalogo" className="tarjeta-accion">
            <strong>Explorar catálogo</strong>
            <span>Revisa la maquinaria disponible y sus tarifas.</span>
          </Link>
          <div className="tarjeta-accion deshabilitada" title="Disponible en el Sprint 3">
            <strong>Mis reservas</strong>
            <span>Próximamente: aquí verás tus reservas y su estado.</span>
          </div>
        </div>
      </div>
    </div>
  );
}
