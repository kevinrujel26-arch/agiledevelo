// Página de inicio + HU-03 Ver catálogo de maquinaria
import { useEffect, useMemo, useState } from 'react';
import { Link, useSearchParams } from 'react-router-dom';
import { api } from '../api/cliente';
import { useAuth, panelSegunRol } from '../contexto/AuthContext';
import { Alerta, Cargando, Paginacion, TarjetaMaquina } from '../componentes/comunes';
import { Excavadora, Icono } from '../componentes/Ilustracion';

const TAMANIO_PAGINA = 12;

const PASOS = [
  { titulo: 'Explora el catálogo', texto: 'Compara equipos, especificaciones técnicas y tarifas por día en un solo lugar.' },
  { titulo: 'Elige tus fechas', texto: 'Revisa la disponibilidad en el calendario y reserva el rango de días que necesitas.' },
  { titulo: 'Recibe en tu obra', texto: 'Confirmamos tu reserva y coordinamos la entrega del equipo donde lo necesites.' },
];

export default function Catalogo() {
  const { usuario } = useAuth();
  const [params, setParams] = useSearchParams();
  const pagina = Math.max(1, Number(params.get('pagina')) || 1);
  const categoriaId = params.get('categoriaId') || '';
  const q = params.get('q') || '';
  const [busqueda, setBusqueda] = useState(q);

  const [resultado, setResultado] = useState(null);
  const [error, setError] = useState('');
  const [categorias, setCategorias] = useState([]);

  // HU-03 criterio 2: categorías activas para el filtro
  useEffect(() => {
    api.get('/categorias').then((r) => setCategorias(r.datos)).catch(() => {});
  }, []);

  useEffect(() => {
    let vigente = true;
    setError('');
    api
      .get('/maquinas', { categoriaId, q, pagina, tamanio: TAMANIO_PAGINA })
      .then((r) => vigente && setResultado(r))
      .catch((e) => vigente && setError(e.message));
    return () => {
      vigente = false;
    };
  }, [categoriaId, q, pagina]);

  const actualizarFiltros = (cambios) => {
    const nuevos = { categoriaId, q, ...cambios };
    setParams(Object.fromEntries(Object.entries(nuevos).filter(([, v]) => v)));
  };

  const ciudades = useMemo(
    () => new Set((resultado?.datos || []).map((m) => m.ubicacion.trim().toLowerCase())).size,
    [resultado]
  );

  const cambiarPagina = (p) => {
    actualizarFiltros({ pagina: p === 1 ? '' : String(p) });
    document.getElementById('equipos')?.scrollIntoView({ behavior: 'smooth' });
  };

  return (
    <>
      {/* ------------------------------ Portada ------------------------------ */}
      <section className="hero">
        <div className="contenedor hero-interior">
          <div>
            <span className="hero-etiqueta">
              <span className="punto-vivo" /> Alquiler de maquinaria por días
            </span>
            <h1>
              Maquinaria pesada,
              <br />
              <span className="degradado">a un clic.</span>
            </h1>
            <p className="hero-texto">
              Excavadoras, cargadores, rodillos y más, listos para tu obra. Compara tarifas, revisa la disponibilidad
              y reserva sin llamadas ni trámites.
            </p>
            <div className="hero-acciones">
              <a href="#equipos" className="boton boton-primario boton-grande">
                Ver equipos <Icono nombre="flecha" />
              </a>
              {usuario ? (
                <Link to={panelSegunRol(usuario)} className="boton boton-secundario boton-grande">
                  Ir a mi cuenta
                </Link>
              ) : (
                <Link to="/registro" className="boton boton-secundario boton-grande">
                  Crear cuenta gratis
                </Link>
              )}
            </div>
            <div className="hero-estadisticas">
              <div className="estadistica">
                <strong>{resultado ? resultado.paginacion.total : '—'}</strong>
                <span>equipos disponibles</span>
              </div>
              <div className="estadistica">
                <strong>{resultado ? Math.max(ciudades, 1) : '—'}</strong>
                <span>{ciudades === 1 ? 'ciudad' : 'ciudades'}</span>
              </div>
              <div className="estadistica">
                <strong>1 día</strong>
                <span>alquiler mínimo</span>
              </div>
            </div>
          </div>

          <div className="hero-visual" aria-hidden="true">
            <Excavadora />
            <div className="tarjeta-flotante tf-1">
              <span className="icono"><Icono nombre="calendario" /></span>
              <div>
                <strong>Disponibilidad en vivo</strong>
                <span>Calendario actualizado</span>
              </div>
            </div>
            <div className="tarjeta-flotante tf-2">
              <span className="icono"><Icono nombre="escudo" /></span>
              <div>
                <strong>Reserva segura</strong>
                <span>Fechas bloqueadas para ti</span>
              </div>
            </div>
          </div>
        </div>
      </section>

      {/* ---------------------------- Cómo funciona ---------------------------- */}
      <section className="seccion">
        <div className="contenedor">
          <div className="seccion-cabecera">
            <div>
              <div className="seccion-sobretitulo">Cómo funciona</div>
              <h2>Alquilar nunca fue tan simple</h2>
            </div>
          </div>
          <div className="pasos">
            {PASOS.map((p, i) => (
              <div className="paso" key={p.titulo}>
                <div className="paso-numero degradado">0{i + 1}</div>
                <h3>{p.titulo}</h3>
                <p>{p.texto}</p>
              </div>
            ))}
          </div>
        </div>
      </section>

      {/* ------------------------------ Catálogo ------------------------------ */}
      <section className="seccion" id="equipos">
        <div className="contenedor">
          <div className="seccion-cabecera">
            <div>
              <div className="seccion-sobretitulo">Catálogo</div>
              <h2>Equipos disponibles</h2>
            </div>
            {resultado && resultado.datos.length > 0 && (
              <p className="texto-suave" style={{ margin: 0 }}>
                {resultado.paginacion.total} equipo(s) · página {resultado.paginacion.pagina} de {resultado.paginacion.totalPaginas}
              </p>
            )}
          </div>

          {/* HU-03 criterio 2: filtrar por categoría y buscar por palabra clave */}
          <div className="filtros">
            <div className="pestanas" role="tablist">
              <button
                type="button"
                role="tab"
                aria-selected={categoriaId === ''}
                className={categoriaId === '' ? 'activa' : ''}
                onClick={() => actualizarFiltros({ categoriaId: '', pagina: '' })}
              >
                Todas
              </button>
              {categorias.map((c) => (
                <button
                  type="button"
                  role="tab"
                  key={c.id}
                  aria-selected={categoriaId === String(c.id)}
                  className={categoriaId === String(c.id) ? 'activa' : ''}
                  onClick={() => actualizarFiltros({ categoriaId: String(c.id), pagina: '' })}
                >
                  {c.nombre}
                </button>
              ))}
            </div>
            <form
              onSubmit={(e) => {
                e.preventDefault();
                actualizarFiltros({ q: busqueda.trim(), pagina: '' });
              }}
            >
              <input
                type="search"
                placeholder="Buscar por nombre o marca"
                value={busqueda}
                onChange={(e) => setBusqueda(e.target.value)}
                aria-label="Buscar maquinaria"
              />
            </form>
          </div>

          <Alerta>{error}</Alerta>
          {!resultado && !error && <Cargando texto="Cargando catálogo…" />}

          {resultado && resultado.datos.length === 0 && (categoriaId || q) && (
            <div className="vacio">
              <h2>Sin resultados para estos filtros</h2>
              <p className="texto-suave">Prueba con otra categoría o palabra clave.</p>
              <button type="button" className="boton boton-secundario" onClick={() => { setBusqueda(''); setParams({}); }}>
                Quitar filtros
              </button>
            </div>
          )}

          {resultado && resultado.datos.length === 0 && !categoriaId && !q && (
            <div className="vacio">
              <h2>Aún no hay máquinas publicadas</h2>
              <p className="texto-suave">Vuelve pronto: estamos preparando nuestra flota.</p>
            </div>
          )}

          {resultado && resultado.datos.length > 0 && (
            <>
              <div className="rejilla-catalogo">
                {resultado.datos.map((m) => (
                  <TarjetaMaquina key={m.id} maquina={m} />
                ))}
              </div>
              <Paginacion paginacion={resultado.paginacion} alCambiar={cambiarPagina} />
            </>
          )}
        </div>
      </section>
    </>
  );
}
