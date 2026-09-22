// HU-01 Registrar cliente · HU-02 Iniciar y cerrar sesión · EN-03 Autenticación base
const bcrypt = require('bcryptjs');
const jwt = require('jsonwebtoken');
const config = require('../../config');
const { consultar } = require('../../db');
const { errores } = require('../../utils/errores');
const { ROLES } = require('../../middleware/autenticacion');

const RONDAS_BCRYPT = config.esPrueba ? 4 : 10;
const MENSAJE_CREDENCIALES = 'Correo o contraseña incorrectos'; // HU-02 criterio 2: mensaje genérico

// Hash de relleno: si el correo no existe igual comparamos, para que la
// respuesta tarde lo mismo y no se pueda adivinar qué correos existen.
const HASH_RELLENO = bcrypt.hashSync('contrasena-de-relleno', RONDAS_BCRYPT);

function usuarioPublico(fila) {
  return { id: fila.id, nombre: fila.nombre, correo: fila.correo, rol: fila.rol };
}

async function registrarCliente({ nombre, correo, contrasena }) {
  const existe = await consultar('SELECT 1 FROM usuarios WHERE correo = $1', [correo]);
  if (existe.rowCount > 0) {
    throw errores.conflicto('Ya existe una cuenta registrada con ese correo');
  }
  const hash = await bcrypt.hash(contrasena, RONDAS_BCRYPT);
  // El rol SIEMPRE es CLIENTE: aunque envíen "rol" en el cuerpo, se ignora (HU-01 criterio 5)
  const { rows } = await consultar(
    `INSERT INTO usuarios (nombre, correo, contrasena_hash, rol)
     VALUES ($1, $2, $3, $4)
     RETURNING id, nombre, correo, rol`,
    [nombre, correo, hash, ROLES.CLIENTE]
  );
  return usuarioPublico(rows[0]);
}

function minutosRestantes(fecha) {
  return Math.max(1, Math.ceil((fecha.getTime() - Date.now()) / 60000));
}

async function registrarIntentoFallido(usuarioId) {
  const { maxIntentosFallidos, minutosBloqueo } = config.login;
  // Si un bloqueo anterior ya venció, el contador vuelve a empezar desde 1
  const { rows } = await consultar(
    `UPDATE usuarios u
        SET intentos_fallidos = n.nuevo,
            bloqueado_hasta = CASE WHEN n.nuevo >= $2
                                   THEN now() + make_interval(mins => $3::int)
                                   ELSE NULL END
       FROM (SELECT id,
                    CASE WHEN bloqueado_hasta IS NOT NULL AND bloqueado_hasta <= now()
                         THEN 1 ELSE intentos_fallidos + 1 END AS nuevo
               FROM usuarios WHERE id = $1) n
      WHERE u.id = n.id
  RETURNING u.intentos_fallidos, u.bloqueado_hasta`,
    [usuarioId, maxIntentosFallidos, minutosBloqueo]
  );
  return rows[0];
}

async function iniciarSesion({ correo, contrasena, recordarme }, contexto = {}) {
  const { rows } = await consultar('SELECT * FROM usuarios WHERE correo = $1', [correo]);
  const usuario = rows[0];

  if (!usuario) {
    await bcrypt.compare(contrasena, HASH_RELLENO);
    throw errores.noAutenticado(MENSAJE_CREDENCIALES);
  }

  // HU-02 criterio 5: cuenta bloqueada temporalmente
  if (usuario.bloqueado_hasta && usuario.bloqueado_hasta.getTime() > Date.now()) {
    throw errores.bloqueado(
      `Cuenta bloqueada temporalmente por varios intentos fallidos. Intenta de nuevo en ${minutosRestantes(usuario.bloqueado_hasta)} minuto(s)`
    );
  }

  const correcta = await bcrypt.compare(contrasena, usuario.contrasena_hash);
  if (!correcta) {
    const estado = await registrarIntentoFallido(usuario.id);
    if (estado.bloqueado_hasta) {
      throw errores.bloqueado(
        `Cuenta bloqueada temporalmente por ${config.login.maxIntentosFallidos} intentos fallidos. Intenta de nuevo en ${config.login.minutosBloqueo} minutos`
      );
    }
    throw errores.noAutenticado(MENSAJE_CREDENCIALES);
  }

  // HU-15 criterio 2: un cliente desactivado no puede iniciar sesión
  if (!usuario.activo) {
    throw errores.prohibido('Tu cuenta está desactivada. Comunícate con el administrador');
  }

  await consultar(
    'UPDATE usuarios SET intentos_fallidos = 0, bloqueado_hasta = NULL WHERE id = $1',
    [usuario.id]
  );

  // HU-02 criterio 6: "Recordarme" extiende la sesión
  const s = config.sesion;
  const duracionMs = recordarme
    ? s.recordarmeDuracionDias * 24 * 60 * 60 * 1000
    : s.duracionHoras * 60 * 60 * 1000;
  const expiraEn = new Date(Date.now() + duracionMs);

  const sesion = await consultar(
    `INSERT INTO sesiones (usuario_id, recordarme, expira_en, ip, agente_usuario)
     VALUES ($1, $2, $3, $4, $5)
     RETURNING id`,
    [usuario.id, recordarme, expiraEn, contexto.ip || null, (contexto.agenteUsuario || '').slice(0, 255) || null]
  );

  const token = jwt.sign(
    { sid: sesion.rows[0].id, rol: usuario.rol },
    config.jwtSecreto,
    { subject: String(usuario.id), expiresIn: Math.floor(duracionMs / 1000) }
  );

  return {
    token,
    expiraEn: expiraEn.toISOString(),
    recordarme,
    usuario: usuarioPublico(usuario),
  };
}

/** HU-02 criterio 4: cerrar sesión invalida el token */
async function cerrarSesion(sesionId) {
  await consultar('UPDATE sesiones SET revocada_en = now() WHERE id = $1 AND revocada_en IS NULL', [sesionId]);
}

module.exports = { registrarCliente, iniciarSesion, cerrarSesion, MENSAJE_CREDENCIALES };
