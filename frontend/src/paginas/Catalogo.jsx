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

  const [resultado, setResultado] = useState(null);
  const [error, setError] = useState('');

  useEffect(() => {
    let vigente = true;
    setError('');
    api
      .get('/maquinas', { pagina, tamanio: TAMANIO_PAGINA })
      .then((r) => vigente && setResultado(r))
      .catch((e) => vigente && setError(e.message));
    return () => {
      vigente = false;
    };
  }, [pagina]);

  const ciudades = useMemo(
    () => new Set((resultado?.datos || []).map((m) => m.ubicacion.trim().toLowerCase())).size,
    [resultado]
  );

  const cambiarPagina = (p) => {
    setParams({ pagina: String(p) });
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

          <Alerta>{error}</Alerta>
          {!resultado && !error && <Cargando texto="Cargando catálogo…" />}

          {resultado && resultado.datos.length === 0 && (
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
