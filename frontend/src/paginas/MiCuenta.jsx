// Panel del cliente (HU-02: destino tras iniciar sesión con rol Cliente)
import { Link } from 'react-router-dom';
import { useAuth } from '../contexto/AuthContext';

export default function MiCuenta() {
  const { usuario } = useAuth();
  return (
    <div className="contenedor contenedor-angosto">
      <div className="panel">
        <h1>Hola, {usuario.nombre.split(' ')[0]} 👋</h1>
        <p className="texto-suave">{usuario.correo}</p>
        <div className="tarjetas-accion">
          <Link to="/" className="tarjeta-accion">
            <strong>Explorar catálogo</strong>
            <span>Revisa la maquinaria disponible y sus tarifas.</span>
          </Link>
          <div className="tarjeta-accion deshabilitada" title="Disponible en el Sprint 3">
            <strong>Mis reservas</strong>
            <span>Próximamente: aquí verás tus reservas y su estado.</span>
          </div>
        </div>
      </div>
    </div>
  );
}
