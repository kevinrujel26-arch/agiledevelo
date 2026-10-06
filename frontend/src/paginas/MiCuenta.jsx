// Panel del cliente (HU-02: destino tras iniciar sesión con rol Cliente)
import { useState } from 'react';
import { Link } from 'react-router-dom';
import { useAuth } from '../contexto/AuthContext';
import { Alerta, Campo } from '../componentes/comunes';
import { formatearCelular } from '../utils/formato';
import { validarCelular } from '../utils/validaciones';
import { useValidacion } from '../utils/useValidacion';

const REGLAS = { telefono: validarCelular };

// Quien entró con Google (o una cuenta antigua) no tiene celular: se le pide aquí
function CompletarCelular() {
  const { guardarCelular } = useAuth();
  const [datos, setDatos] = useState({ telefono: '' });
  const { hayErrores, errorDe, esValido, tocar, editado, tocarTodos, setErroresServidor } = useValidacion(REGLAS, datos);
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
