import { useRef, useState } from 'react';
import { Navigate, useNavigate } from 'react-router-dom';
import { useAuth, panelSegunRol } from '../contexto/AuthContext';
import { Alerta, Campo } from '../componentes/comunes';
import { CampoContrasena } from '../componentes/CampoContrasena';
import PantallaAuth from '../componentes/PantallaAuth';
import { leerCampos, validarCelular } from '../utils/validaciones';
import { useValidacion } from '../utils/useValidacion';

const REGLAS = { telefono: validarCelular };
const CAMPOS = Object.keys(REGLAS);

export default function CompletarRegistro() {
  const { usuario, guardarCelular } = useAuth();
  const navegar = useNavigate();
  const formulario = useRef(null);
  const [datos, setDatos] = useState({ telefono: '' });
  const { hayErrores, errorDe, esValido, tocar, editado, tocarTodos, setErroresServidor } = useValidacion(
    REGLAS,
    datos
  );
  const [error, setError] = useState('');
  const [enviando, setEnviando] = useState(false);

  const alSalir = (e) => tocar(e.target.name);
  const cambiar = (e) => {
    const { name, value } = e.target;
    setDatos((d) => ({ ...d, [name]: value }));
    editado(name);
  };

  const sincronizarAutocompletado = () => {
    const leidos = leerCampos(formulario.current, CAMPOS);
    const cambiados = Object.keys(leidos).filter((c) => leidos[c] && leidos[c] !== datos[c]);
    if (!cambiados.length) return;
    setDatos((d) => ({ ...d, ...Object.fromEntries(cambiados.map((c) => [c, leidos[c]])) }));
    tocar(...cambiados);
  };

  if (!usuario) return <Navigate to="/login" replace />;
  if (usuario.tieneCelular) return <Navigate to={panelSegunRol(usuario)} replace />;
  // Los administradores no quedan atrapados aquí
  if (usuario.rol === 'ADMINISTRADOR') return <Navigate to={panelSegunRol(usuario)} replace />;

  async function enviar(e) {
    e.preventDefault();
    if (hayErrores) {
      tocarTodos();
      return;
    }
    setEnviando(true);
    setError('');
    try {
      const u = await guardarCelular(datos.telefono);
      navegar(panelSegunRol(u), { replace: true });
    } catch (err) {
      setErroresServidor(err.porCampo || {});
      setError(err.message);
    } finally {
      setEnviando(false);
    }
  }

  return (
    <PantallaAuth titulo="Completa tu registro" subtitulo="Necesitamos tu celular para procesar tus alquileres.">
      <Alerta>{error}</Alerta>
      <form
        ref={formulario}
        onSubmit={enviar}
        onPointerDownCapture={sincronizarAutocompletado}
        onKeyDownCapture={sincronizarAutocompletado}
        noValidate
      >
        <Campo etiqueta="Nombre completo" id="nombre">
          <input
            id="nombre"
            type="text"
            value={usuario.nombre}
            disabled
          />
        </Campo>
        <Campo etiqueta="Correo electrónico" id="correo">
          <input
            id="correo"
            type="email"
            value={usuario.correo}
            disabled
          />
        </Campo>
        <Campo
          etiqueta="Celular"
          id="telefono"
          error={errorDe('telefono')}
          valido={esValido('telefono')}
          ayuda="9 dígitos, empieza con 9"
        >
          <input
            id="telefono"
            name="telefono"
            type="tel"
            inputMode="tel"
            placeholder="987 654 321"
            maxLength={16}
            value={datos.telefono}
            onChange={cambiar}
            onBlur={alSalir}
            autoComplete="tel-national"
          />
        </Campo>
        <button type="submit" className="boton boton-primario boton-grande boton-bloque" disabled={enviando || hayErrores}>
          {enviando ? 'Guardando…' : 'Continuar'}
        </button>
      </form>
    </PantallaAuth>
  );
}
