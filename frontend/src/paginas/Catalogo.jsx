// Página de inicio + HU-03 Ver catálogo de maquinaria
import { useEffect, useMemo, useRef, useState } from 'react';
import { Link, useSearchParams } from 'react-router-dom';
import { api } from '../api/cliente';
import { useAuth, panelSegunRol } from '../contexto/AuthContext';
import { Alerta, Cargando, Paginacion, TarjetaMaquina } from '../componentes/comunes';
import { Excavadora, Icono } from '../componentes/Ilustracion';

const TAMANIO_PAGINA = 12;
const ESPERA_BUSQUEDA_MS = 400; // espera tras dejar de escribir antes de buscar (texto y precio)

const PASOS = [
  { titulo: 'Explora el catálogo', texto: 'Compara equipos, especificaciones técnicas y tarifas por hora en un solo lugar.' },
  { titulo: 'Elige tus fechas', texto: 'Revisa la disponibilidad en el calendario y reserva cuando la necesites.' },
  { titulo: 'Recibe en tu obra', texto: 'Confirmamos tu reserva y coordinamos la entrega del equipo donde lo necesites.' },
];

export default function Catalogo() {
  const { usuario } = useAuth();
  const [params, setParams] = useSearchParams();
  const pagina = Math.max(1, Number(params.get('pagina')) || 1);
  const categoriaId = params.get('categoriaId') || '';
  const q = params.get('q') || '';
  const precioMin = params.get('precioMin') || '';
  const precioMax = params.get('precioMax') || '';
  // Lo que el usuario escribe; se lleva a la URL tras una pequeña espera (debounce)
  const [busqueda, setBusqueda] = useState(q);
  const [entradaMin, setEntradaMin] = useState(precioMin);
  const [entradaMax, setEntradaMax] = useState(precioMax);
  // Últimos valores que este componente llevó a la URL, para distinguirlos de cambios externos
  const enviadoRef = useRef(null);
  const rangoInvalido = entradaMin !== '' && entradaMax !== '' && Number(entradaMin) > Number(entradaMax);
  const hayFiltros = Boolean(categoriaId || q || precioMin || precioMax);

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
      .get('/maquinas', { categoriaId, q, precioMin, precioMax, pagina, tamanio: TAMANIO_PAGINA })
      .then((r) => vigente && setResultado(r))
      .catch((e) => vigente && setError(e.message));
    return () => {
      vigente = false;
    };
  }, [categoriaId, q, precioMin, precioMax, pagina]);

  const actualizarFiltros = (cambios) => {
    const nuevos = { categoriaId, q, precioMin, precioMax, ...cambios };
    setParams(Object.fromEntries(Object.entries(nuevos).filter(([, v]) => v)));
  };

  // Búsqueda y rango de precio que corresponden a lo escrito en los campos
  const objetivo = {
    q: busqueda.trim(),
    // HU-06: un rango inválido no se aplica; se mantiene el último válido
    precioMin: rangoInvalido ? precioMin : entradaMin,
    precioMax: rangoInvalido ? precioMax : entradaMax,
  };
  const pendiente = objetivo.q !== q || objetivo.precioMin !== precioMin || objetivo.precioMax !== precioMax;

  // Lleva a la URL una nueva búsqueda (vuelve a la página 1) y la recuerda como propia
  const enviarBusqueda = (nueva) => {
    if (nueva.q === q && nueva.precioMin === precioMin && nueva.precioMax === precioMax) return;
    enviadoRef.current = nueva;
    actualizarFiltros({ ...nueva, pagina: '' });
  };
  const aplicarEntradas = () => enviarBusqueda(objetivo);

  // Busca al dejar de escribir; cada tecla reinicia la espera
  useEffect(() => {
    if (!pendiente) return undefined;
    const temporizador = setTimeout(aplicarEntradas, ESPERA_BUSQUEDA_MS);
    return () => clearTimeout(temporizador);
    // Incluye los demás filtros para no aplicar la búsqueda con valores viejos de categoría
  }, [busqueda, entradaMin, entradaMax, categoriaId, q, precioMin, precioMax]);

  // Si la URL cambia desde fuera (atrás/adelante, "Quitar filtros"), los campos la siguen.
  // Los cambios que hizo la propia búsqueda se ignoran para no pisar lo que se sigue escribiendo.
  useEffect(() => {
    const enviado = enviadoRef.current;
    enviadoRef.current = null;
    if (enviado && enviado.q === q && enviado.precioMin === precioMin && enviado.precioMax === precioMax) return;
    setBusqueda(q);
    setEntradaMin(precioMin);
    setEntradaMax(precioMax);
  }, [q, precioMin, precioMax]);

  const limpiarPrecio = () => {
    setEntradaMin('');
    setEntradaMax('');
    enviarBusqueda({ q: objetivo.q, precioMin: '', precioMax: '' });
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
              <span className="punto-vivo" /> Alquiler de maquinaria por horas
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
                <strong>1 hora</strong>
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
                aplicarEntradas(); // Enter busca sin esperar
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
            {/* HU-06: rango de precio por hora */}
            <div className={`filtro-precio ${rangoInvalido ? 'campo-error' : ''}`}>
              <label>
                <span>Precio mín. (S/ por hora)</span>
                <input
                  type="number"
                  min="0"
                  step="any"
                  inputMode="decimal"
                  placeholder="0"
                  value={entradaMin}
                  onChange={(e) => setEntradaMin(e.target.value)}
                  aria-invalid={rangoInvalido}
                />
              </label>
              <label>
                <span>Precio máx.</span>
                <input
                  type="number"
                  min="0"
                  step="any"
                  inputMode="decimal"
                  placeholder="Sin límite"
                  value={entradaMax}
                  onChange={(e) => setEntradaMax(e.target.value)}
                  aria-invalid={rangoInvalido}
                />
              </label>
              {(entradaMin || entradaMax) && (
                <button type="button" className="boton boton-secundario boton-chico" onClick={limpiarPrecio}>
                  Limpiar precio
                </button>
              )}
            </div>
          </div>
          {rangoInvalido && <p className="mensaje-error">El precio mínimo no puede ser mayor que el máximo.</p>}

          <Alerta>{error}</Alerta>
          {!resultado && !error && <Cargando texto="Cargando catálogo…" />}

          {resultado && resultado.datos.length === 0 && hayFiltros && (
            <div className="vacio">
              <h2>{precioMin || precioMax ? 'No hay máquinas en ese rango' : 'Sin resultados para estos filtros'}</h2>
              <p className="texto-suave">
                {precioMin || precioMax
                  ? 'Prueba ampliando el rango de precio por hora.'
                  : 'Prueba con otra categoría o palabra clave.'}
              </p>
              <button type="button" className="boton boton-secundario" onClick={() => setParams({})}>
                Quitar filtros
              </button>
            </div>
          )}

          {resultado && resultado.datos.length === 0 && !hayFiltros && (
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
