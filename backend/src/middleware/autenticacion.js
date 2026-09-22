// EN-03 (autenticación con tokens) y EN-05 (control de acceso por rol)
const jwt = require('jsonwebtoken');
const config = require('../config');
const { consultar } = require('../db');
const { errores } = require('../utils/errores');

const ROLES = Object.freeze({ CLIENTE: 'CLIENTE', ADMINISTRADOR: 'ADMINISTRADOR' });

/** Solo actualiza ultima_actividad si pasó más de 1 minuto (evita escribir en cada petición). */
const INTERVALO_ACTUALIZAR_ACTIVIDAD_MS = 60 * 1000;

function limiteInactividadMs(recordarme) {
  const s = config.sesion;
  return recordarme
    ? s.recordarmeInactividadDias * 24 * 60 * 60 * 1000
    : s.inactividadMinutos * 60 * 1000;
}

/**
 * Exige un token válido: "Authorization: Bearer <token>".
 * Verifica la firma del JWT y además que su sesión siga vigente en la BD
 * (no cerrada, no vencida, sin exceso de inactividad y usuario activo).
 */
async function autenticar(req, _res, next) {
  const cabecera = req.headers.authorization || '';
  const [tipo, token] = cabecera.split(' ');
  if (tipo !== 'Bearer' || !token) throw errores.noAutenticado();

  let datosToken;
  try {
    datosToken = jwt.verify(token, config.jwtSecreto);
  } catch {
    throw errores.noAutenticado('Tu sesión expiró. Inicia sesión nuevamente');
  }

  const { rows } = await consultar(
    `SELECT s.id, s.recordarme, s.ultima_actividad, s.expira_en, s.revocada_en,
            u.id AS usuario_id, u.nombre, u.correo, u.rol, u.activo
       FROM sesiones s
       JOIN usuarios u ON u.id = s.usuario_id
      WHERE s.id = $1`,
    [datosToken.sid]
  );
  const sesion = rows[0];
  const ahora = Date.now();

  if (!sesion || sesion.revocada_en || sesion.expira_en.getTime() <= ahora) {
    throw errores.noAutenticado('Tu sesión expiró. Inicia sesión nuevamente');
  }
  if (!sesion.activo) {
    throw errores.noAutenticado('Tu cuenta está desactivada');
  }

  const inactivo = ahora - sesion.ultima_actividad.getTime();
  if (inactivo > limiteInactividadMs(sesion.recordarme)) {
    await consultar('UPDATE sesiones SET revocada_en = now() WHERE id = $1', [sesion.id]);
    throw errores.noAutenticado('Tu sesión expiró por inactividad. Inicia sesión nuevamente');
  }
  if (inactivo > INTERVALO_ACTUALIZAR_ACTIVIDAD_MS) {
    await consultar('UPDATE sesiones SET ultima_actividad = now() WHERE id = $1', [sesion.id]);
  }

  req.sesionId = sesion.id;
  req.usuario = {
    id: sesion.usuario_id,
    nombre: sesion.nombre,
    correo: sesion.correo,
    rol: sesion.rol,
  };
  next();
}

/** Restringe la ruta a uno o más roles. Usar después de autenticar. */
function autorizar(...rolesPermitidos) {
  return (req, _res, next) => {
    if (!req.usuario) return next(errores.noAutenticado());
    if (!rolesPermitidos.includes(req.usuario.rol)) return next(errores.prohibido());
    next();
  };
}

module.exports = { autenticar, autorizar, ROLES };
