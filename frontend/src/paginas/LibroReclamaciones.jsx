import { useState } from 'react';
import { api } from '../api/cliente';
import { Alerta, Campo } from '../componentes/comunes';
import { CONTACTO, EMPRESA, NOMBRE_APP } from '../utils/formato';
import {
  limpiarDecimal,
  sinPuntoFinal,
  validarCelular,
  validarCorreo,
  validarDecimal,
  validarLinea,
  validarNombre,
  validarParrafo,
} from '../utils/validaciones';

const INICIAL = {
  tipo: 'RECLAMO',
  nombre: '',
  tipoDocumento: 'DNI',
  documento: '',
  domicilio: '',
  telefono: '',
  correo: '',
  tipoBien: 'SERVICIO',
  descripcionBien: '',
  monto: '',
  detalle: '',
  pedido: '',
};

function validarDocumento(tipo, valor) {
  const t = (valor || '').trim();
  if (!t) return 'El documento es obligatorio';
  if (tipo === 'DNI') return /^\d{8}$/.test(t) ? '' : 'El DNI debe tener 8 dígitos';
  return /^[A-Za-z0-9]{6,12}$/.test(t) ? '' : 'El documento debe tener entre 6 y 12 letras o números';
}

function validarTextoLargo(valor, etiqueta, max, mensaje) {
  if (!(valor || '').trim()) return mensaje;
  return validarParrafo(valor, etiqueta, max);
}

const REGLAS = {
  nombre: (d) => validarNombre(d.nombre),
  documento: (d) => validarDocumento(d.tipoDocumento, d.documento),
  domicilio: (d) =>
    validarLinea(d.domicilio, { etiqueta: 'El domicilio', min: 5, max: 200, obligatorio: 'El domicilio es obligatorio' }),
  telefono: (d) => validarCelular(d.telefono),
  correo: (d) => validarCorreo(d.correo),
  descripcionBien: (d) =>
    validarLinea(d.descripcionBien, {
      etiqueta: 'La descripción del bien',
      min: 3,
      max: 200,
      obligatorio: 'Indica el servicio o producto relacionado',
    }),
  monto: (d) =>
    validarDecimal(d.monto, { etiqueta: 'El monto', obligatorio: '', mayorQueCero: false, maximo: 1000000, maxDecimales: 2 }),
  detalle: (d) => validarTextoLargo(d.detalle, 'El detalle', 2000, 'El detalle es obligatorio'),
  pedido: (d) => validarTextoLargo(d.pedido, 'El pedido', 1000, 'El pedido del consumidor es obligatorio'),
};

function calcularErrores(datos) {
  const errores = {};
  Object.entries(REGLAS).forEach(([campo, regla]) => {
    const mensaje = regla(datos);
    if (mensaje) errores[campo] = mensaje;
  });
  return errores;
}

export default function LibroReclamaciones() {
  const [datos, setDatos] = useState(INICIAL);
  const [errores, setErrores] = useState({});
  const [errorGeneral, setErrorGeneral] = useState('');
  const [enviando, setEnviando] = useState(false);
  const [constancia, setConstancia] = useState(null);

  function cambiar(e) {
    const { name, value } = e.target;
    const nuevo = name === 'monto' ? limpiarDecimal(value) : value;
    setDatos((d) => ({ ...d, [name]: nuevo }));
    setErrores((prev) => {
      const resto = { ...prev };
      delete resto[name];
      if (name === 'tipoDocumento') delete resto.documento;
      return resto;
    });
  }

  async function enviar(e) {
    e.preventDefault();
    const encontrados = calcularErrores(datos);
    setErrores(encontrados);
    if (Object.keys(encontrados).length) {
      setErrorGeneral('Revisa los campos marcados');
      return;
    }
    setEnviando(true);
    setErrorGeneral('');
    try {
      const respuesta = await api.post('/reclamos', {
        ...datos,
        monto: sinPuntoFinal(datos.monto) || null,
      });
      setConstancia({ codigo: respuesta.codigo, tipo: datos.tipo, correo: datos.correo.trim() });
      window.scrollTo({ top: 0 });
    } catch (err) {
      setErrores(err.porCampo || {});
      setErrorGeneral(err.message);
    } finally {
      setEnviando(false);
    }
  }

  if (constancia) {
    return (
      <div className="contenedor" style={{ maxWidth: 760, paddingTop: 40 }}>
        <div className="panel">
          <h1>Libro de reclamaciones</h1>
          <Alerta tipo="exito">{`Tu ${constancia.tipo === 'QUEJA' ? 'queja' : 'reclamo'} fue registrado correctamente.`}</Alerta>
          <p>
            Código de tu hoja de reclamación: <strong>{constancia.codigo}</strong>
          </p>
          <p className="texto-suave">
            Guarda este código. Responderemos al correo {constancia.correo} en un plazo máximo de 15 días hábiles.
          </p>
          <button type="button" className="boton boton-secundario" onClick={() => window.print()}>
            Imprimir constancia
          </button>
        </div>
      </div>
    );
  }

  return (
    <div className="contenedor" style={{ maxWidth: 760, paddingTop: 40 }}>
      <div className="panel">
        <h1>Libro de reclamaciones</h1>
        <p className="texto-suave">
          Conforme al Código de Protección y Defensa del Consumidor, puedes registrar aquí tu reclamo o queja.
        </p>
        <p className="texto-suave" style={{ marginBottom: 0 }}>
          <strong>{NOMBRE_APP}</strong> · {EMPRESA.razonSocial} · RUC {EMPRESA.ruc} · {CONTACTO.direccion}
        </p>
      </div>

      <form onSubmit={enviar} noValidate className="panel">
        <Alerta>{errorGeneral}</Alerta>

        <h2>1. Identificación del consumidor</h2>
        <Campo etiqueta="Nombre completo" id="nombre" error={errores.nombre}>
          <input id="nombre" name="nombre" value={datos.nombre} onChange={cambiar} maxLength={120} autoComplete="name" />
        </Campo>
        <Campo etiqueta="Tipo de documento" id="tipoDocumento">
          <select id="tipoDocumento" name="tipoDocumento" value={datos.tipoDocumento} onChange={cambiar}>
            <option value="DNI">DNI</option>
            <option value="CE">Carné de extranjería</option>
            <option value="PASAPORTE">Pasaporte</option>
          </select>
        </Campo>
        <Campo etiqueta="Número de documento" id="documento" error={errores.documento}>
          <input id="documento" name="documento" value={datos.documento} onChange={cambiar} maxLength={12} />
        </Campo>
        <Campo etiqueta="Domicilio" id="domicilio" error={errores.domicilio}>
          <input id="domicilio" name="domicilio" value={datos.domicilio} onChange={cambiar} maxLength={200} autoComplete="street-address" />
        </Campo>
        <Campo etiqueta="Celular" id="telefono" error={errores.telefono}>
          <input id="telefono" name="telefono" value={datos.telefono} onChange={cambiar} inputMode="tel" autoComplete="tel" />
        </Campo>
        <Campo etiqueta="Correo electrónico" id="correo" error={errores.correo}>
          <input id="correo" name="correo" type="email" value={datos.correo} onChange={cambiar} maxLength={160} autoComplete="email" />
        </Campo>

        <h2>2. Identificación del bien contratado</h2>
        <Campo etiqueta="Tipo" id="tipoBien">
          <select id="tipoBien" name="tipoBien" value={datos.tipoBien} onChange={cambiar}>
            <option value="SERVICIO">Servicio</option>
            <option value="PRODUCTO">Producto</option>
          </select>
        </Campo>
        <Campo
          etiqueta="Descripción"
          id="descripcionBien"
          error={errores.descripcionBien}
          ayuda="Por ejemplo: alquiler de excavadora por horas"
        >
          <input id="descripcionBien" name="descripcionBien" value={datos.descripcionBien} onChange={cambiar} maxLength={200} />
        </Campo>
        <Campo etiqueta="Monto reclamado (S/, opcional)" id="monto" error={errores.monto}>
          <input id="monto" name="monto" value={datos.monto} onChange={cambiar} inputMode="decimal" />
        </Campo>

        <h2>3. Detalle del reclamo o queja</h2>
        <Campo
          etiqueta="Tipo"
          id="tipo"
          ayuda="Reclamo: disconformidad con el servicio. Queja: malestar por la atención recibida."
          mantenerAyuda
        >
          <select id="tipo" name="tipo" value={datos.tipo} onChange={cambiar}>
            <option value="RECLAMO">Reclamo</option>
            <option value="QUEJA">Queja</option>
          </select>
        </Campo>
        <Campo etiqueta="Detalle" id="detalle" error={errores.detalle}>
          <textarea id="detalle" name="detalle" rows={5} value={datos.detalle} onChange={cambiar} maxLength={2000} />
        </Campo>
        <Campo etiqueta="Pedido del consumidor" id="pedido" error={errores.pedido}>
          <textarea id="pedido" name="pedido" rows={3} value={datos.pedido} onChange={cambiar} maxLength={1000} />
        </Campo>

        <button type="submit" className="boton boton-primario boton-grande" disabled={enviando}>
          {enviando ? 'Enviando…' : 'Enviar'}
        </button>
      </form>
    </div>
  );
}
