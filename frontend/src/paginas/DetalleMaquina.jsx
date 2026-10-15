// HU-04 Detalle de máquina
import { useEffect, useState } from 'react';
import { Link, useNavigate, useParams } from 'react-router-dom';
import { api } from '../api/cliente';
import { useAuth } from '../contexto/AuthContext';
import { Alerta, Cargando, FotoMaquina } from '../componentes/comunes';
import { Icono } from '../componentes/Ilustracion';
import Calendario from '../componentes/Calendario';
import FormularioReserva from '../componentes/FormularioReserva';
import { formatearHoras, formatearMoneda } from '../utils/formato';
import { HU6_ACTIVA } from '../alcance';

export default function DetalleMaquina() {
  const { id } = useParams();
  const { usuario, cargando } = useAuth();
  const navegar = useNavigate();
  const [maquina, setMaquina] = useState(null);
  const [ocupados, setOcupados] = useState([]);
  const [fotoActiva, setFotoActiva] = useState(0);
  const [error, setError] = useState('');
  const [mostrarFormularioReserva, setMostrarFormularioReserva] = useState(false);
  const [avisoReserva, setAvisoReserva] = useState(false);

  useEffect(() => {
    let vigente = true;
    setMaquina(null);
    setError('');
    setFotoActiva(0);
    setMostrarFormularioReserva(false);
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
          <h1 style={{ marginTop: 14 }}>{maquina.nombre}</h1>
          <p className="texto-suave">
            Este equipo está en mantenimiento y no se puede reservar por ahora. Vuelve a revisarlo en unos días.
          </p>
          <Link to="/catalogo" className="boton boton-secundario">← Ver otros equipos</Link>
        </div>
      </div>
    );
  }

  // HU-06: flujo de reserva por horas
  function reservar() {
    if (!usuario) {
      navegar('/login', { state: { desde: `/maquinas/${id}` } });
      return;
    }
    if (!usuario.tieneCelular) {
      navegar('/completar-registro', { state: { desde: `/maquinas/${id}` } });
      return;
    }
    // HU6 apagada (ALCANCE_HU < 6): con sesión iniciada solo se avisa, no se abre el formulario
    if (!HU6_ACTIVA) {
      setAvisoReserva(true);
      return;
    }
    setMostrarFormularioReserva(true);
  }

  const fotos = maquina.fotos;
  const especificaciones = Object.entries(maquina.especificaciones || {});

  return (
    <div className="contenedor">
      <nav className="migas" aria-label="Ruta">
        <Link to="/catalogo">Catálogo</Link> <span aria-hidden="true">/</span> <span>{maquina.categoria.nombre}</span>{' '}
        <span aria-hidden="true">/</span>
        <span style={{ color: 'var(--texto)' }} aria-current="page">{maquina.nombre}</span>
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
            <div className="dato">
              <span>Operador</span>
              <strong>Incluido</strong>
            </div>
            {maquina.combustiblePorHora > 0 && (
              <div className="dato">
                <span>Combustible</span>
                <strong>{formatearMoneda(maquina.combustiblePorHora)} / hora</strong>
                <small className="ayuda">Lo paga el cliente y se suma al total</small>
              </div>
            )}
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
            <p className="ayuda centrado" style={{ margin: '10px 0 0' }}>Necesitas iniciar sesión para reservar.</p>
          )}
          {avisoReserva && (
            <p className="ayuda centrado" role="status" style={{ margin: '10px 0 0' }}>
              La reserva en línea estará disponible próximamente.
            </p>
          )}
        </aside>
      </div>

      {HU6_ACTIVA && mostrarFormularioReserva && maquina && (
        <div
          className="modal-overlay"
          onClick={() => setMostrarFormularioReserva(false)}
          role="dialog"
          aria-modal="true"
          aria-labelledby="modal-titulo"
          onKeyDown={(e) => {
            if (e.key === 'Escape') setMostrarFormularioReserva(false);
          }}
        >
          <div className="modal-contenido" onClick={(e) => e.stopPropagation()} role="document">
            <FormularioReserva
              maquina={maquina}
              ocupados={ocupados}
              onExito={() => {
                setMostrarFormularioReserva(false);
                navegar('/mi-cuenta');
              }}
              onCerrar={() => setMostrarFormularioReserva(false)}
            />
          </div>
        </div>
      )}

      <style jsx>{`
        .modal-overlay {
          position: fixed;
          top: 0;
          left: 0;
          right: 0;
          bottom: 0;
          background: rgba(0, 0, 0, 0.5);
          display: flex;
          align-items: center;
          justify-content: center;
          z-index: 30;
          padding: 16px;
          backdrop-filter: blur(4px);
          -webkit-backdrop-filter: blur(4px);
          animation: fadeIn 0.2s ease-out;
        }
        @keyframes fadeIn {
          from { opacity: 0; }
          to { opacity: 1; }
        }
        .modal-contenido {
          background: var(--panel-arriba);
          border: 1px solid var(--borde);
          border-radius: var(--radio);
          padding: 28px;
          max-width: 600px;
          width: 100%;
          max-height: 88vh;
          overflow-y: auto;
          box-shadow: var(--sombra);
          backdrop-filter: blur(10px);
          -webkit-backdrop-filter: blur(10px);
          animation: slideUp 0.25s ease-out;
        }
        @keyframes slideUp {
          from { transform: translateY(20px); opacity: 0; }
          to { transform: translateY(0); opacity: 1; }
        }
        @media (max-width: 640px) {
          .modal-overlay {
            padding: 0;
            align-items: flex-end;
          }
          .modal-contenido {
            border-radius: 20px 20px 0 0;
            max-height: 90vh;
            padding: 24px 20px 32px;
            width: 100%;
          }
        }
        @media (max-height: 500px) {
          .modal-contenido {
            max-height: 95vh;
          }
        }
      `}</style>
    </div>
  );
}
