// Listado de la flota para el administrador (HU-08)
import { useEffect, useRef, useState } from 'react';
import { Link, useSearchParams } from 'react-router-dom';
import { api } from '../../api/cliente';
import { Alerta, Cargando, FotoMaquina, Paginacion } from '../../componentes/comunes';
import { ETIQUETA_ESTADO, formatearHoras, formatearMoneda } from '../../utils/formato';
import { BUSQUEDA_MAX, normalizarTexto, validarBusqueda } from '../../utils/validaciones';

const PESTANAS = [
  ['', 'Todas'],
  ['PUBLICADA', 'Publicadas'],
  ['BORRADOR', 'Borradores'],
  ['RETIRADA', 'Retiradas'],
];

const ESPERA_BUSQUEDA_MS = 400; // espera tras dejar de escribir antes de buscar

export default function AdminMaquinas() {
  const [params, setParams] = useSearchParams();
  const estado = params.get('estado') || '';
  const pagina = Number(params.get('pagina')) || 1;
  const q = params.get('q') || '';
  // Lo que se escribe; se lleva a la URL tras una pequeña espera (debounce)
  const [busqueda, setBusqueda] = useState(q);
  // Último texto que este componente llevó a la URL, para distinguirlo de cambios externos
  const enviadoRef = useRef(null);

  const [resultado, setResultado] = useState(null);
  const [error, setError] = useState('');

  useEffect(() => {
    let vigente = true;
    setError('');
    api
      .get('/admin/maquinas', { estado, q, pagina, tamanio: 15 })
      .then((r) => vigente && setResultado(r))
      .catch((e) => vigente && setError(e.message));
    return () => {
      vigente = false;
    };
  }, [estado, q, pagina]);

  const actualizarFiltros = (cambios) => {
    const nuevos = { estado, q, ...cambios };
    setParams(Object.fromEntries(Object.entries(nuevos).filter(([, v]) => v)));
  };

  // Lleva la búsqueda a la URL (vuelve a la página 1)
  const errorBusqueda = validarBusqueda(busqueda);
  const aplicarBusqueda = () => {
    const texto = normalizarTexto(busqueda);
    if (errorBusqueda || texto === q) return;
    enviadoRef.current = texto;
    actualizarFiltros({ q: texto, pagina: '' });
  };

  // Busca al dejar de escribir; cada tecla reinicia la espera
  useEffect(() => {
    if (errorBusqueda || normalizarTexto(busqueda) === q) return undefined;
    const temporizador = setTimeout(aplicarBusqueda, ESPERA_BUSQUEDA_MS);
    return () => clearTimeout(temporizador);
  }, [busqueda, q, estado]);

  // Si la URL cambia desde fuera (atrás/adelante), el campo la sigue sin pisar lo que se escribe
  useEffect(() => {
    const enviado = enviadoRef.current;
    enviadoRef.current = null;
    if (enviado !== q) setBusqueda(q);
  }, [q]);

  return (
    <>
      <div className="cabecera-seccion">
        <h1>Máquinas</h1>
        <Link to="/admin/maquinas/nueva" className="boton boton-primario">+ Registrar máquina</Link>
      </div>
      <Alerta>{error}</Alerta>

      <div className="filtros">
        <div className="pestanas" role="tablist">
          {PESTANAS.map(([valor, texto]) => (
            <button
              type="button"
              role="tab"
              aria-selected={estado === valor}
              key={valor || 'todas'}
              className={estado === valor ? 'activa' : ''}
              onClick={() => actualizarFiltros({ estado: valor, pagina: '' })}
            >
              {texto}
            </button>
          ))}
        </div>
        <form
          onSubmit={(e) => {
            e.preventDefault();
            aplicarBusqueda(); // Enter busca sin esperar
          }}
        >
          <input
            type="search"
            placeholder="Buscar por nombre o marca"
            value={busqueda}
            maxLength={BUSQUEDA_MAX}
            onChange={(e) => setBusqueda(e.target.value)}
            aria-label="Buscar máquinas"
            aria-invalid={Boolean(errorBusqueda)}
            aria-describedby={errorBusqueda ? 'busqueda-mensaje' : undefined}
          />
        </form>
      </div>
      {errorBusqueda && <p className="mensaje-error" id="busqueda-mensaje">{errorBusqueda}</p>}

      {!resultado ? (
        <Cargando />
      ) : resultado.datos.length === 0 ? (
        <div className="vacio">
          <p>No hay máquinas con estos filtros.</p>
        </div>
      ) : (
        <>
          <div className="tabla-contenedor">
            <table className="tabla">
              <thead>
                <tr>
                  <th aria-label="Foto" />
                  <th>Máquina</th>
                  <th>Categoría</th>
                  <th>Tarifa por hora</th>
                  <th className="columna-opcional">Horas de uso</th>
                  <th>Estado</th>
                  <th aria-label="Acciones" />
                </tr>
              </thead>
              <tbody>
                {resultado.datos.map((m) => (
                  <tr key={m.id}>
                    <td className="celda-foto">
                      <FotoMaquina ruta={m.fotoPrincipal} alt="" />
                    </td>
                    <td>
                      <strong>{m.nombre}</strong>
                      <div className="texto-suave">{m.marca} · {m.modelo} · {m.ubicacion}</div>
                      {m.enMantenimiento && <span className="insignia insignia-aviso">En mantenimiento</span>}
                    </td>
                    <td>{m.categoria.nombre}</td>
                    <td>{formatearMoneda(m.tarifaHoraria)}</td>
                    <td className="columna-opcional">{formatearHoras(m.horasUso)}</td>
                    <td>
                      <span className={`insignia estado-${m.estado.toLowerCase()}`}>{ETIQUETA_ESTADO[m.estado]}</span>
                    </td>
                    <td className="acciones">
                      <Link to={`/admin/maquinas/${m.id}`} className="boton boton-secundario boton-chico">Editar</Link>
                      <Link to={`/admin/maquinas/${m.id}/disponibilidad`} className="boton boton-secundario boton-chico">Disponibilidad</Link>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
          <Paginacion paginacion={resultado.paginacion} alCambiar={(p) => actualizarFiltros({ pagina: String(p) })} />
        </>
      )}
    </>
  );
}
