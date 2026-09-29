// HU-08 Registrar y publicar máquina (crear, editar, fotos, publicar y retirar)
import { useEffect, useRef, useState } from 'react';
import { Link, useLocation, useNavigate, useParams } from 'react-router-dom';
import { api } from '../../api/cliente';
import { Alerta, Campo, Cargando, FotoMaquina } from '../../componentes/comunes';
import { ETIQUETA_ESTADO, formatearHoras } from '../../utils/formato';
import {
  MAX_FOTOS,
  MAX_MB_FOTO,
  erroresEspecificaciones,
  especificacionesComoObjeto,
  limpiarDecimal,
  validarDescripcionMaquina,
  validarFotos,
  validarHorometro,
  validarMarca,
  validarModelo,
  validarNombreMaquina,
  validarTarifa,
  validarUbicacion,
} from '../../utils/validaciones';
import { useValidacion } from '../../utils/useValidacion';

const VACIO = {
  nombre: '',
  categoriaId: '',
  marca: '',
  modelo: '',
  tarifaHoraria: '',
  ubicacion: '',
  descripcion: '',
  enMantenimiento: false,
  horometroInicial: '',
};
const FILA_VACIA = { clave: '', valor: '' };
const CAMPOS_DECIMALES = ['tarifaHoraria', 'horometroInicial'];

const REGLAS = {
  categoriaId: (v) => (v ? '' : 'Selecciona una categoría'),
  nombre: validarNombreMaquina,
  marca: validarMarca,
  modelo: validarModelo,
  tarifaHoraria: validarTarifa,
  ubicacion: validarUbicacion,
  horometroInicial: validarHorometro,
  descripcion: validarDescripcionMaquina,
};

export default function AdminMaquinaForm() {
  const { id } = useParams();
  const esNueva = !id;
  const navegar = useNavigate();
  const { state } = useLocation();
  const entradaFotos = useRef(null);

  const [categorias, setCategorias] = useState([]);
  const [maquina, setMaquina] = useState(null);
  const [form, setForm] = useState(VACIO);
  // Valores con los que se cargó la máquina: al editar solo se validan y envían los campos que cambian,
  // así una máquina antigua con datos fuera de las reglas actuales se puede seguir editando
  const [original, setOriginal] = useState(null);
  const [specs, setSpecs] = useState([FILA_VACIA]);
  const [specsOriginales, setSpecsOriginales] = useState('{}');
  const [error, setError] = useState('');
  const [errorFotos, setErrorFotos] = useState('');
  const [exito, setExito] = useState(state?.mensaje || '');
  const [ocupado, setOcupado] = useState(false);

  const reglas = Object.fromEntries(
    Object.entries(REGLAS).map(([campo, regla]) => [
      campo,
      (valor, datos) => (original && valor === original[campo] ? '' : regla(valor, datos)),
    ])
  );
  const v = useValidacion(reglas, form);

  const especificaciones = especificacionesComoObjeto(specs);
  const specsCambiaron = JSON.stringify(especificaciones) !== specsOriginales;
  const erroresSpecs = original && !specsCambiaron ? { porFila: [], general: '', hayErrores: false } : erroresEspecificaciones(specs);
  const hayErrores = v.hayErrores || erroresSpecs.hayErrores;

  useEffect(() => {
    api.get('/admin/categorias').then((r) => setCategorias(r.datos)).catch((e) => setError(e.message));
  }, []);

  useEffect(() => {
    if (esNueva) {
      setMaquina(null);
      setOriginal(null);
      setForm(VACIO);
      setSpecs([FILA_VACIA]);
      setSpecsOriginales('{}');
      v.reiniciar();
      return;
    }
    api
      .get(`/admin/maquinas/${id}`)
      .then(cargarEnFormulario)
      .catch((e) => setError(e.message));
    // v.reiniciar solo cambia estado interno del hook
  }, [id, esNueva]);

  function cargarEnFormulario(m) {
    const datos = {
      nombre: m.nombre,
      categoriaId: String(m.categoria.id),
      marca: m.marca,
      modelo: m.modelo,
      tarifaHoraria: String(m.tarifaHoraria),
      ubicacion: m.ubicacion,
      descripcion: m.descripcion || '',
      enMantenimiento: m.enMantenimiento,
      horometroInicial: Number(m.horometroInicial) ? String(m.horometroInicial) : '',
    };
    setMaquina(m);
    setForm(datos);
    setOriginal(datos);
    const filas = Object.entries(m.especificaciones || {}).map(([clave, valor]) => ({ clave, valor }));
    setSpecs(filas.length ? filas : [FILA_VACIA]);
    setSpecsOriginales(JSON.stringify(especificacionesComoObjeto(filas)));
    v.reiniciar();
  }

  const cambiar = (e) => {
    const { name, value, type, checked } = e.target;
    const valor = type === 'checkbox' ? checked : CAMPOS_DECIMALES.includes(name) ? limpiarDecimal(value) : value;
    setForm({ ...form, [name]: valor });
    v.editado(name);
  };
  const alSalir = (e) => v.tocar(e.target.name);

  const cambiarSpecs = (nuevas) => {
    setSpecs(nuevas);
    v.editado('especificaciones'); // olvida el error del servidor en las especificaciones
  };
  const cambiarSpec = (i, campo, valor) => cambiarSpecs(specs.map((s, j) => (j === i ? { ...s, [campo]: valor } : s)));

  async function accion(fn, mensajeExito) {
    setError('');
    setExito('');
    setOcupado(true);
    try {
      const r = await fn();
      if (mensajeExito) setExito(mensajeExito);
      return r;
    } catch (e) {
      setError(e.message);
      v.setErroresServidor(e.porCampo || {});
      return null;
    } finally {
      setOcupado(false);
    }
  }

  async function guardar(e) {
    e.preventDefault();
    if (hayErrores) return v.tocarTodos();

    const completo = {
      ...form,
      categoriaId: Number(form.categoriaId),
      tarifaHoraria: form.tarifaHoraria.trim(),
      horometroInicial: form.horometroInicial.trim() || 0,
      descripcion: form.descripcion.trim() || null,
      especificaciones,
    };

    if (esNueva) {
      const creada = await accion(() => api.post('/admin/maquinas', completo));
      if (creada) {
        navegar(`/admin/maquinas/${creada.id}`, {
          replace: true,
          state: { mensaje: 'Máquina guardada como borrador. Ahora sube sus fotos para poder publicarla' },
        });
      }
      return;
    }

    // Edición: solo lo que cambió
    const cuerpo = Object.fromEntries(Object.keys(VACIO).filter((c) => form[c] !== original[c]).map((c) => [c, completo[c]]));
    if (specsCambiaron) cuerpo.especificaciones = especificaciones;
    if (!Object.keys(cuerpo).length) {
      setError('');
      setExito('No hay cambios para guardar');
      return;
    }
    const actualizada = await accion(() => api.put(`/admin/maquinas/${id}`, cuerpo), 'Cambios guardados');
    if (actualizada) cargarEnFormulario(actualizada);
  }

  async function subirFotos(e) {
    const archivos = Array.from(e.target.files || []);
    e.target.value = '';
    if (!archivos.length) return;

    // Se avisa al elegir el archivo, antes de subir nada
    const mensaje = validarFotos(archivos, maquina.fotos.length);
    setErrorFotos(mensaje);
    if (mensaje) return;

    const datos = new FormData();
    archivos.forEach((a) => datos.append('fotos', a));
    const r = await accion(() => api.subir(`/admin/maquinas/${id}/fotos`, datos), 'Fotos subidas');
    if (r) setMaquina({ ...maquina, fotos: r.fotos });
  }

  async function marcarPrincipal(fotoId) {
    const r = await accion(() => api.patch(`/admin/maquinas/${id}/fotos/${fotoId}/principal`), 'Foto principal actualizada');
    if (r) setMaquina({ ...maquina, fotos: r.fotos });
  }

  async function eliminarFoto(fotoId) {
    if (!window.confirm('¿Eliminar esta foto?')) return;
    const r = await accion(() => api.delete(`/admin/maquinas/${id}/fotos/${fotoId}`), 'Foto eliminada');
    if (r) {
      setMaquina({ ...maquina, fotos: r.fotos });
      setErrorFotos('');
    }
  }

  async function cambiarPublicacion(tipo) {
    const mensajes = {
      publicar: '¡Publicada! Ya aparece en el catálogo',
      retirar: 'Retirada del catálogo. Su historial de reservas se conserva',
    };
    const r = await accion(() => api.post(`/admin/maquinas/${id}/${tipo}`), mensajes[tipo]);
    if (r) cargarEnFormulario(r);
  }

  async function eliminarBorrador() {
    if (!window.confirm('¿Eliminar este borrador? Esta acción no se puede deshacer.')) return;
    const r = await accion(() => api.delete(`/admin/maquinas/${id}`).then(() => true));
    if (r) navegar('/admin/maquinas', { replace: true });
  }

  if (!esNueva && !maquina && !error) return <Cargando />;

  const categoriasSeleccionables = categorias.filter((c) => c.activa || String(c.id) === form.categoriaId);
  const tienePrincipal = maquina?.fotos.some((f) => f.esPrincipal);
  const errorSpecsGeneral = erroresSpecs.general || v.errorDe('especificaciones');

  return (
    <>
      <Link to="/admin/maquinas" className="enlace-volver">← Volver a máquinas</Link>
      <div className="cabecera-seccion">
        <h1>{esNueva ? 'Registrar máquina' : maquina?.nombre}</h1>
        {maquina && (
          <span className={`insignia estado-${maquina.estado.toLowerCase()}`}>{ETIQUETA_ESTADO[maquina.estado]}</span>
        )}
      </div>
      <Alerta alCerrar={() => setError('')}>{error}</Alerta>
      <Alerta tipo="exito" alCerrar={() => setExito('')}>{exito}</Alerta>

      {/* ------------ Publicación ------------ */}
      {maquina && (
        <div className="panel barra-publicacion">
          {maquina.estado === 'PUBLICADA' ? (
            <>
              <p>
                Visible en el catálogo. <Link to={`/maquinas/${maquina.id}`} target="_blank">Ver como cliente ↗</Link>
              </p>
              <button type="button" className="boton boton-secundario" onClick={() => cambiarPublicacion('retirar')} disabled={ocupado}>
                Retirar del catálogo
              </button>
            </>
          ) : (
            <>
              <p>
                {maquina.estado === 'BORRADOR' ? 'Borrador: aún no la ven los clientes.' : 'Retirada: no aparece en el catálogo.'}
                {!tienePrincipal && ' Para publicarla, sube al menos una foto.'}
              </p>
              <div className="acciones">
                {maquina.estado === 'BORRADOR' && (
                  <button type="button" className="boton boton-peligro" onClick={eliminarBorrador} disabled={ocupado}>
                    Eliminar borrador
                  </button>
                )}
                <button type="button" className="boton boton-primario" onClick={() => cambiarPublicacion('publicar')} disabled={ocupado || !tienePrincipal}>
                  Publicar en el catálogo
                </button>
              </div>
            </>
          )}
        </div>
      )}

      {/* ------------ Datos ------------ */}
      <form className="panel" onSubmit={guardar} noValidate>
        <h2 className="subtitulo">Datos de la máquina</h2>
        <div className="rejilla-form">
          <Campo etiqueta="Nombre *" id="nombre" error={v.errorDe('nombre')} ayuda="Letras, números, espacios y - . / + ( )">
            <input id="nombre" name="nombre" value={form.nombre} onChange={cambiar} onBlur={alSalir} />
          </Campo>
          <Campo etiqueta="Categoría *" id="categoriaId" error={v.errorDe('categoriaId')}>
            <select id="categoriaId" name="categoriaId" value={form.categoriaId} onChange={cambiar} onBlur={alSalir}>
              <option value="">Selecciona…</option>
              {categoriasSeleccionables.map((c) => (
                <option key={c.id} value={c.id}>
                  {c.nombre}{c.activa ? '' : ' (inactiva)'}
                </option>
              ))}
            </select>
          </Campo>
          <Campo etiqueta="Marca *" id="marca" error={v.errorDe('marca')}>
            <input id="marca" name="marca" value={form.marca} onChange={cambiar} onBlur={alSalir} />
          </Campo>
          <Campo etiqueta="Modelo *" id="modelo" error={v.errorDe('modelo')}>
            <input id="modelo" name="modelo" value={form.modelo} onChange={cambiar} onBlur={alSalir} />
          </Campo>
          <Campo etiqueta="Tarifa por hora (S/) *" id="tarifaHoraria" error={v.errorDe('tarifaHoraria')} ayuda="Mayor que 0, hasta 2 decimales">
            <input
              id="tarifaHoraria"
              name="tarifaHoraria"
              inputMode="decimal"
              placeholder="0.00"
              value={form.tarifaHoraria}
              onChange={cambiar}
              onBlur={alSalir}
            />
          </Campo>
          <Campo etiqueta="Ubicación *" id="ubicacion" error={v.errorDe('ubicacion')} ayuda="Ciudad o sede donde se recoge">
            <input id="ubicacion" name="ubicacion" value={form.ubicacion} onChange={cambiar} onBlur={alSalir} />
          </Campo>
          <Campo
            etiqueta="Horómetro inicial (horas de uso si la máquina es usada)"
            id="horometroInicial"
            error={v.errorDe('horometroInicial')}
            ayuda={
              maquina
                ? `Horas de uso actuales: ${formatearHoras(maquina.horasUso)} (este valor + horas de reservas finalizadas)`
                : 'Entre 0 y 999999.9, con 1 decimal como máximo. Déjalo vacío si la máquina es nueva'
            }
          >
            <input
              id="horometroInicial"
              name="horometroInicial"
              inputMode="decimal"
              placeholder="0"
              value={form.horometroInicial}
              onChange={cambiar}
              onBlur={alSalir}
            />
          </Campo>
        </div>
        <Campo etiqueta="Descripción" id="descripcion" error={v.errorDe('descripcion')} ayuda="Hasta 2000 caracteres">
          <textarea id="descripcion" name="descripcion" rows={3} value={form.descripcion} onChange={cambiar} onBlur={alSalir} />
        </Campo>

        <fieldset className="especificaciones" aria-describedby={errorSpecsGeneral ? 'especificaciones-mensaje' : undefined}>
          <legend>Especificaciones técnicas</legend>
          {specs.map((s, i) => {
            const errorFila = erroresSpecs.porFila[i];
            const idMensaje = `spec-${i}-mensaje`;
            const aria = { 'aria-invalid': Boolean(errorFila), 'aria-describedby': errorFila ? idMensaje : undefined };
            return (
              <div key={i} className={errorFila ? 'campo-error' : ''}>
                <div className="fila-spec">
                  <input placeholder="Ej. Potencia" value={s.clave} onChange={(e) => cambiarSpec(i, 'clave', e.target.value)} aria-label="Nombre de la especificación" {...aria} />
                  <input placeholder="Ej. 146 HP" value={s.valor} onChange={(e) => cambiarSpec(i, 'valor', e.target.value)} aria-label="Valor" {...aria} />
                  <button type="button" className="boton boton-secundario boton-chico" onClick={() => cambiarSpecs(specs.length > 1 ? specs.filter((_, j) => j !== i) : [FILA_VACIA])} aria-label="Quitar">
                    ×
                  </button>
                </div>
                {errorFila && <small className="mensaje-error mensaje-fila" id={idMensaje}>{errorFila}</small>}
              </div>
            );
          })}
          {errorSpecsGeneral && <p className="mensaje-error" id="especificaciones-mensaje">{errorSpecsGeneral}</p>}
          <button type="button" className="boton boton-secundario boton-chico" onClick={() => cambiarSpecs([...specs, FILA_VACIA])} disabled={specs.length >= 30}>
            + Agregar especificación
          </button>
        </fieldset>

        <label className="casilla">
          <input type="checkbox" name="enMantenimiento" checked={form.enMantenimiento} onChange={cambiar} />
          En mantenimiento (los clientes verán un aviso en lugar del detalle)
        </label>

        <div className="acciones-form">
          <button type="submit" className="boton boton-primario" disabled={ocupado || hayErrores}>
            {esNueva ? 'Guardar como borrador' : 'Guardar cambios'}
          </button>
        </div>
      </form>

      {/* ------------ Fotos ------------ */}
      <div className="panel">
        <h2 className="subtitulo">
          Fotos {maquina && <span className="texto-suave">({maquina.fotos.length}/{MAX_FOTOS})</span>}
        </h2>
        {!maquina ? (
          <p className="texto-suave">Guarda la máquina primero para poder subir sus fotos.</p>
        ) : (
          <>
            <p className="texto-suave">
              JPG o PNG, hasta {MAX_MB_FOTO} MB cada una. La foto marcada con ★ es la principal del catálogo.
            </p>
            {errorFotos && <p className="mensaje-error" role="alert">{errorFotos}</p>}
            <div className="rejilla-fotos">
              {maquina.fotos.map((f) => (
                <div key={f.id} className={`foto-admin ${f.esPrincipal ? 'principal' : ''}`}>
                  <FotoMaquina ruta={f.url} alt="" />
                  <div className="foto-acciones">
                    {f.esPrincipal ? (
                      <span className="insignia insignia-exito">★ Principal</span>
                    ) : (
                      <button type="button" className="boton boton-secundario boton-chico" onClick={() => marcarPrincipal(f.id)} disabled={ocupado}>
                        ☆ Principal
                      </button>
                    )}
                    <button type="button" className="boton boton-peligro boton-chico" onClick={() => eliminarFoto(f.id)} disabled={ocupado} aria-label="Eliminar foto">
                      Eliminar
                    </button>
                  </div>
                </div>
              ))}
              {maquina.fotos.length < MAX_FOTOS && (
                <button type="button" className="foto-agregar" onClick={() => entradaFotos.current?.click()} disabled={ocupado}>
                  <span>+</span>
                  Subir fotos
                </button>
              )}
            </div>
            <input ref={entradaFotos} type="file" accept="image/jpeg,image/png" multiple hidden onChange={subirFotos} />
          </>
        )}
      </div>
    </>
  );
}
