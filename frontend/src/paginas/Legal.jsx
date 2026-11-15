import { Link } from 'react-router-dom';
import { CONTACTO, EMPRESA, NOMBRE_APP } from '../utils/formato';
import { TEXTO_TERMINOS } from '../utils/textoTerminos';

export function Terminos() {
  return (
    <div className="contenedor" style={{ maxWidth: 760, paddingTop: 40 }}>
      <div className="panel">
        <h1>Términos y condiciones</h1>
        <p className="texto-suave">
          {NOMBRE_APP} · {EMPRESA.razonSocial} · RUC {EMPRESA.ruc}
        </p>
        <div style={{ whiteSpace: 'pre-line', lineHeight: 1.6 }}>{TEXTO_TERMINOS.trim()}</div>
        <p className="texto-suave">
          Consulta también la <Link to="/cambios-y-devoluciones">política de cambios y devoluciones</Link> y el{' '}
          <Link to="/libro-de-reclamaciones">libro de reclamaciones</Link>.
        </p>
      </div>
    </div>
  );
}

export function Devoluciones() {
  return (
    <div className="contenedor" style={{ maxWidth: 760, paddingTop: 40 }}>
      <div className="panel" style={{ lineHeight: 1.6 }}>
        <h1>Política de cambios y devoluciones</h1>

        <h2>Cambio de fecha u horario</h2>
        <p>
          Puedes pedir el cambio de fecha u horario de tu reserva con al menos 24 horas de anticipación al inicio del
          alquiler. El cambio está sujeto a la disponibilidad de la máquina y no tiene costo.
        </p>

        <h2>Cancelación y devolución</h2>
        <p>
          Cancelación con 24 horas o más de anticipación: devolvemos el 100 % de lo pagado. Cancelación con menos de 24
          horas: devolvemos el 50 %. Si no te presentas en el horario reservado, no hay devolución.
        </p>

        <h2>Cancelación por parte de {NOMBRE_APP}</h2>
        <p>
          Si no podemos prestar el servicio por una falla de la máquina u otra causa nuestra, te ofrecemos reprogramar tu
          reserva o devolverte el 100 % de lo pagado, a tu elección.
        </p>

        <h2>Cómo se hace la devolución</h2>
        <p>
          La devolución se realiza por el mismo medio de pago usado en la reserva, en un plazo máximo de 15 días hábiles
          desde que se aprueba la solicitud. El tiempo en que se refleja en tu estado de cuenta depende de tu banco.
        </p>

        <h2>Cómo solicitarlo</h2>
        <p>
          Escríbenos a <a href={`mailto:${CONTACTO.correo}`}>{CONTACTO.correo}</a> o llámanos al{' '}
          <a href={`tel:${CONTACTO.telefono.replace(/\s+/g, '')}`}>{CONTACTO.telefono}</a>, indicando el código de tu
          reserva. Si no estás conforme con la respuesta, puedes registrar un reclamo en el{' '}
          <Link to="/libro-de-reclamaciones">libro de reclamaciones</Link>.
        </p>
      </div>
    </div>
  );
}
