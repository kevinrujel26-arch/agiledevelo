// HU-03 Ver catálogo de maquinaria
import { useEffect, useRef, useState } from 'react';
import { useSearchParams } from 'react-router-dom';
import { api } from '../api/cliente';
import { Alerta, EsqueletoTarjetas, Paginacion, TarjetaMaquina } from '../componentes/comunes';
import { Icono } from '../componentes/Ilustracion';
import { BUSQUEDA_MAX, erroresPrecio, limpiarDecimal, normalizarTexto, sinPuntoFinal, validarBusqueda } from '../utils/validaciones';

const TAMANIO_PAGINA = 12;
const ESPERA_BUSQUEDA_MS = 400; // espera tras dejar de escribir antes de buscar (texto y precio)

export default function Catalogo() {
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
  // Validación en vivo: lo que no es válido no se aplica (se mantiene lo último válido de la URL)
  const errorBusqueda = validarBusqueda(busqueda);
  const errPrecio = erroresPrecio(entradaMin, entradaMax);
  const precioInvalido = Object.keys(errPrecio).length > 0;
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
    q: errorBusqueda ? q : normalizarTexto(busqueda),
    // HU-06: un rango inválido no se aplica; se mantiene el último válido
    precioMin: precioInvalido ? precioMin : sinPuntoFinal(entradaMin),
    precioMax: precioInvalido ? precioMax : sinPuntoFinal(entradaMax),
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

  const cambiarPagina = (p) => {
    actualizarFiltros({ pagina: p === 1 ? '' : String(p) });
    document.getElementById('equipos')?.scrollIntoView({ behavior: 'smooth' });
  };

  return (
    <>
      {/* ------------------------------ Catálogo ------------------------------ */}
      <section className="seccion" id="equipos">
        <div className="contenedor">
          <div className="seccion-cabecera">
            <div>
              <div className="seccion-sobretitulo">Catálogo</div>
              <h2>Equipos disponibles</h2>
            </div>
            {resultado && resultado.datos.length > 0 && (
              <p className="texto-suave sin-margen">
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
              className="buscador"
              role="search"
              onSubmit={(e) => {
                e.preventDefault();
                aplicarEntradas(); // Enter busca sin esperar
              }}
            >
              <Icono nombre="buscar" />
              <input
                type="search"
                placeholder="Buscar por nombre o marca"
                value={busqueda}
                maxLength={BUSQUEDA_MAX}
                onChange={(e) => setBusqueda(e.target.value)}
                aria-label="Buscar maquinaria"
                aria-invalid={Boolean(errorBusqueda)}
                aria-describedby={errorBusqueda ? 'busqueda-mensaje' : undefined}
              />
            </form>
            {/* HU-06: rango de precio por hora. Solo admite dígitos y un punto decimal */}
            <div className={`filtro-precio ${precioInvalido ? 'campo-error' : ''}`}>
              <label>
                <span>Precio mín. (S/ por hora)</span>
                <input
                  inputMode="decimal"
                  placeholder="0"
                  value={entradaMin}
                  onChange={(e) => setEntradaMin(limpiarDecimal(e.target.value))}
                  aria-invalid={Boolean(errPrecio.precioMin)}
                  aria-describedby={errPrecio.precioMin ? 'precio-min-mensaje' : undefined}
                />
              </label>
              <label>
                <span>Precio máx.</span>
                <input
                  inputMode="decimal"
                  placeholder="Sin límite"
                  value={entradaMax}
                  onChange={(e) => setEntradaMax(limpiarDecimal(e.target.value))}
                  aria-invalid={Boolean(errPrecio.precioMax)}
                  aria-describedby={errPrecio.precioMax ? 'precio-max-mensaje' : undefined}
                />
              </label>
              {(entradaMin || entradaMax) && (
                <button type="button" className="boton boton-secundario boton-chico" onClick={limpiarPrecio}>
                  Limpiar precio
                </button>
              )}
            </div>
          </div>
          {errorBusqueda && <p className="mensaje-error" id="busqueda-mensaje">{errorBusqueda}</p>}
          {errPrecio.precioMin && <p className="mensaje-error" id="precio-min-mensaje">{errPrecio.precioMin}</p>}
          {errPrecio.precioMax && <p className="mensaje-error" id="precio-max-mensaje">{errPrecio.precioMax}</p>}

          <Alerta>{error}</Alerta>
          {!resultado && !error && <EsqueletoTarjetas cantidad={6} texto="Cargando catálogo…" />}

          {resultado && resultado.datos.length === 0 && hayFiltros && (
            <div className="vacio">
              <span className="vacio-icono" aria-hidden="true"><Icono nombre="buscar" tamanio={28} /></span>
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
              <span className="vacio-icono" aria-hidden="true"><Icono nombre="camion" tamanio={28} /></span>
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
