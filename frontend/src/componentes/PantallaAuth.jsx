// Diseño compartido de Login y Registro: panel visual a la izquierda y formulario a la derecha
import { Excavadora, Icono } from './Ilustracion';

const BENEFICIOS = [
  'Catálogo con tarifas claras por hora',
  'Disponibilidad en tiempo real',
  'Reserva y sigue tus alquileres en línea',
];

export default function PantallaAuth({ titulo, subtitulo, children }) {
  return (
    <div className="auth">
      <div className="auth-visual">
        <div>
          <h2>
            Tu obra no se detiene.
            <br />
            <span className="degradado">Tu maquinaria tampoco.</span>
          </h2>
          <p>Alquila equipos pesados en minutos, desde cualquier lugar.</p>
          <ul className="auth-beneficios">
            {BENEFICIOS.map((b) => (
              <li key={b}>
                <span className="check"><Icono nombre="check" tamanio={16} /></span>
                {b}
              </li>
            ))}
          </ul>
        </div>
        <Excavadora className="auth-ilustracion" />
      </div>

      <div className="auth-formulario">
        <div className="auth-tarjeta">
          <h1>{titulo}</h1>
          <p className="texto-suave">{subtitulo}</p>
          {children}
        </div>
      </div>
    </div>
  );
}
