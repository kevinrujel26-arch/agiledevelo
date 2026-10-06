// HU-02 Iniciar sesión
import { useRef, useState } from 'react';
import { Link, Navigate, useLocation, useNavigate } from 'react-router-dom';
import { useAuth, panelSegunRol } from '../contexto/AuthContext';
import { Alerta, Campo } from '../componentes/comunes';
import PantallaAuth from '../componentes/PantallaAuth';
import BotonGoogle from '../componentes/BotonGoogle';
import { leerCampos, validarContrasenaIngreso, validarCorreo } from '../utils/validaciones';
import { useValidacion } from '../utils/useValidacion';

// Solo se valida el formato; si el correo existe o la contraseña es correcta lo responde el servidor
const REGLAS = { correo: validarCorreo, contrasena: validarContrasenaIngreso };
const CAMPOS = Object.keys(REGLAS);

export default function Login() {
  const { usuario, iniciarSesion, iniciarSesionGoogle, avisoSesion, limpiarAviso } = useAuth();
  const navegar = useNavigate();
  const { state } = useLocation();

  const formulario = useRef(null);
  const [datos, setDatos] = useState({ correo: state?.correo || '', contrasena: '' });
  const { hayErrores, errorDe, esValido, tocar, editado, tocarTodos, setErroresServidor } = useValidacion(
    REGLAS,
    datos,
    { correo: Boolean(state?.correo) }
  );
  const [recordarme, setRecordarme] = useState(false);
  const [error, setError] = useState('');
  const [enviando, setEnviando] = useState(false);

  // Si venía de una página protegida, vuelve a ella; si no, a su panel según el rol
  const destinoPara = (u) =>
    state?.desde && (u.rol === 'ADMINISTRADOR' || !state.desde.startsWith('/admin'))
      ? state.desde
      : panelSegunRol(u);

  const alSalir = (e) => tocar(e.target.name);
  const cambiar = (e) => {
    const { name, value } = e.target;
    setDatos((d) => ({ ...d, [name]: value }));
    editado(name);
  };

  // Recoge lo que el navegador autocompletó sin avisar con onChange
  const sincronizarAutocompletado = () => {
    const leidos = leerCampos(formulario.current, CAMPOS);
    const cambiados = Object.keys(leidos).filter((c) => leidos[c] && leidos[c] !== datos[c]);
    if (!cambiados.length) return;
    setDatos((d) => ({ ...d, ...Object.fromEntries(cambiados.map((c) => [c, leidos[c]])) }));
    tocar(...cambiados);
  };

  if (usuario) return <Navigate to={destinoPara(usuario)} replace />;

  async function enviar(e) {
    e.preventDefault();
    if (hayErrores) {
      tocarTodos();
      return;
    }
    setEnviando(true);
    setError('');
    limpiarAviso();
    try {
      const u = await iniciarSesion({ correo: datos.correo, contrasena: datos.contrasena, recordarme });
      navegar(destinoPara(u), { replace: true });
    } catch (err) {
      setErroresServidor(err.porCampo || {});
      setError(err.message);
    } finally {
      setEnviando(false);
    }
  }

  // "Continuar con Google": si la cuenta es nueva, va a "Mi cuenta" a completar su celular
  async function ingresarConGoogle(credential) {
    setEnviando(true);
    setError('');
    limpiarAviso();
    try {
      const { usuario: u, nuevo } = await iniciarSesionGoogle({ credential, recordarme });
      navegar(nuevo ? panelSegunRol(u) : destinoPara(u), { replace: true });
    } catch (err) {
      setError(err.message);
    } finally {
      setEnviando(false);
    }
  }

  return (
    <PantallaAuth titulo="Bienvenido de nuevo" subtitulo="Ingresa para gestionar tus alquileres.">
      <Alerta tipo="exito">{state?.mensaje}</Alerta>
      <Alerta tipo="aviso" alCerrar={limpiarAviso}>{avisoSesion}</Alerta>
      <Alerta>{error}</Alerta>
      <form
        ref={formulario}
        onSubmit={enviar}
        onPointerDownCapture={sincronizarAutocompletado}
        onKeyDownCapture={sincronizarAutocompletado}
        noValidate
      >
        <Campo etiqueta="Correo electrónico" id="correo" error={errorDe('correo')} valido={esValido('correo')}>
          <input
            id="correo"
            name="correo"
            type="email"
            placeholder="tu@correo.com"
            value={datos.correo}
            onChange={cambiar}
            onBlur={alSalir}
            autoComplete="email"
          />
        </Campo>
        <Campo etiqueta="Contraseña" id="contrasena" error={errorDe('contrasena')}>
          <input
            id="contrasena"
            name="contrasena"
            type="password"
            placeholder="••••••••"
            value={datos.contrasena}
            onChange={cambiar}
            onBlur={alSalir}
            autoComplete="current-password"
          />
        </Campo>
        <label className="casilla">
          <input type="checkbox" checked={recordarme} onChange={(e) => setRecordarme(e.target.checked)} />
          Recordarme en este equipo
        </label>
        <button type="submit" className="boton boton-primario boton-grande boton-bloque" disabled={enviando || hayErrores}>
          {enviando ? 'Ingresando…' : 'Ingresar'}
        </button>
      </form>
      <BotonGoogle texto="continue_with" alIngresar={ingresarConGoogle} ocupado={enviando} />
      <p className="pie-formulario">
        ¿No tienes cuenta? <Link to="/registro">Regístrate gratis</Link>
      </p>
    </PantallaAuth>
  );
}
