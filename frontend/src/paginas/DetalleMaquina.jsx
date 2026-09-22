// Detalle de máquina: base para HU-04 (el calendario y el botón "Reservar" llegan en el Sprint 3)
import { useEffect, useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import { api } from '../api/cliente';
import { Alerta, Cargando, FotoMaquina } from '../componentes/comunes';
import { formatearMoneda, formatearRango } from '../utils/formato';

export default function DetalleMaquina() {
  const { id } = useParams();
  const [maquina, setMaquina] = useState(null);
  const [ocupados, setOcupados] = useState([]);
  const [fotoActiva, setFotoActiva] = useState(0);
  const [error, setError] = useState('');

  useEffect(() => {
    let vigente = true;
    setMaquina(null);
    setError('');
    setFotoActiva(0);
    Promise.all([api.get(`/maquinas/${id}`), api.get(`/maquinas/${id}/disponibilidad`)])
      .then(([m, d]) => {
        if (!vigente) return;
        setMaquina(m);
        setOcupados(d.ocupados);
      })
      .catch((e) => vigente && setError(e.estado === 404 ? 'La máquina no existe o ya no está disponible.' : e.message));
    return () => {
      vigente = false;
    };
  }, [id]);

  if (error) {
    return (
      <div className="contenedor">
        <Alerta>{error}</Alerta>
        <Link to="/">← Volver al catálogo</Link>
      </div>
    );
  }
  if (!maquina) return <Cargando />;

  // HU-04 criterio 5: si está en mantenimiento se muestra un aviso en vez del detalle
  if (maquina.enMantenimiento) {
    return (
      <div className="contenedor contenedor-angosto">
        <div className="panel centrado">
          <h1>{maquina.nombre}</h1>
          <Alerta tipo="aviso">
            Esta máquina está en mantenimiento y no se puede reservar por ahora. Vuelve a revisarla en unos días.
          </Alerta>
          <Link to="/" className="boton boton-secundario">← Ver otras máquinas</Link>
        </div>
      </div>
    );
  }

  const fotos = maquina.fotos;
  const especificaciones = Object.entries(maquina.especificaciones || {});

  return (
    <div className="contenedor">
      <Link to="/" className="enlace-volver">← Volver al catálogo</Link>
      <div className="detalle">
        <div className="galeria">
          <FotoMaquina ruta={fotos[fotoActiva]?.url} alt={maquina.nombre} className="galeria-principal" />
          {fotos.length > 1 && (
            <div className="galeria-miniaturas">
              {fotos.map((f, i) => (
                <button
                  type="button"
                  key={f.id}
                  className={i === fotoActiva ? 'activa' : ''}
                  onClick={() => setFotoActiva(i)}
                  aria-label={`Ver foto ${i + 1}`}
                >
                  <FotoMaquina ruta={f.url} alt="" />
                </button>
              ))}
            </div>
          )}
        </div>

        <div className="detalle-info">
          <span className="insignia">{maquina.categoria.nombre}</span>
          <h1>{maquina.nombre}</h1>
          <p className="texto-suave">
            {maquina.marca} · Modelo {maquina.modelo}
          </p>
          <p className="tarifa tarifa-grande">
            {formatearMoneda(maquina.tarifaDiaria)} <span>/ día</span>
          </p>
          <p>
            <strong>Ubicación:</strong> {maquina.ubicacion}
          </p>
          {maquina.descripcion && <p>{maquina.descripcion}</p>}

          {especificaciones.length > 0 && (
            <>
              <h2 className="subtitulo">Especificaciones técnicas</h2>
              <table className="tabla-specs">
                <tbody>
                  {especificaciones.map(([clave, valor]) => (
                    <tr key={clave}>
                      <th scope="row">{clave}</th>
                      <td>{valor}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </>
          )}

          <h2 className="subtitulo">Fechas no disponibles (próximos 6 meses)</h2>
          {ocupados.length === 0 ? (
            <p className="texto-suave">Sin fechas ocupadas. ¡Disponible!</p>
          ) : (
            <ul className="lista-fechas">
              {ocupados.map((o) => (
                <li key={`${o.tipo}-${o.fechaInicio}`}>{formatearRango(o.fechaInicio, o.fechaFin)}</li>
              ))}
            </ul>
          )}
        </div>
      </div>
    </div>
  );
}
