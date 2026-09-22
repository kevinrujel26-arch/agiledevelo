// HU-03 Ver catálogo · HU-04 Ver detalle (base) · HU-08 Registrar y publicar máquina
const { consultar, transaccion } = require('../../db');
const config = require('../../config');
const { errores } = require('../../utils/errores');
const { limiteDesplazamiento, respuestaPaginada } = require('../../utils/paginacion');
const {
  esImagenReal,
  rutaPublica,
  borrarArchivoFoto,
  borrarArchivosSubidos,
} = require('../../middleware/subidaFotos');

const ESTADOS = Object.freeze({ BORRADOR: 'BORRADOR', PUBLICADA: 'PUBLICADA', RETIRADA: 'RETIRADA' });

// ---------------------------------------------------------------------
// Conversión fila de BD (snake_case) -> JSON de la API (camelCase)
// ---------------------------------------------------------------------
function mapearTarjeta(f) {
  return {
    id: f.id,
    nombre: f.nombre,
    marca: f.marca,
    modelo: f.modelo,
    categoria: { id: f.categoria_id, nombre: f.categoria_nombre },
    tarifaDiaria: f.tarifa_diaria,
    ubicacion: f.ubicacion,
    enMantenimiento: f.en_mantenimiento,
    fotoPrincipal: f.foto_principal || null,
  };
}

function mapearFoto(f) {
  return { id: f.id, url: f.ruta, esPrincipal: f.es_principal, orden: f.orden };
}

function mapearDetalle(f, fotos) {
  return {
    ...mapearTarjeta(f),
    descripcion: f.descripcion,
    especificaciones: f.especificaciones,
    estado: f.estado,
    publicadaEn: f.publicada_en,
    creadoEn: f.creado_en,
    actualizadoEn: f.actualizado_en,
    fotos: fotos.map(mapearFoto),
  };
}

const SELECT_MAQUINA = `
  SELECT m.*, c.nombre AS categoria_nombre,
         (SELECT f.ruta FROM fotos_maquina f
           WHERE f.maquina_id = m.id AND f.es_principal LIMIT 1) AS foto_principal
    FROM maquinas m
    JOIN categorias c ON c.id = m.categoria_id`;

async function obtenerFotos(maquinaId, cliente = null) {
  const ejecutar = cliente ? cliente.query.bind(cliente) : consultar;
  const { rows } = await ejecutar(
    `SELECT * FROM fotos_maquina WHERE maquina_id = $1
      ORDER BY es_principal DESC, orden, id`,
    [maquinaId]
  );
  return rows;
}

async function obtenerFila(id) {
  const { rows } = await consultar(`${SELECT_MAQUINA} WHERE m.id = $1`, [id]);
  return rows[0];
}

// ---------------------------------------------------------------------
// Catálogo público (HU-03)
// ---------------------------------------------------------------------
async function listarCatalogo(paginacion) {
  const { limite, desplazamiento } = limiteDesplazamiento(paginacion);
  const where = `WHERE m.estado = '${ESTADOS.PUBLICADA}'`;

  const [datos, conteo] = await Promise.all([
    consultar(
      `${SELECT_MAQUINA} ${where}
        ORDER BY m.publicada_en DESC NULLS LAST, m.id DESC
        LIMIT $1 OFFSET $2`,
      [limite, desplazamiento]
    ),
    consultar(`SELECT count(*) AS total FROM maquinas m ${where}`),
  ]);

  return respuestaPaginada(datos.rows.map(mapearTarjeta), conteo.rows[0].total, paginacion);
}

/** Detalle público: solo si está publicada (la URL se puede compartir, HU-04 criterio 4) */
async function obtenerPublica(id) {
  const fila = await obtenerFila(id);
  if (!fila || fila.estado !== ESTADOS.PUBLICADA) {
    throw errores.noEncontrado('La máquina no existe o ya no está disponible');
  }
  return mapearDetalle(fila, await obtenerFotos(id));
}

// ---------------------------------------------------------------------
// Administración (HU-08)
// ---------------------------------------------------------------------
async function listarAdmin({ estado, q, categoriaId, ...paginacion }) {
  const { limite, desplazamiento } = limiteDesplazamiento(paginacion);
  const condiciones = [];
  const params = [];

  if (estado) {
    params.push(estado);
    condiciones.push(`m.estado = $${params.length}`);
  }
  if (categoriaId) {
    params.push(categoriaId);
    condiciones.push(`m.categoria_id = $${params.length}`);
  }
  if (q) {
    params.push(`%${q.toLowerCase()}%`);
    condiciones.push(`(lower(m.nombre) LIKE $${params.length} OR lower(m.marca) LIKE $${params.length})`);
  }
  const where = condiciones.length ? `WHERE ${condiciones.join(' AND ')}` : '';

  const [datos, conteo] = await Promise.all([
    consultar(
      `${SELECT_MAQUINA} ${where}
        ORDER BY m.actualizado_en DESC, m.id DESC
        LIMIT $${params.length + 1} OFFSET $${params.length + 2}`,
      [...params, limite, desplazamiento]
    ),
    consultar(`SELECT count(*) AS total FROM maquinas m ${where}`, params),
  ]);

  const filas = datos.rows.map((f) => ({ ...mapearTarjeta(f), estado: f.estado, actualizadoEn: f.actualizado_en }));
  return respuestaPaginada(filas, conteo.rows[0].total, paginacion);
}

async function obtenerAdmin(id) {
  const fila = await obtenerFila(id);
  if (!fila) throw errores.noEncontrado('La máquina no existe');
  return mapearDetalle(fila, await obtenerFotos(id));
}

async function validarCategoria(categoriaId) {
  const { rows } = await consultar('SELECT activa FROM categorias WHERE id = $1', [categoriaId]);
  if (!rows[0]) throw errores.solicitudInvalida('La categoría seleccionada no existe');
  if (!rows[0].activa) throw errores.solicitudInvalida('La categoría seleccionada está desactivada');
}

/** Se guarda siempre como BORRADOR (HU-08 criterio 4) */
async function crear(datos, usuarioId) {
  await validarCategoria(datos.categoriaId);
  const { rows } = await consultar(
    `INSERT INTO maquinas (categoria_id, nombre, marca, modelo, descripcion, especificaciones,
                           tarifa_diaria, ubicacion, en_mantenimiento, creado_por)
     VALUES ($1, $2, $3, $4, $5, $6, $7, $8, $9, $10)
     RETURNING id`,
    [
      datos.categoriaId,
      datos.nombre,
      datos.marca,
      datos.modelo,
      datos.descripcion ?? null,
      JSON.stringify(datos.especificaciones ?? {}),
      datos.tarifaDiaria,
      datos.ubicacion,
      datos.enMantenimiento ?? false,
      usuarioId,
    ]
  );
  return obtenerAdmin(rows[0].id);
}

// Campo de la API -> columna de la BD
const COLUMNAS_EDITABLES = {
  categoriaId: 'categoria_id',
  nombre: 'nombre',
  marca: 'marca',
  modelo: 'modelo',
  descripcion: 'descripcion',
  especificaciones: 'especificaciones',
  tarifaDiaria: 'tarifa_diaria',
  ubicacion: 'ubicacion',
  enMantenimiento: 'en_mantenimiento',
};

/** HU-08 criterio 6: se puede editar incluso después de publicada */
async function actualizar(id, datos) {
  if (datos.categoriaId !== undefined) await validarCategoria(datos.categoriaId);

  const asignaciones = [];
  const params = [id];
  for (const [campo, columna] of Object.entries(COLUMNAS_EDITABLES)) {
    if (datos[campo] === undefined) continue;
    const valor = campo === 'especificaciones' ? JSON.stringify(datos[campo] ?? {}) : datos[campo];
    params.push(valor);
    asignaciones.push(`${columna} = $${params.length}`);
  }

  const { rowCount } = await consultar(
    `UPDATE maquinas SET ${asignaciones.join(', ')} WHERE id = $1`,
    params
  );
  if (rowCount === 0) throw errores.noEncontrado('La máquina no existe');
  return obtenerAdmin(id);
}

/** HU-08 criterios 3 y 5: publicar exige foto principal; aparece de inmediato en el catálogo */
async function publicar(id) {
  const maquina = await obtenerAdmin(id);
  if (maquina.estado === ESTADOS.PUBLICADA) return maquina;

  if (!maquina.fotos.some((f) => f.esPrincipal)) {
    throw errores.conflicto('Para publicar, sube al menos una foto y marca una como principal');
  }
  await validarCategoria(maquina.categoria.id);

  await consultar(
    `UPDATE maquinas SET estado = $2, publicada_en = now() WHERE id = $1`,
    [id, ESTADOS.PUBLICADA]
  );
  return obtenerAdmin(id);
}

/** HU-08 criterio 7: retirar del catálogo sin borrar el historial de reservas */
async function retirar(id) {
  const maquina = await obtenerAdmin(id);
  if (maquina.estado !== ESTADOS.PUBLICADA) {
    throw errores.conflicto('Solo se puede retirar una máquina publicada');
  }
  await consultar('UPDATE maquinas SET estado = $2 WHERE id = $1', [id, ESTADOS.RETIRADA]);
  return obtenerAdmin(id);
}

/** Solo se pueden eliminar borradores sin reservas; lo demás se retira. */
async function eliminar(id) {
  const maquina = await obtenerAdmin(id);
  if (maquina.estado !== ESTADOS.BORRADOR) {
    throw errores.conflicto('Solo se pueden eliminar borradores. Una máquina publicada se retira del catálogo');
  }
  const { rows } = await consultar('SELECT count(*) AS total FROM reservas WHERE maquina_id = $1', [id]);
  if (rows[0].total > 0) throw errores.conflicto('La máquina tiene reservas y no se puede eliminar');

  await consultar('DELETE FROM maquinas WHERE id = $1', [id]);
  await Promise.all(maquina.fotos.map((f) => borrarArchivoFoto(f.url)));
}

// ---------------------------------------------------------------------
// Fotos (HU-08 criterios 2 y 3)
// ---------------------------------------------------------------------
async function agregarFotos(maquinaId, archivos = []) {
  if (archivos.length === 0) throw errores.solicitudInvalida('Selecciona al menos una foto');
  const max = config.fotos.maxPorMaquina;

  try {
    for (const archivo of archivos) {
      if (!(await esImagenReal(archivo.path, archivo.mimetype))) {
        throw errores.solicitudInvalida(`"${archivo.originalname}" no es una imagen JPG o PNG válida`);
      }
    }

    await transaccion(async (cliente) => {
      const maquina = await cliente.query('SELECT id FROM maquinas WHERE id = $1 FOR UPDATE', [maquinaId]);
      if (maquina.rowCount === 0) throw errores.noEncontrado('La máquina no existe');

      const { rows } = await cliente.query(
        `SELECT count(*) AS total,
                coalesce(bool_or(es_principal), false) AS tiene_principal,
                coalesce(max(orden), 0) AS max_orden
           FROM fotos_maquina WHERE maquina_id = $1`,
        [maquinaId]
      );
      const { total, tiene_principal: tienePrincipal, max_orden: maxOrden } = rows[0];

      if (total + archivos.length > max) {
        const disponibles = max - total;
        throw errores.solicitudInvalida(
          disponibles > 0
            ? `La máquina ya tiene ${total} foto(s). Solo puedes subir ${disponibles} más (máximo ${max})`
            : `La máquina ya tiene el máximo de ${max} fotos`
        );
      }

      for (let i = 0; i < archivos.length; i++) {
        await cliente.query(
          `INSERT INTO fotos_maquina (maquina_id, ruta, tipo_mime, es_principal, orden)
           VALUES ($1, $2, $3, $4, $5)`,
          [
            maquinaId,
            rutaPublica(archivos[i].filename),
            archivos[i].mimetype,
            !tienePrincipal && i === 0, // la primera foto se vuelve principal si no había una
            maxOrden + i + 1,
          ]
        );
      }
    });
  } catch (error) {
    await borrarArchivosSubidos(archivos);
    throw error;
  }

  return (await obtenerFotos(maquinaId)).map(mapearFoto);
}

async function marcarPrincipal(maquinaId, fotoId) {
  await transaccion(async (cliente) => {
    const { rowCount } = await cliente.query(
      'SELECT 1 FROM fotos_maquina WHERE id = $1 AND maquina_id = $2',
      [fotoId, maquinaId]
    );
    if (rowCount === 0) throw errores.noEncontrado('La foto no existe');
    await cliente.query(
      'UPDATE fotos_maquina SET es_principal = false WHERE maquina_id = $1 AND es_principal',
      [maquinaId]
    );
    await cliente.query('UPDATE fotos_maquina SET es_principal = true WHERE id = $1', [fotoId]);
  });
  return (await obtenerFotos(maquinaId)).map(mapearFoto);
}

async function eliminarFoto(maquinaId, fotoId) {
  const ruta = await transaccion(async (cliente) => {
    const { rows: maq } = await cliente.query(
      'SELECT estado FROM maquinas WHERE id = $1 FOR UPDATE',
      [maquinaId]
    );
    if (!maq[0]) throw errores.noEncontrado('La máquina no existe');

    const fotos = await obtenerFotos(maquinaId, cliente);
    const foto = fotos.find((f) => f.id === fotoId);
    if (!foto) throw errores.noEncontrado('La foto no existe');

    if (maq[0].estado === ESTADOS.PUBLICADA && fotos.length === 1) {
      throw errores.conflicto('Una máquina publicada debe tener al menos una foto');
    }

    await cliente.query('DELETE FROM fotos_maquina WHERE id = $1', [fotoId]);

    // Si se borró la principal, la siguiente pasa a ser principal
    if (foto.es_principal) {
      const siguiente = fotos.find((f) => f.id !== fotoId);
      if (siguiente) {
        await cliente.query('UPDATE fotos_maquina SET es_principal = true WHERE id = $1', [siguiente.id]);
      }
    }
    return foto.ruta;
  });

  await borrarArchivoFoto(ruta);
  return (await obtenerFotos(maquinaId)).map(mapearFoto);
}

module.exports = {
  ESTADOS,
  listarCatalogo,
  obtenerPublica,
  listarAdmin,
  obtenerAdmin,
  crear,
  actualizar,
  publicar,
  retirar,
  eliminar,
  agregarFotos,
  marcarPrincipal,
  eliminarFoto,
};
