// HU-14 Gestionar categorías de maquinaria
import { useCallback, useEffect, useState } from 'react';
import { api } from '../../api/cliente';
import { Alerta, Campo, Cargando } from '../../componentes/comunes';
import { normalizarTexto, validarDescripcionCategoria, validarNombreCategoria } from '../../utils/validaciones';
import { useValidacion } from '../../utils/useValidacion';

const NUEVA_VACIA = { nombre: '', descripcion: '' };

export default function AdminCategorias() {
  const [categorias, setCategorias] = useState(null);
  const [nueva, setNueva] = useState(NUEVA_VACIA);
  const [edicion, setEdicion] = useState(null); // { id, nombre, descripcion, nombreOriginal }
  const [error, setError] = useState('');
  const [exito, setExito] = useState('');

  // Reglas en vivo; el nombre también avisa si ya existe otra categoría igual
  const vNueva = useValidacion(
    {
      nombre: (v) => validarNombreCategoria(v, categorias || []),
      descripcion: validarDescripcionCategoria,
    },
    nueva
  );
  // Al editar, el nombre solo se valida si cambió: una categoría antigua con un nombre
  // fuera de las reglas actuales se puede seguir editando sin renombrarla
  const nombreCambiado = edicion && normalizarTexto(edicion.nombre) !== normalizarTexto(edicion.nombreOriginal);
  const vEdicion = useValidacion(
    {
      nombre: (v) => (nombreCambiado ? validarNombreCategoria(v, categorias || [], edicion?.id) : ''),
      descripcion: validarDescripcionCategoria,
    },
    edicion || NUEVA_VACIA
  );

  const cargar = useCallback(() => {
    api.get('/admin/categorias').then((r) => setCategorias(r.datos)).catch((e) => setError(e.message));
  }, []);
  useEffect(cargar, [cargar]);

  async function ejecutar(accion, mensajeExito, validacion) {
    setError('');
    setExito('');
    try {
      await accion();
      setExito(mensajeExito);
      cargar();
      return true;
    } catch (e) {
      setError(e.message);
      validacion?.setErroresServidor(e.porCampo || {});
      return false;
    }
  }

  const cambiarNueva = (e) => {
    setNueva({ ...nueva, [e.target.name]: e.target.value });
    vNueva.editado(e.target.name);
  };

  async function crear(e) {
    e.preventDefault();
    if (vNueva.hayErrores) return vNueva.tocarTodos();
    const ok = await ejecutar(
      () => api.post('/admin/categorias', nueva),
      `Categoría "${normalizarTexto(nueva.nombre)}" creada`,
      vNueva
    );
    if (ok) {
      setNueva(NUEVA_VACIA);
      vNueva.reiniciar();
    }
  }

  const empezarEdicion = (c) => {
    setEdicion({ id: c.id, nombre: c.nombre, descripcion: c.descripcion || '', nombreOriginal: c.nombre });
    vEdicion.reiniciar();
  };

  const cambiarEdicion = (e) => {
    setEdicion({ ...edicion, [e.target.name]: e.target.value });
    vEdicion.editado(e.target.name);
  };

  async function guardarEdicion(e) {
    e.preventDefault();
    if (vEdicion.hayErrores) return vEdicion.tocarTodos();
    const { id, nombre, descripcion } = edicion;
    const cuerpo = nombreCambiado ? { nombre, descripcion } : { descripcion };
    const ok = await ejecutar(() => api.put(`/admin/categorias/${id}`, cuerpo), 'Categoría actualizada', vEdicion);
    if (ok) setEdicion(null);
  }

  const cambiarEstado = (c) =>
    ejecutar(
      () => api.patch(`/admin/categorias/${c.id}/estado`, { activa: !c.activa }),
      c.activa ? `"${c.nombre}" desactivada: ya no aparece como filtro en el catálogo` : `"${c.nombre}" activada`
    );

  const eliminar = (c) => {
    if (!window.confirm(`¿Eliminar la categoría "${c.nombre}"?`)) return;
    ejecutar(() => api.delete(`/admin/categorias/${c.id}`), 'Categoría eliminada');
  };

  return (
    <>
      <div className="cabecera-seccion">
        <h1>Categorías</h1>
      </div>
      <Alerta alCerrar={() => setError('')}>{error}</Alerta>
      <Alerta tipo="exito" alCerrar={() => setExito('')}>{exito}</Alerta>

      <form className="panel formulario-linea" onSubmit={crear} noValidate>
        <Campo etiqueta="Nombre de la nueva categoría" id="nueva-nombre" error={vNueva.errorDe('nombre')}>
          <input
            id="nueva-nombre"
            name="nombre"
            placeholder="Ej. Grúas torre"
            value={nueva.nombre}
            onChange={cambiarNueva}
            onBlur={() => vNueva.tocar('nombre')}
          />
        </Campo>
        <Campo etiqueta="Descripción (opcional)" id="nueva-descripcion" error={vNueva.errorDe('descripcion')}>
          <input
            id="nueva-descripcion"
            name="descripcion"
            placeholder="Hasta 255 caracteres"
            value={nueva.descripcion}
            onChange={cambiarNueva}
            onBlur={() => vNueva.tocar('descripcion')}
          />
        </Campo>
        <button type="submit" className="boton boton-primario" disabled={vNueva.hayErrores}>
          Agregar
        </button>
      </form>

      {!categorias ? (
        <Cargando />
      ) : (
        <div className="tabla-contenedor">
          <table className="tabla">
            <thead>
              <tr>
                <th>Nombre</th>
                <th>Descripción</th>
                <th>Máquinas</th>
                <th>Estado</th>
                <th aria-label="Acciones" />
              </tr>
            </thead>
            <tbody>
              {categorias.length === 0 && (
                <tr>
                  <td colSpan={5} className="texto-suave">Aún no hay categorías.</td>
                </tr>
              )}
              {categorias.map((c) =>
                edicion?.id === c.id ? (
                  <tr key={c.id}>
                    <td>
                      <Campo etiqueta={<span className="solo-lector">Nombre</span>} id="editar-nombre" error={vEdicion.errorDe('nombre')}>
                        <input id="editar-nombre" name="nombre" value={edicion.nombre} onChange={cambiarEdicion} onBlur={() => vEdicion.tocar('nombre')} />
                      </Campo>
                    </td>
                    <td>
                      <Campo
                        etiqueta={<span className="solo-lector">Descripción</span>}
                        id="editar-descripcion"
                        error={vEdicion.errorDe('descripcion')}
                      >
                        <input
                          id="editar-descripcion"
                          name="descripcion"
                          value={edicion.descripcion}
                          onChange={cambiarEdicion}
                          onBlur={() => vEdicion.tocar('descripcion')}
                        />
                      </Campo>
                    </td>
                    <td>{c.totalMaquinas}</td>
                    <td />
                    <td className="acciones">
                      <button type="button" className="boton boton-primario boton-chico" onClick={guardarEdicion} disabled={vEdicion.hayErrores}>
                        Guardar
                      </button>
                      <button type="button" className="boton boton-secundario boton-chico" onClick={() => setEdicion(null)}>Cancelar</button>
                    </td>
                  </tr>
                ) : (
                  <tr key={c.id} className={c.activa ? '' : 'fila-inactiva'}>
                    <td><strong>{c.nombre}</strong></td>
                    <td>{c.descripcion}</td>
                    <td>
                      {c.totalMaquinas} <span className="texto-suave">({c.maquinasPublicadas} publicadas)</span>
                    </td>
                    <td>
                      <span className={`insignia ${c.activa ? 'insignia-exito' : 'insignia-gris'}`}>
                        {c.activa ? 'Activa' : 'Inactiva'}
                      </span>
                    </td>
                    <td className="acciones">
                      <button type="button" className="boton boton-secundario boton-chico" onClick={() => empezarEdicion(c)}>
                        Renombrar
                      </button>
                      <button type="button" className="boton boton-secundario boton-chico" onClick={() => cambiarEstado(c)}>
                        {c.activa ? 'Desactivar' : 'Activar'}
                      </button>
                      <button
                        type="button"
                        className="boton boton-peligro boton-chico"
                        onClick={() => eliminar(c)}
                        disabled={c.totalMaquinas > 0}
                        title={c.totalMaquinas > 0 ? 'Tiene máquinas: solo se puede desactivar' : 'Eliminar'}
                      >
                        Eliminar
                      </button>
                    </td>
                  </tr>
                )
              )}
            </tbody>
          </table>
        </div>
      )}
    </>
  );
}
