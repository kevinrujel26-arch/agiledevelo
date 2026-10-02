import { Link } from 'react-router-dom';

export default function NoEncontrado() {
  return (
    <div className="contenedor contenedor-angosto">
      <div className="panel centrado">
        <p className="codigo-error" aria-hidden="true">404</p>
        <h1>Página no encontrada</h1>
        <p className="texto-suave">La dirección que buscas no existe.</p>
        <div className="acciones-centradas">
          <Link to="/catalogo" className="boton boton-primario">Ir al catálogo</Link>
          <Link to="/" className="boton boton-secundario">Ir al inicio</Link>
        </div>
      </div>
    </div>
  );
}
