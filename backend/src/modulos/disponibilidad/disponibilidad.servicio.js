// HU-09 Gestionar disponibilidad (bloqueo de fechas)
const { consultar, transaccion } = require('../../db');
const { errores } = require('../../utils/errores');
const { hoy, buscarSuperposicion } = require('../../utils/fechas');

const ESTADOS_RESERVA_OCUPAN = ['PENDIENTE_PAGO', 'PAGADA'];

function mapearBloqueo(f) {
  return {
    id: f.id,
    fechaInicio: f.fecha_inicio,
    fechaFin: f.fecha_fin,
    motivo: f.motivo,
    creadoPor: f.creado_por_nombre ?? null,
    creadoEn: f.creado_en,
  };
}

async function asegurarMaquina(maquinaId, cliente = null) {
  const ejecutar = cliente ? cliente.query.bind(cliente) : consultar;
  const { rows } = await ejecutar(
    `SELECT id, estado FROM maquinas WHERE id = $1 ${cliente ? 'FOR UPDATE' : ''}`,
    [maquinaId]
  );
  if (!rows[0]) throw errores.noEncontrado('La máquina no existe');
  return rows[0];
}

async function listarBloqueos(maquinaId, { incluirPasados = false } = {}) {
  await asegurarMaquina(maquinaId);
  const { rows } = await consultar(
    `SELECT b.*, u.nombre AS creado_por_nombre
       FROM bloqueos_disponibilidad b
       LEFT JOIN usuarios u ON u.id = b.creado_por
      WHERE b.maquina_id = $1 AND ($2::boolean OR b.fecha_fin >= $3::date)
      ORDER BY b.fecha_inicio`,
    [maquinaId, incluirPasados, hoy()]
  );
  return rows.map(mapearBloqueo);
}

/**
 * Bloquea uno o varios rangos (HU-09 criterio 1) en una sola transacción:
 * o se crean todos, o ninguno.
 */
async function bloquear(maquinaId, { rangos, motivo }, usuarioId) {
  const fechaHoy = hoy();
  const pasado = rangos.find((r) => r.fechaInicio < fechaHoy);
  if (pasado) {
    throw errores.solicitudInvalida(`No se pueden bloquear fechas pasadas (${pasado.fechaInicio})`);
  }
  const cruce = buscarSuperposicion(rangos);
  if (cruce) {
    throw errores.solicitudInvalida(
      `Los rangos ${cruce[0].fechaInicio}–${cruce[0].fechaFin} y ${cruce[1].fechaInicio}–${cruce[1].fechaFin} se superponen entre sí`
    );
  }

  const creados = await transaccion(async (cliente) => {
    await asegurarMaquina(maquinaId, cliente);

    // HU-09 criterio 3: no se puede bloquear una fecha con una reserva ya pagada
    for (const r of rangos) {
      const { rows } = await cliente.query(
        `SELECT fecha_inicio, fecha_fin FROM reservas
          WHERE maquina_id = $1 AND estado = 'PAGADA'
            AND daterange(fecha_inicio, fecha_fin, '[]') && daterange($2::date, $3::date, '[]')
          ORDER BY fecha_inicio LIMIT 1`,
        [maquinaId, r.fechaInicio, r.fechaFin]
      );
      if (rows[0]) {
        throw errores.conflicto(
          `No se puede bloquear: hay una reserva pagada del ${rows[0].fecha_inicio} al ${rows[0].fecha_fin}`
        );
      }
    }

    const filas = [];
    for (const r of rangos) {
      const { rows } = await cliente.query(
        `INSERT INTO bloqueos_disponibilidad (maquina_id, fecha_inicio, fecha_fin, motivo, creado_por)
         VALUES ($1, $2, $3, $4, $5)
         RETURNING *`,
        [maquinaId, r.fechaInicio, r.fechaFin, motivo ?? null, usuarioId]
      );
      filas.push(rows[0]);
    }
    return filas;
  });

  return creados.map(mapearBloqueo);
}

/** HU-09 criterio 4: desbloquear fechas bloqueadas antes */
async function desbloquear(maquinaId, bloqueoId) {
  const { rowCount } = await consultar(
    'DELETE FROM bloqueos_disponibilidad WHERE id = $1 AND maquina_id = $2',
    [bloqueoId, maquinaId]
  );
  if (rowCount === 0) throw errores.noEncontrado('El bloqueo no existe');
}

/**
 * Fechas ocupadas de una máquina publicada (para el calendario público).
 * HU-09 criterio 5: los cambios se ven de inmediato porque se consulta en vivo.
 * No se expone el motivo del bloqueo ni quién reservó.
 */
async function ocupacionPublica(maquinaId, { desde, hasta }) {
  const maquina = await asegurarMaquina(maquinaId);
  if (maquina.estado !== 'PUBLICADA') {
    throw errores.noEncontrado('La máquina no existe o ya no está disponible');
  }
  const { rows } = await consultar(
    `SELECT fecha_inicio, fecha_fin, 'BLOQUEO' AS tipo
       FROM bloqueos_disponibilidad
      WHERE maquina_id = $1 AND daterange(fecha_inicio, fecha_fin, '[]') && daterange($2::date, $3::date, '[]')
     UNION ALL
     SELECT fecha_inicio, fecha_fin, 'RESERVA' AS tipo
       FROM reservas
      WHERE maquina_id = $1 AND estado = ANY($4::text[])
        AND daterange(fecha_inicio, fecha_fin, '[]') && daterange($2::date, $3::date, '[]')
     ORDER BY fecha_inicio`,
    [maquinaId, desde, hasta, ESTADOS_RESERVA_OCUPAN]
  );
  return {
    maquinaId,
    desde,
    hasta,
    ocupados: rows.map((f) => ({ fechaInicio: f.fecha_inicio, fechaFin: f.fecha_fin, tipo: f.tipo })),
  };
}

module.exports = { listarBloqueos, bloquear, desbloquear, ocupacionPublica };
