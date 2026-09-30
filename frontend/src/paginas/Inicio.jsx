// HU-01 Página de inicio pública
import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { api } from '../api/cliente';
import { useAuth, panelSegunRol } from '../contexto/AuthContext';
import { Alerta, Cargando, TarjetaMaquina } from '../componentes/comunes';
import { Excavadora, Icono, LogoMarca } from '../componentes/Ilustracion';
import { CONTACTO, NOMBRE_APP } from '../utils/formato';

const TAMANIO_DESTACADAS = 3;

const PASOS = [
  { titulo: 'Explora el catálogo', texto: 'Compara equipos, especificaciones técnicas y tarifas por hora en un solo lugar.' },
  { titulo: 'Elige tus fechas', texto: 'Revisa la disponibilidad en el calendario y reserva cuando la necesites.' },
  { titulo: 'Recibe en tu obra', texto: 'Confirmamos tu reserva y coordinamos la entrega del equipo donde lo necesites.' },
];

export default function Inicio() {
  const { usuario } = useAuth();
  const [destacadas, setDestacadas] = useState(null);
  const [error, setError] = useState('');

  // Máquinas destacadas: las primeras del catálogo público
  useEffect(() => {
    let vigente = true;
    api
      .get('/maquinas', { tamanio: TAMANIO_DESTACADAS })
      .then((r) => vigente && setDestacadas(r.datos))
      .catch((e) => vigente && setError(e.message));
    return () => {
      vigente = false;
    };
  }, []);

  return (
    <>
      {/* ------------------------------ Portada ------------------------------ */}
      <section className="hero">
        <div className="contenedor hero-interior">
          <div>
            <div className="marca inicio-marca">
              <LogoMarca />
              {NOMBRE_APP}
            </div>
            <span className="hero-etiqueta">
              <span className="punto-vivo" /> Alquiler de maquinaria pesada por hora
            </span>
            <h1>
              Maquinaria pesada,
              <br />
              <span className="degradado">a un clic.</span>
            </h1>
            <p className="hero-texto">
              Alquila excavadoras, cargadores, rodillos y más por hora, desde una hora de uso. Compara tarifas, revisa
              la disponibilidad y reserva sin llamadas ni trámites.
            </p>
            <div className="hero-acciones">
              <Link to="/catalogo" className="boton boton-primario boton-grande">
                Ver catálogo <Icono nombre="flecha" />
              </Link>
              {usuario ? (
                <Link to={panelSegunRol(usuario)} className="boton boton-secundario boton-grande">
                  Ir a mi panel
                </Link>
              ) : (
                <Link to="/login" className="boton boton-secundario boton-grande">
                  Iniciar sesión
                </Link>
              )}
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

      {/* ------------------------- Máquinas destacadas ------------------------- */}
      <section className="seccion">
        <div className="contenedor">
          <div className="seccion-cabecera">
            <div>
              <div className="seccion-sobretitulo">Destacadas</div>
              <h2>Equipos listos para tu obra</h2>
            </div>
            <Link to="/catalogo" className="boton boton-secundario">
              Ver todo el catálogo <Icono nombre="flecha" />
            </Link>
          </div>

          <Alerta>{error}</Alerta>
          {!destacadas && !error && <Cargando texto="Cargando equipos…" />}
          {destacadas && destacadas.length === 0 && (
            <div className="vacio">
              <h2>Aún no hay máquinas publicadas</h2>
              <p className="texto-suave">Vuelve pronto: estamos preparando nuestra flota.</p>
            </div>
          )}
          {destacadas && destacadas.length > 0 && (
            <div className="rejilla-catalogo">
              {destacadas.map((m) => (
                <TarjetaMaquina key={m.id} maquina={m} />
              ))}
            </div>
          )}
        </div>
      </section>

      {/* ------------------------------ Contacto ------------------------------ */}
      {/* HU-01 criterio 5: datos de contacto de la empresa */}
      <section className="seccion" id="contacto">
        <div className="contenedor">
          <div className="seccion-cabecera">
            <div>
              <div className="seccion-sobretitulo">Contacto</div>
              <h2>¿Tienes dudas? Escríbenos</h2>
            </div>
          </div>
          <div className="contacto-rejilla">
            <a className="contacto-item" href={`mailto:${CONTACTO.correo}`}>
              <span>Correo</span>
              <strong>{CONTACTO.correo}</strong>
            </a>
            <a className="contacto-item" href={`tel:${CONTACTO.telefono.replace(/\s+/g, '')}`}>
              <span>Teléfono</span>
              <strong>{CONTACTO.telefono}</strong>
            </a>
            <div className="contacto-item">
              <span>Dirección</span>
              <strong>{CONTACTO.direccion}</strong>
            </div>
          </div>
        </div>
      </section>
    </>
  );
}
