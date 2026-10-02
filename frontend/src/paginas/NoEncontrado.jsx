import { Link } from 'react-router-dom';

export default function NoEncontrado() {
  return (
    <div className="contenedor contenedor-angosto">
      <div className="panel centrado">
        <h1>Página no encontrada</h1>
        <p className="texto-suave">La dirección que buscas no existe.</p>
        <div className="hero-acciones" style={{ justifyContent: 'center', marginBottom: 0 }}>
          <Link to="/catalogo" className="boton boton-primario">Ir al catálogo</Link>
          <Link to="/" className="boton boton-secundario">Ir al inicio</Link>
        </div>
      </div>
    </div>
  );
}
