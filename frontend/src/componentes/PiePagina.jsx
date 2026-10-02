import { Link } from 'react-router-dom';
import { CONTACTO, NOMBRE_APP } from '../utils/formato';
import { LogoMarca } from './Ilustracion';

export default function PiePagina() {
  return (
    <footer className="pie">
      <div className="contenedor">
        <div className="pie-rejilla">
          <div>
            <Link to="/" className="marca">
              <LogoMarca />
              {NOMBRE_APP}
            </Link>
            <p className="pie-descripcion">
              Alquiler de maquinaria pesada por horas. Compara, revisa la disponibilidad y reserva en línea.
            </p>
          </div>
          <nav aria-label="Explorar">
            <h2>Explorar</h2>
            <ul>
              <li><Link to="/">Inicio</Link></li>
              <li><Link to="/catalogo">Catálogo</Link></li>
              <li><a href="/#contacto">Contacto</a></li>
            </ul>
          </nav>
          <nav aria-label="Cuenta">
            <h2>Cuenta</h2>
            <ul>
              <li><Link to="/login">Iniciar sesión</Link></li>
              <li><Link to="/registro">Crear cuenta</Link></li>
            </ul>
          </nav>
          {/* HU-01 criterio 5: datos de contacto de la empresa */}
          <div>
            <h2>Contacto</h2>
            <ul>
              <li><a href={`mailto:${CONTACTO.correo}`}>{CONTACTO.correo}</a></li>
              <li><a href={`tel:${CONTACTO.telefono.replace(/\s+/g, '')}`}>{CONTACTO.telefono}</a></li>
              <li>{CONTACTO.direccion}</li>
            </ul>
          </div>
        </div>
        <div className="pie-legal">
          <span>© {new Date().getFullYear()} {NOMBRE_APP}. Todos los derechos reservados.</span>
          <span>Proyecto académico · Agile Development · UPAO</span>
        </div>
      </div>
    </footer>
  );
}
