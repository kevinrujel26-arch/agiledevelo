// HU-03 Ver catálogo de maquinaria
import { useEffect, useState } from 'react';
import { useSearchParams } from 'react-router-dom';
import { api } from '../api/cliente';
import { Alerta, Cargando, Paginacion, TarjetaMaquina } from '../componentes/comunes';

const TAMANIO_PAGINA = 12;

export default function Catalogo() {
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

  const cambiarPagina = (p) => {
    setParams({ pagina: String(p) });
    window.scrollTo({ top: 0, behavior: 'smooth' });
  };

  return (
    <>
      <section className="portada">
        <div className="contenedor">
          <h1>Maquinaria pesada lista para tu obra</h1>
          <p>Revisa el catálogo, compara tarifas y reserva el equipo que necesitas por días.</p>
        </div>
      </section>

      <div className="contenedor">
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
            <p className="texto-suave resumen-resultados">
              {resultado.paginacion.total} máquina(s) disponible(s)
            </p>
            <div className="rejilla-catalogo">
              {resultado.datos.map((m) => (
                <TarjetaMaquina key={m.id} maquina={m} />
              ))}
            </div>
            <Paginacion paginacion={resultado.paginacion} alCambiar={cambiarPagina} />
          </>
        )}
      </div>
    </>
  );
}
