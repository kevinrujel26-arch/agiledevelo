// HU-01 Registrar cliente
import { useRef, useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { api } from '../api/cliente';
import { panelSegunRol, useAuth } from '../contexto/AuthContext';
import { Alerta, Campo } from '../componentes/comunes';
import { CampoContrasena } from '../componentes/CampoContrasena';
import {
  leerCampos,
  requisitosContrasena,
  validarCelular,
  validarConfirmacion,
  validarContrasenaNueva,
  validarCorreo,
  validarNombre,
} from '../utils/validaciones';
import { useValidacion } from '../utils/useValidacion';
import PantallaAuth from '../componentes/PantallaAuth';
import BotonGoogle from '../componentes/BotonGoogle';

const REGLAS = {
  nombre: validarNombre,
  correo: validarCorreo,
  telefono: validarCelular,
  contrasena: validarContrasenaNueva,
  confirmar: (valor, datos) => validarConfirmacion(valor, datos.contrasena),
};
const CAMPOS = Object.keys(REGLAS);

function RequisitosContrasena({ valor, tocado }) {
  return (
    <ul className="requisitos">
      {requisitosContrasena(valor).map((r) => (
        <li key={r.clave} className={r.cumple ? 'cumple' : tocado ? 'falta' : ''}>
          <span aria-hidden="true">{r.cumple ? '✓' : '•'}</span>
          {r.texto}
          <span className="solo-lector">{r.cumple ? ' (cumplido)' : ' (pendiente)'}</span>
        </li>
      ))}
    </ul>
  );
}

export default function Registro() {
  const navegar = useNavigate();
  const { iniciarSesionGoogle } = useAuth();
  const formulario = useRef(null);
  const [datos, setDatos] = useState({ nombre: '', correo: '', telefono: '', contrasena: '', confirmar: '' });
  const { hayErrores, tocados, errorDe, esValido, tocar, editado, tocarTodos, setErroresServidor } = useValidacion(
    REGLAS,
    datos
  );
  const [errorGeneral, setErrorGeneral] = useState('');
  const [enviando, setEnviando] = useState(false);

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

  async function enviar(e) {
    e.preventDefault();
    setErrorGeneral('');
    if (hayErrores) {
      tocarTodos();
      return;
    }

    setEnviando(true);
    try {
      const r = await api.post('/auth/registro', {
        nombre: datos.nombre,
        correo: datos.correo,
        telefono: datos.telefono,
        contrasena: datos.contrasena,
      });
      // HU-01 criterio 6: mensaje de confirmación
      navegar('/login', { state: { mensaje: r.mensaje, correo: r.usuario.correo } });
    } catch (err) {
      setErroresServidor(err.porCampo || {});
      setErrorGeneral(err.message);
    } finally {
      setEnviando(false);
    }
  }

  // Registrarse con Google deja la sesión iniciada; el celular se completa en "Mi cuenta"
  async function registrarConGoogle(credential) {
    setErrorGeneral('');
    setEnviando(true);
    try {
      const { usuario: u } = await iniciarSesionGoogle({ credential });
      navegar(panelSegunRol(u), { replace: true });
    } catch (err) {
      setErrorGeneral(err.message);
    } finally {
      setEnviando(false);
    }
  }

  return (
    <PantallaAuth titulo="Crea tu cuenta" subtitulo="Regístrate como cliente para reservar maquinaria.">
      <Alerta>{errorGeneral}</Alerta>
      <form
        ref={formulario}
        onSubmit={enviar}
        onPointerDownCapture={sincronizarAutocompletado}
        onKeyDownCapture={sincronizarAutocompletado}
        noValidate
      >
        <Campo etiqueta="Nombre completo" id="nombre" error={errorDe('nombre')} valido={esValido('nombre')}>
          <input
            id="nombre"
            name="nombre"
            placeholder="Ana Torres"
            value={datos.nombre}
            onChange={cambiar}
            onBlur={alSalir}
            autoComplete="name"
          />
        </Campo>
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
        <Campo
          etiqueta="Contraseña"
          id="contrasena"
          error={errorDe('contrasena')}
          valido={esValido('contrasena')}
          ayuda={<RequisitosContrasena valor={datos.contrasena} tocado={tocados.contrasena} />}
          mantenerAyuda
        >
          <CampoContrasena
            id="contrasena"
            name="contrasena"
            placeholder="••••••••"
            value={datos.contrasena}
            onChange={cambiar}
            onBlur={alSalir}
            autoComplete="new-password"
          />
        </Campo>
        <Campo etiqueta="Repite la contraseña" id="confirmar" error={errorDe('confirmar')} valido={esValido('confirmar')}>
          <CampoContrasena
            id="confirmar"
            name="confirmar"
            placeholder="••••••••"
            value={datos.confirmar}
            onChange={cambiar}
            onBlur={alSalir}
            autoComplete="new-password"
          />
        </Campo>
        <button
          type="submit"
          className="boton boton-primario boton-grande boton-bloque"
          disabled={enviando || hayErrores}
        >
          {enviando ? 'Creando cuenta…' : 'Crear cuenta'}
        </button>
      </form>
      <BotonGoogle texto="signup_with" alIngresar={registrarConGoogle} ocupado={enviando} />
      <p className="pie-formulario">
        ¿Ya tienes cuenta? <Link to="/login">Inicia sesión</Link>
      </p>
    </PantallaAuth>
  );
}
