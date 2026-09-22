// HU-02 Iniciar sesión
import { useState } from 'react';
import { Link, Navigate, useLocation, useNavigate } from 'react-router-dom';
import { useAuth, panelSegunRol } from '../contexto/AuthContext';
import { Alerta, Campo } from '../componentes/comunes';

export default function Login() {
  const { usuario, iniciarSesion, avisoSesion, limpiarAviso } = useAuth();
  const navegar = useNavigate();
  const { state } = useLocation();

  const [correo, setCorreo] = useState(state?.correo || '');
  const [contrasena, setContrasena] = useState('');
  const [recordarme, setRecordarme] = useState(false);
  const [error, setError] = useState('');
  const [enviando, setEnviando] = useState(false);

  // Si venía de una página protegida, vuelve a ella; si no, a su panel según el rol
  const destinoPara = (u) =>
    state?.desde && (u.rol === 'ADMINISTRADOR' || !state.desde.startsWith('/admin'))
      ? state.desde
      : panelSegunRol(u);

  if (usuario) return <Navigate to={destinoPara(usuario)} replace />;

  async function enviar(e) {
    e.preventDefault();
    if (!correo.trim() || !contrasena) {
      setError('Ingresa tu correo y contraseña');
      return;
    }
    setEnviando(true);
    setError('');
    limpiarAviso();
    try {
      const u = await iniciarSesion({ correo, contrasena, recordarme });
      navegar(destinoPara(u), { replace: true });
    } catch (err) {
      setError(err.message);
    } finally {
      setEnviando(false);
    }
  }

  return (
    <div className="contenedor contenedor-angosto">
      <div className="panel">
        <h1>Iniciar sesión</h1>
        <Alerta tipo="exito">{state?.mensaje}</Alerta>
        <Alerta tipo="aviso" alCerrar={limpiarAviso}>{avisoSesion}</Alerta>
        <Alerta>{error}</Alerta>
        <form onSubmit={enviar} noValidate>
          <Campo etiqueta="Correo electrónico" id="correo">
            <input id="correo" type="email" value={correo} onChange={(e) => setCorreo(e.target.value)} autoComplete="email" />
          </Campo>
          <Campo etiqueta="Contraseña" id="contrasena">
            <input id="contrasena" type="password" value={contrasena} onChange={(e) => setContrasena(e.target.value)} autoComplete="current-password" />
          </Campo>
          <label className="casilla">
            <input type="checkbox" checked={recordarme} onChange={(e) => setRecordarme(e.target.checked)} />
            Recordarme en este equipo
          </label>
          <button type="submit" className="boton boton-primario boton-bloque" disabled={enviando}>
            {enviando ? 'Ingresando…' : 'Ingresar'}
          </button>
        </form>
        <p className="pie-formulario">
          ¿No tienes cuenta? <Link to="/registro">Regístrate</Link>
        </p>
      </div>
    </div>
  );
}
