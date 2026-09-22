import { Link } from 'react-router-dom';

export default function NoEncontrado() {
  return (
    <div className="contenedor contenedor-angosto">
      <div className="panel centrado">
        <h1>Página no encontrada</h1>
        <p className="texto-suave">La dirección que buscas no existe.</p>
        <Link to="/" className="boton boton-primario">Ir al catálogo</Link>
      </div>
    </div>
  );
}
