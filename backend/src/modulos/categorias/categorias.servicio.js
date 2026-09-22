// HU-14 Gestionar categorías de maquinaria
const { consultar } = require('../../db');
const { errores } = require('../../utils/errores');

function mapear(fila) {
  const categoria = {
    id: fila.id,
    nombre: fila.nombre,
    descripcion: fila.descripcion,
    activa: fila.activa,
  };
  if (fila.total_maquinas !== undefined) {
    categoria.totalMaquinas = fila.total_maquinas;
    categoria.maquinasPublicadas = fila.maquinas_publicadas;
  }
  return categoria;
}

/** Catálogo público: solo categorías activas (HU-14 criterio 3) */
async function listarActivas() {
  const { rows } = await consultar(
    'SELECT id, nombre, descripcion, activa FROM categorias WHERE activa ORDER BY nombre'
  );
  return rows.map(mapear);
}

/** Panel de administración: todas, con cuántas máquinas tiene cada una */
async function listarTodas() {
  const { rows } = await consultar(
    `SELECT c.id, c.nombre, c.descripcion, c.activa,
            count(m.id) AS total_maquinas,
            count(m.id) FILTER (WHERE m.estado = 'PUBLICADA') AS maquinas_publicadas
       FROM categorias c
       LEFT JOIN maquinas m ON m.categoria_id = c.id
      GROUP BY c.id
      ORDER BY c.nombre`
  );
  return rows.map(mapear);
}

async function nombreEnUso(nombre, excluirId = null) {
  const { rowCount } = await consultar(
    `SELECT 1 FROM categorias
      WHERE lower(trim(nombre)) = lower(trim($1)) AND ($2::int IS NULL OR id <> $2)`,
    [nombre, excluirId]
  );
  return rowCount > 0;
}

async function crear({ nombre, descripcion }) {
  if (await nombreEnUso(nombre)) throw errores.conflicto('Ya existe una categoría con ese nombre');
  const { rows } = await consultar(
    'INSERT INTO categorias (nombre, descripcion) VALUES ($1, $2) RETURNING *',
    [nombre, descripcion ?? null]
  );
  return mapear(rows[0]);
}

async function actualizar(id, { nombre, descripcion }) {
  if (nombre !== undefined && (await nombreEnUso(nombre, id))) {
    throw errores.conflicto('Ya existe una categoría con ese nombre');
  }
  const { rows } = await consultar(
    `UPDATE categorias
        SET nombre = COALESCE($2, nombre),
            descripcion = CASE WHEN $3::boolean THEN $4 ELSE descripcion END
      WHERE id = $1
  RETURNING *`,
    [id, nombre ?? null, descripcion !== undefined, descripcion ?? null]
  );
  if (!rows[0]) throw errores.noEncontrado('La categoría no existe');
  return mapear(rows[0]);
}

async function cambiarEstado(id, activa) {
  const { rows } = await consultar(
    'UPDATE categorias SET activa = $2 WHERE id = $1 RETURNING *',
    [id, activa]
  );
  if (!rows[0]) throw errores.noEncontrado('La categoría no existe');
  return mapear(rows[0]);
}

/** HU-14 criterio 2: si tiene máquinas no se elimina, solo se desactiva */
async function eliminar(id) {
  const { rows } = await consultar(
    'SELECT count(*) AS total FROM maquinas WHERE categoria_id = $1',
    [id]
  );
  if (rows[0].total > 0) {
    throw errores.conflicto(
      'La categoría tiene máquinas registradas y no se puede eliminar. Desactívala en su lugar'
    );
  }
  const { rowCount } = await consultar('DELETE FROM categorias WHERE id = $1', [id]);
  if (rowCount === 0) throw errores.noEncontrado('La categoría no existe');
}

module.exports = { listarActivas, listarTodas, crear, actualizar, cambiarEstado, eliminar };
