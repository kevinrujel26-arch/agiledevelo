const config = require('../config');

/** Fecha de hoy 'YYYY-MM-DD' en la zona horaria del negocio (por defecto Lima). */
function hoy(zona = config.zonaHoraria) {
  // en-CA formatea como YYYY-MM-DD
  return new Intl.DateTimeFormat('en-CA', {
    timeZone: zona,
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
  }).format(new Date());
}

/** true si el texto es una fecha real con formato YYYY-MM-DD. */
function esFechaValida(texto) {
  if (typeof texto !== 'string' || !/^\d{4}-\d{2}-\d{2}$/.test(texto)) return false;
  const [a, m, d] = texto.split('-').map(Number);
  const fecha = new Date(Date.UTC(a, m - 1, d));
  return fecha.getUTCFullYear() === a && fecha.getUTCMonth() === m - 1 && fecha.getUTCDate() === d;
}

/** Rangos inclusivos [inicio, fin] que comparten al menos un día. */
function rangosSeSuperponen(a, b) {
  return a.fechaInicio <= b.fechaFin && b.fechaInicio <= a.fechaFin;
}

/** Devuelve el primer par de rangos de la lista que se superponen, o null. */
function buscarSuperposicion(rangos) {
  const ordenados = [...rangos].sort((x, y) => x.fechaInicio.localeCompare(y.fechaInicio));
  for (let i = 1; i < ordenados.length; i++) {
    if (rangosSeSuperponen(ordenados[i - 1], ordenados[i])) {
      return [ordenados[i - 1], ordenados[i]];
    }
  }
  return null;
}

/** Cantidad de días de un rango inclusivo (2026-10-01 a 2026-10-01 = 1 día). */
function diasEntre(fechaInicio, fechaFin) {
  const ms = Date.parse(`${fechaFin}T00:00:00Z`) - Date.parse(`${fechaInicio}T00:00:00Z`);
  return Math.round(ms / 86400000) + 1;
}

module.exports = { hoy, esFechaValida, rangosSeSuperponen, buscarSuperposicion, diasEntre };
