// Panel del cliente (HU-02: destino tras iniciar sesión con rol Cliente)
import { Link } from 'react-router-dom';
import { useAuth } from '../contexto/AuthContext';
import { formatearCelular } from '../utils/formato';
import { Icono } from '../componentes/Ilustracion';

export default function MiCuenta() {
  const { usuario } = useAuth();
  return (
    <div className="contenedor contenedor-angosto">
      <div className="panel">
        <h1>Hola, {usuario.nombre.split(' ')[0]} 👋</h1>
        <dl className="datos-cuenta">
          <div>
            <dt>Correo</dt>
            <dd>{usuario.correo}</dd>
          </div>
          <div>
            <dt>Celular</dt>
            <dd>{usuario.telefono ? formatearCelular(usuario.telefono) : 'Sin registrar'}</dd>
          </div>
        </dl>
        <div className="tarjetas-accion">
          <Link to="/catalogo" className="tarjeta-accion">
            <span className="icono-circulo" aria-hidden="true"><Icono nombre="camion" /></span>
            <div>
              <strong>Explorar catálogo</strong>
              <span>Revisa la maquinaria disponible y sus tarifas.</span>
            </div>
          </Link>
          <div className="tarjeta-accion deshabilitada" title="Disponible en el Sprint 3" aria-disabled="true">
            <span className="icono-circulo" aria-hidden="true"><Icono nombre="calendario" /></span>
            <div>
              <strong>Mis reservas <span className="insignia insignia-gris">Próximamente</span></strong>
              <span>Aquí verás tus reservas y su estado.</span>
            </div>
          </div>
        </div>
      </div>
    </div>
  );
}
