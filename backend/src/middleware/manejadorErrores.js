const multer = require('multer');
const { ZodError } = require('zod');
const { ErrorApp } = require('../utils/errores');
const { detallesZod } = require('./validar');
const config = require('../config');

// Restricciones de la BD -> mensaje entendible para el usuario
const MENSAJES_RESTRICCION = {
  ux_usuarios_correo: [409, 'Ya existe una cuenta registrada con ese correo'],
  ux_categorias_nombre: [409, 'Ya existe una categoría con ese nombre'],
  ux_fotos_una_principal: [409, 'La máquina ya tiene una foto principal'],
  ck_max_5_fotos: [400, `Una máquina puede tener como máximo ${config.fotos.maxPorMaquina} fotos`],
  ex_bloqueos_sin_superposicion: [409, 'El rango se superpone con otro bloqueo existente de esta máquina'],
  ex_reservas_sin_superposicion: [409, 'Las fechas elegidas ya están ocupadas'],
};

const MENSAJES_MULTER = {
  LIMIT_FILE_SIZE: `Cada foto puede pesar como máximo ${config.fotos.maxBytes / (1024 * 1024)} MB`,
  LIMIT_FILE_COUNT: `Puedes subir como máximo ${config.fotos.maxPorMaquina} fotos`,
  LIMIT_UNEXPECTED_FILE: 'Envía las fotos en el campo "fotos"',
};

function rutaNoEncontrada(req, res) {
  res.status(404).json({ error: `No existe la ruta ${req.method} ${req.originalUrl}` });
}

// eslint-disable-next-line no-unused-vars
function manejadorErrores(err, req, res, _next) {
  if (err instanceof ErrorApp) {
    return res.status(err.estadoHttp).json({ error: err.message, detalles: err.detalles });
  }
  if (err instanceof ZodError) {
    return res.status(400).json({ error: 'Datos inválidos', detalles: detallesZod(err) });
  }
  if (err instanceof multer.MulterError) {
    return res.status(400).json({ error: MENSAJES_MULTER[err.code] || err.message });
  }
  if (err.type === 'entity.parse.failed') {
    return res.status(400).json({ error: 'El cuerpo de la petición no es un JSON válido' });
  }
  if (err.type === 'entity.too.large') {
    return res.status(413).json({ error: 'La petición es demasiado grande' });
  }
  // Errores de PostgreSQL
  if (err.constraint && MENSAJES_RESTRICCION[err.constraint]) {
    const [estado, mensaje] = MENSAJES_RESTRICCION[err.constraint];
    return res.status(estado).json({ error: mensaje });
  }
  // Errores HTTP de librerías (p. ej. express.static) que ya traen su código 4xx
  const estado = err.status || err.statusCode;
  if (estado >= 400 && estado < 500) {
    return res.status(estado).json({ error: err.expose ? err.message : 'Solicitud inválida' });
  }
  if (err.code === '23503') {
    return res.status(409).json({ error: 'El registro está relacionado con otros datos y no se puede modificar así' });
  }

  if (!config.esPrueba) console.error(err);
  return res.status(500).json({ error: 'Ocurrió un error inesperado. Intenta nuevamente' });
}

module.exports = { manejadorErrores, rutaNoEncontrada };
