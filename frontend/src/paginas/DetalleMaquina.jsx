// Detalle de máquina: base para HU-04 (el botón "Reservar" llega en el Sprint 3)
import { useEffect, useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import { api } from '../api/cliente';
import { Alerta, Cargando, FotoMaquina } from '../componentes/comunes';
import { Icono } from '../componentes/Ilustracion';
import Calendario from '../componentes/Calendario';
import { formatearMoneda } from '../utils/formato';

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
      <div className="contenedor contenedor-angosto">
        <div className="panel centrado">
          <Alerta>{error}</Alerta>
          <Link to="/" className="boton boton-secundario">← Volver al catálogo</Link>
        </div>
      </div>
    );
  }
  if (!maquina) return <Cargando />;

  // HU-04 criterio 5: si está en mantenimiento se muestra un aviso en vez del detalle
  if (maquina.enMantenimiento) {
    return (
      <div className="contenedor contenedor-angosto">
        <div className="panel centrado">
          <span className="insignia insignia-aviso"><Icono nombre="herramienta" tamanio={14} /> En mantenimiento</span>
          <h1 style={{ marginTop: 14 }}>{maquina.nombre}</h1>
          <p className="texto-suave">
            Este equipo está en mantenimiento y no se puede reservar por ahora. Vuelve a revisarlo en unos días.
          </p>
          <Link to="/" className="boton boton-secundario">← Ver otros equipos</Link>
        </div>
      </div>
    );
  }

  const fotos = maquina.fotos;
  const especificaciones = Object.entries(maquina.especificaciones || {});

  return (
    <div className="contenedor">
      <nav className="migas" aria-label="Ruta">
        <Link to="/">Catálogo</Link> <span>/</span> <span>{maquina.categoria.nombre}</span> <span>/</span>
        <span style={{ color: 'var(--texto)' }}>{maquina.nombre}</span>
      </nav>

      <div className="detalle">
        {/* ----------------------------- Columna izquierda ----------------------------- */}
        <div>
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

          {especificaciones.length > 0 && (
            <section className="bloque-detalle">
              <h2 className="subtitulo">Especificaciones técnicas</h2>
              <div className="specs">
                {especificaciones.map(([clave, valor]) => (
                  <div className="spec" key={clave}>
                    <span>{clave}</span>
                    <strong>{valor}</strong>
                  </div>
                ))}
              </div>
            </section>
          )}

          <section className="bloque-detalle">
            <h2 className="subtitulo">Disponibilidad</h2>
            <Calendario ocupados={ocupados} meses={2} />
          </section>
        </div>

        {/* ------------------------------ Columna derecha ------------------------------ */}
        <aside className="panel panel-precio detalle-info">
          <span className="insignia">{maquina.categoria.nombre}</span>
          <h1>{maquina.nombre}</h1>
          <p className="texto-suave" style={{ margin: 0 }}>
            {maquina.marca} · Modelo {maquina.modelo}
          </p>
          <p className="tarifa tarifa-grande">
            {formatearMoneda(maquina.tarifaHoraria)} <span>/ hora</span>
          </p>

          <div className="datos-rapidos">
            <div className="dato">
              <span>Ubicación</span>
              <strong>{maquina.ubicacion}</strong>
            </div>
            <div className="dato">
              <span>Alquiler mínimo</span>
              <strong>1 hora</strong>
            </div>
          </div>

          {maquina.descripcion && <p className="descripcion">{maquina.descripcion}</p>}

          <div className="nota-reserva">
            <Icono nombre="info" />
            <span>La reserva en línea estará disponible muy pronto. Revisa en el calendario las fechas libres.</span>
          </div>
        </aside>
      </div>
    </div>
  );
}
