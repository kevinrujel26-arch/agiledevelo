// HU-14 Gestionar categorías de maquinaria
import { useCallback, useEffect, useState } from 'react';
import { api } from '../../api/cliente';
import { Alerta, Cargando } from '../../componentes/comunes';

export default function AdminCategorias() {
  const [categorias, setCategorias] = useState(null);
  const [nueva, setNueva] = useState({ nombre: '', descripcion: '' });
  const [edicion, setEdicion] = useState(null); // { id, nombre, descripcion }
  const [error, setError] = useState('');
  const [exito, setExito] = useState('');

  const cargar = useCallback(() => {
    api.get('/admin/categorias').then((r) => setCategorias(r.datos)).catch((e) => setError(e.message));
  }, []);
  useEffect(cargar, [cargar]);

  async function ejecutar(accion, mensajeExito) {
    setError('');
    setExito('');
    try {
      await accion();
      setExito(mensajeExito);
      cargar();
      return true;
    } catch (e) {
      setError(e.message);
      return false;
    }
  }

  async function crear(e) {
    e.preventDefault();
    if (!nueva.nombre.trim()) return setError('El nombre es obligatorio');
    const ok = await ejecutar(() => api.post('/admin/categorias', nueva), `Categoría "${nueva.nombre.trim()}" creada`);
    if (ok) setNueva({ nombre: '', descripcion: '' });
  }

  async function guardarEdicion(e) {
    e.preventDefault();
    const { id, nombre, descripcion } = edicion;
    const ok = await ejecutar(() => api.put(`/admin/categorias/${id}`, { nombre, descripcion }), 'Categoría actualizada');
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

      <form className="panel formulario-linea" onSubmit={crear}>
        <input
          placeholder="Nombre de la nueva categoría"
          value={nueva.nombre}
          onChange={(e) => setNueva({ ...nueva, nombre: e.target.value })}
          maxLength={80}
          aria-label="Nombre de la nueva categoría"
        />
        <input
          placeholder="Descripción (opcional)"
          value={nueva.descripcion}
          onChange={(e) => setNueva({ ...nueva, descripcion: e.target.value })}
          maxLength={255}
          aria-label="Descripción"
        />
        <button type="submit" className="boton boton-primario">Agregar</button>
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
                      <input value={edicion.nombre} onChange={(e) => setEdicion({ ...edicion, nombre: e.target.value })} aria-label="Nombre" />
                    </td>
                    <td>
                      <input value={edicion.descripcion || ''} onChange={(e) => setEdicion({ ...edicion, descripcion: e.target.value })} aria-label="Descripción" />
                    </td>
                    <td>{c.totalMaquinas}</td>
                    <td />
                    <td className="acciones">
                      <button type="button" className="boton boton-primario boton-chico" onClick={guardarEdicion}>Guardar</button>
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
                      <button type="button" className="boton boton-secundario boton-chico" onClick={() => setEdicion({ id: c.id, nombre: c.nombre, descripcion: c.descripcion })}>
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
