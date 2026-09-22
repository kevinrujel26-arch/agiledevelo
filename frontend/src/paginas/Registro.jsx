// HU-01 Registrar cliente
import { useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { api } from '../api/cliente';
import { Alerta, Campo } from '../componentes/comunes';
import { CORREO_VALIDO } from '../utils/formato';

function validar({ nombre, correo, contrasena, confirmar }) {
  const e = {};
  if (!nombre.trim()) e.nombre = 'El nombre es obligatorio';
  if (!correo.trim()) e.correo = 'El correo es obligatorio';
  else if (!CORREO_VALIDO.test(correo.trim())) e.correo = 'El correo no tiene un formato válido';
  if (!contrasena) e.contrasena = 'La contraseña es obligatoria';
  else if (contrasena.length < 8) e.contrasena = 'La contraseña debe tener al menos 8 caracteres';
  if (confirmar !== contrasena) e.confirmar = 'Las contraseñas no coinciden';
  return e;
}

export default function Registro() {
  const navegar = useNavigate();
  const [datos, setDatos] = useState({ nombre: '', correo: '', contrasena: '', confirmar: '' });
  const [errores, setErrores] = useState({});
  const [errorGeneral, setErrorGeneral] = useState('');
  const [enviando, setEnviando] = useState(false);

  const cambiar = (e) => {
    setDatos({ ...datos, [e.target.name]: e.target.value });
    setErrores({ ...errores, [e.target.name]: undefined });
  };

  async function enviar(e) {
    e.preventDefault();
    const encontrados = validar(datos);
    setErrores(encontrados);
    setErrorGeneral('');
    if (Object.keys(encontrados).length) return;

    setEnviando(true);
    try {
      const r = await api.post('/auth/registro', {
        nombre: datos.nombre,
        correo: datos.correo,
        contrasena: datos.contrasena,
      });
      // HU-01 criterio 6: mensaje de confirmación
      navegar('/login', { state: { mensaje: r.mensaje, correo: r.usuario.correo } });
    } catch (err) {
      setErrores(err.porCampo || {});
      setErrorGeneral(err.message);
    } finally {
      setEnviando(false);
    }
  }

  return (
    <div className="contenedor contenedor-angosto">
      <div className="panel">
        <h1>Crear cuenta</h1>
        <p className="texto-suave">Regístrate como cliente para reservar maquinaria.</p>
        <Alerta>{errorGeneral}</Alerta>
        <form onSubmit={enviar} noValidate>
          <Campo etiqueta="Nombre completo" id="nombre" error={errores.nombre}>
            <input id="nombre" name="nombre" value={datos.nombre} onChange={cambiar} autoComplete="name" />
          </Campo>
          <Campo etiqueta="Correo electrónico" id="correo" error={errores.correo}>
            <input id="correo" name="correo" type="email" value={datos.correo} onChange={cambiar} autoComplete="email" />
          </Campo>
          <Campo etiqueta="Contraseña" id="contrasena" error={errores.contrasena} ayuda="Mínimo 8 caracteres">
            <input id="contrasena" name="contrasena" type="password" value={datos.contrasena} onChange={cambiar} autoComplete="new-password" />
          </Campo>
          <Campo etiqueta="Repite la contraseña" id="confirmar" error={errores.confirmar}>
            <input id="confirmar" name="confirmar" type="password" value={datos.confirmar} onChange={cambiar} autoComplete="new-password" />
          </Campo>
          <button type="submit" className="boton boton-primario boton-bloque" disabled={enviando}>
            {enviando ? 'Registrando…' : 'Registrarme'}
          </button>
        </form>
        <p className="pie-formulario">
          ¿Ya tienes cuenta? <Link to="/login">Inicia sesión</Link>
        </p>
      </div>
    </div>
  );
}
