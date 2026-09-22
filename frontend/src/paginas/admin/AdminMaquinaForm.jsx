// HU-08 Registrar y publicar máquina (crear, editar, fotos, publicar y retirar)
import { useEffect, useRef, useState } from 'react';
import { Link, useLocation, useNavigate, useParams } from 'react-router-dom';
import { api } from '../../api/cliente';
import { Alerta, Campo, Cargando, FotoMaquina } from '../../componentes/comunes';
import { ETIQUETA_ESTADO } from '../../utils/formato';

const MAX_FOTOS = 5;
const TIPOS_PERMITIDOS = ['image/jpeg', 'image/png'];
const VACIO = {
  nombre: '',
  categoriaId: '',
  marca: '',
  modelo: '',
  tarifaDiaria: '',
  ubicacion: '',
  descripcion: '',
  enMantenimiento: false,
};

function validar(f) {
  const e = {};
  if (!f.nombre.trim()) e.nombre = 'El nombre es obligatorio';
  if (!f.categoriaId) e.categoriaId = 'Selecciona una categoría';
  if (!f.marca.trim()) e.marca = 'La marca es obligatoria';
  if (!f.modelo.trim()) e.modelo = 'El modelo es obligatorio';
  if (!(Number(f.tarifaDiaria) > 0)) e.tarifaDiaria = 'Ingresa una tarifa mayor a 0';
  if (!f.ubicacion.trim()) e.ubicacion = 'La ubicación es obligatoria';
  return e;
}

export default function AdminMaquinaForm() {
  const { id } = useParams();
  const esNueva = !id;
  const navegar = useNavigate();
  const { state } = useLocation();
  const entradaFotos = useRef(null);

  const [categorias, setCategorias] = useState([]);
  const [maquina, setMaquina] = useState(null);
  const [form, setForm] = useState(VACIO);
  const [specs, setSpecs] = useState([{ clave: '', valor: '' }]);
  const [errores, setErrores] = useState({});
  const [error, setError] = useState('');
  const [exito, setExito] = useState(state?.mensaje || '');
  const [ocupado, setOcupado] = useState(false);

  useEffect(() => {
    api.get('/admin/categorias').then((r) => setCategorias(r.datos)).catch((e) => setError(e.message));
  }, []);

  useEffect(() => {
    if (esNueva) {
      setMaquina(null);
      setForm(VACIO);
      setSpecs([{ clave: '', valor: '' }]);
      return;
    }
    api
      .get(`/admin/maquinas/${id}`)
      .then(cargarEnFormulario)
      .catch((e) => setError(e.message));
  }, [id, esNueva]);

  function cargarEnFormulario(m) {
    setMaquina(m);
    setForm({
      nombre: m.nombre,
      categoriaId: String(m.categoria.id),
      marca: m.marca,
      modelo: m.modelo,
      tarifaDiaria: String(m.tarifaDiaria),
      ubicacion: m.ubicacion,
      descripcion: m.descripcion || '',
      enMantenimiento: m.enMantenimiento,
    });
    const filas = Object.entries(m.especificaciones || {}).map(([clave, valor]) => ({ clave, valor }));
    setSpecs(filas.length ? filas : [{ clave: '', valor: '' }]);
  }

  const cambiar = (e) => {
    const { name, value, type, checked } = e.target;
    setForm({ ...form, [name]: type === 'checkbox' ? checked : value });
    setErrores({ ...errores, [name]: undefined });
  };

  const cambiarSpec = (i, campo, valor) => setSpecs(specs.map((s, j) => (j === i ? { ...s, [campo]: valor } : s)));

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
      if (e.porCampo) setErrores(e.porCampo);
      return null;
    } finally {
      setOcupado(false);
    }
  }

  async function guardar(e) {
    e.preventDefault();
    const encontrados = validar(form);
    setErrores(encontrados);
    if (Object.keys(encontrados).length) return;

    const especificaciones = Object.fromEntries(
      specs.filter((s) => s.clave.trim()).map((s) => [s.clave.trim(), s.valor.trim()])
    );
    const cuerpo = {
      ...form,
      categoriaId: Number(form.categoriaId),
      tarifaDiaria: Number(form.tarifaDiaria),
      descripcion: form.descripcion.trim() || null,
      especificaciones,
    };

    if (esNueva) {
      const creada = await accion(() => api.post('/admin/maquinas', cuerpo));
      if (creada) {
        setExito('Máquina guardada como borrador. Ahora sube sus fotos para poder publicarla');
        navegar(`/admin/maquinas/${creada.id}`, {
          replace: true,
          state: { mensaje: 'Máquina guardada como borrador. Ahora sube sus fotos para poder publicarla' },
        });
      }
    } else {
      const actualizada = await accion(() => api.put(`/admin/maquinas/${id}`, cuerpo), 'Cambios guardados');
      if (actualizada) cargarEnFormulario(actualizada);
    }
  }

  async function subirFotos(e) {
    const archivos = Array.from(e.target.files || []);
    e.target.value = '';
    if (!archivos.length) return;

    const disponibles = MAX_FOTOS - maquina.fotos.length;
    if (archivos.length > disponibles) {
      setError(disponibles > 0 ? `Solo puedes subir ${disponibles} foto(s) más (máximo ${MAX_FOTOS})` : `Ya tiene el máximo de ${MAX_FOTOS} fotos`);
      return;
    }
    const invalida = archivos.find((a) => !TIPOS_PERMITIDOS.includes(a.type));
    if (invalida) {
      setError(`"${invalida.name}" no es JPG ni PNG`);
      return;
    }

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
    if (r) setMaquina({ ...maquina, fotos: r.fotos });
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
          <Campo etiqueta="Nombre *" id="nombre" error={errores.nombre}>
            <input id="nombre" name="nombre" value={form.nombre} onChange={cambiar} maxLength={120} />
          </Campo>
          <Campo etiqueta="Categoría *" id="categoriaId" error={errores.categoriaId}>
            <select id="categoriaId" name="categoriaId" value={form.categoriaId} onChange={cambiar}>
              <option value="">Selecciona…</option>
              {categoriasSeleccionables.map((c) => (
                <option key={c.id} value={c.id}>
                  {c.nombre}{c.activa ? '' : ' (inactiva)'}
                </option>
              ))}
            </select>
          </Campo>
          <Campo etiqueta="Marca *" id="marca" error={errores.marca}>
            <input id="marca" name="marca" value={form.marca} onChange={cambiar} maxLength={80} />
          </Campo>
          <Campo etiqueta="Modelo *" id="modelo" error={errores.modelo}>
            <input id="modelo" name="modelo" value={form.modelo} onChange={cambiar} maxLength={80} />
          </Campo>
          <Campo etiqueta="Tarifa diaria (S/) *" id="tarifaDiaria" error={errores.tarifaDiaria}>
            <input id="tarifaDiaria" name="tarifaDiaria" type="number" min="0.01" step="0.01" value={form.tarifaDiaria} onChange={cambiar} />
          </Campo>
          <Campo etiqueta="Ubicación *" id="ubicacion" error={errores.ubicacion} ayuda="Ciudad o sede donde se recoge">
            <input id="ubicacion" name="ubicacion" value={form.ubicacion} onChange={cambiar} maxLength={160} />
          </Campo>
        </div>
        <Campo etiqueta="Descripción" id="descripcion" error={errores.descripcion}>
          <textarea id="descripcion" name="descripcion" rows={3} value={form.descripcion} onChange={cambiar} maxLength={2000} />
        </Campo>

        <fieldset className="especificaciones">
          <legend>Especificaciones técnicas</legend>
          {specs.map((s, i) => (
            <div className="fila-spec" key={i}>
              <input placeholder="Ej. Potencia" value={s.clave} onChange={(e) => cambiarSpec(i, 'clave', e.target.value)} maxLength={60} aria-label="Nombre de la especificación" />
              <input placeholder="Ej. 146 HP" value={s.valor} onChange={(e) => cambiarSpec(i, 'valor', e.target.value)} maxLength={200} aria-label="Valor" />
              <button type="button" className="boton boton-secundario boton-chico" onClick={() => setSpecs(specs.length > 1 ? specs.filter((_, j) => j !== i) : [{ clave: '', valor: '' }])} aria-label="Quitar">
                ×
              </button>
            </div>
          ))}
          <button type="button" className="boton boton-secundario boton-chico" onClick={() => setSpecs([...specs, { clave: '', valor: '' }])}>
            + Agregar especificación
          </button>
        </fieldset>

        <label className="casilla">
          <input type="checkbox" name="enMantenimiento" checked={form.enMantenimiento} onChange={cambiar} />
          En mantenimiento (los clientes verán un aviso en lugar del detalle)
        </label>

        <div className="acciones-form">
          <button type="submit" className="boton boton-primario" disabled={ocupado}>
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
            <p className="texto-suave">JPG o PNG, hasta 5 MB cada una. La foto marcada con ★ es la principal del catálogo.</p>
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
