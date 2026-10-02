// HU-04 Detalle de máquina
import { useEffect, useState } from 'react';
import { Link, useNavigate, useParams } from 'react-router-dom';
import { api } from '../api/cliente';
import { useAuth } from '../contexto/AuthContext';
import { Alerta, Cargando, FotoMaquina } from '../componentes/comunes';
import { Icono } from '../componentes/Ilustracion';
import Calendario from '../componentes/Calendario';
import { formatearHoras, formatearMoneda } from '../utils/formato';

export default function DetalleMaquina() {
  const { id } = useParams();
  const { usuario, cargando } = useAuth();
  const navegar = useNavigate();
  const [maquina, setMaquina] = useState(null);
  const [ocupados, setOcupados] = useState([]);
  const [fotoActiva, setFotoActiva] = useState(0);
  const [error, setError] = useState('');
  const [avisoReserva, setAvisoReserva] = useState(false);

  useEffect(() => {
    let vigente = true;
    setMaquina(null);
    setError('');
    setFotoActiva(0);
    setAvisoReserva(false);
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
          <Link to="/catalogo" className="boton boton-secundario">← Volver al catálogo</Link>
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
          <h1 className="titulo-aviso">{maquina.nombre}</h1>
          <p className="texto-suave">
            Este equipo está en mantenimiento y no se puede reservar por ahora. Vuelve a revisarlo en unos días.
          </p>
          <Link to="/catalogo" className="boton boton-secundario">← Ver otros equipos</Link>
        </div>
      </div>
    );
  }

  // HU-04 criterio 3: sin sesión lleva al login y vuelve aquí; con sesión, el flujo de reservas aún no existe
  function reservar() {
    if (!usuario) {
      navegar('/login', { state: { desde: `/maquinas/${id}` } });
      return;
    }
    setAvisoReserva(true);
  }

  const fotos = maquina.fotos;
  const especificaciones = Object.entries(maquina.especificaciones || {});

  return (
    <div className="contenedor">
      <nav className="migas" aria-label="Ruta">
        <Link to="/catalogo">Catálogo</Link> <span aria-hidden="true">/</span> <span>{maquina.categoria.nombre}</span>{' '}
        <span aria-hidden="true">/</span>
        <span className="miga-actual" aria-current="page">{maquina.nombre}</span>
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
          <span className="insignia insignia-info">{maquina.categoria.nombre}</span>
          <h1>{maquina.nombre}</h1>
          <p className="texto-suave sin-margen">
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
            <div className="dato">
              <span>Horas de uso</span>
              <strong>{formatearHoras(maquina.horasUso)}</strong>
              <small className="ayuda">Horas registradas en el sistema</small>
            </div>
          </div>

          {maquina.descripcion && <p className="descripcion">{maquina.descripcion}</p>}

          <button
            type="button"
            className="boton boton-primario boton-grande boton-bloque"
            onClick={reservar}
            disabled={cargando}
          >
            Reservar
          </button>
          {!usuario && !cargando && (
            <p className="ayuda centrado nota-sesion">Necesitas iniciar sesión para reservar.</p>
          )}
          {avisoReserva && (
            <div className="nota-reserva" role="status">
              <Icono nombre="info" />
              <span>La reserva en línea estará disponible pronto. Mientras tanto, revisa en el calendario las fechas libres.</span>
            </div>
          )}
        </aside>
      </div>
    </div>
  );
}
