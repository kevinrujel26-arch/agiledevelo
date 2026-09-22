/**
 * Carga datos iniciales: el administrador, categorías y algunas máquinas de ejemplo.
 * Se puede ejecutar varias veces sin duplicar datos.
 *   npm run db:sembrar
 */
const bcrypt = require('bcryptjs');
const { pool, transaccion } = require('../src/db');

const CATEGORIAS = [
  ['Excavadoras', 'Excavadoras sobre orugas y sobre ruedas'],
  ['Retroexcavadoras', 'Equipos de carga frontal y excavación trasera'],
  ['Cargadores frontales', 'Cargadores de ruedas para movimiento de material'],
  ['Rodillos compactadores', 'Compactación de suelos y asfalto'],
  ['Montacargas', 'Elevación y traslado de carga en almacén u obra'],
  ['Minicargadores', 'Equipos compactos para espacios reducidos'],
];

// [categoría, nombre, marca, modelo, tarifa, ubicación, estado, especificaciones]
const MAQUINAS = [
  ['Excavadoras', 'Excavadora hidráulica 320', 'Caterpillar', '320 GC', 1450, 'Trujillo', 'PUBLICADA',
    { 'Peso operativo': '22 t', Potencia: '146 HP', 'Capacidad del cucharón': '1.2 m³' }],
  ['Excavadoras', 'Miniexcavadora 35G', 'John Deere', '35G', 620, 'Trujillo', 'PUBLICADA',
    { 'Peso operativo': '3.6 t', Potencia: '23 HP', 'Profundidad de excavación': '3.4 m' }],
  ['Retroexcavadoras', 'Retroexcavadora 416F2', 'Caterpillar', '416F2', 780, 'Chiclayo', 'PUBLICADA',
    { Potencia: '87 HP', Tracción: '4x4', 'Capacidad del cargador': '1 m³' }],
  ['Cargadores frontales', 'Cargador frontal 950GC', 'Caterpillar', '950 GC', 1300, 'Lima', 'PUBLICADA',
    { Potencia: '225 HP', 'Capacidad del cucharón': '3.1 m³' }],
  ['Rodillos compactadores', 'Rodillo vibratorio CA2500', 'Dynapac', 'CA2500D', 690, 'Trujillo', 'PUBLICADA',
    { 'Peso operativo': '10.5 t', 'Ancho de tambor': '2.13 m' }],
  ['Montacargas', 'Montacargas diésel 3 t', 'Toyota', '8FD30', 280, 'Lima', 'PUBLICADA',
    { Capacidad: '3000 kg', 'Altura de elevación': '4.7 m', Combustible: 'Diésel' }],
  ['Minicargadores', 'Minicargador S650', 'Bobcat', 'S650', 450, 'Piura', 'BORRADOR',
    { Potencia: '74 HP', 'Carga operativa': '1250 kg' }],
];

async function sembrar() {
  await transaccion(async (c) => {
    // Administrador
    const correoAdmin = (process.env.ADMIN_CORREO || 'admin@alquiler.pe').toLowerCase();
    const contrasenaAdmin = process.env.ADMIN_CONTRASENA || 'Admin12345';
    const hash = await bcrypt.hash(contrasenaAdmin, 10);
    const admin = await c.query(
      `INSERT INTO usuarios (nombre, correo, contrasena_hash, rol)
       VALUES ($1, $2, $3, 'ADMINISTRADOR')
       ON CONFLICT (correo) DO UPDATE SET rol = 'ADMINISTRADOR'
       RETURNING id`,
      [process.env.ADMIN_NOMBRE || 'Administrador', correoAdmin, hash]
    );
    const adminId = admin.rows[0].id;
    console.log(`✔ Administrador: ${correoAdmin}`);

    // Categorías
    const idsCategoria = {};
    for (const [nombre, descripcion] of CATEGORIAS) {
      const existente = await c.query('SELECT id FROM categorias WHERE lower(nombre) = lower($1)', [nombre]);
      idsCategoria[nombre] = existente.rows[0]
        ? existente.rows[0].id
        : (await c.query('INSERT INTO categorias (nombre, descripcion) VALUES ($1, $2) RETURNING id', [nombre, descripcion])).rows[0].id;
    }
    console.log(`✔ ${CATEGORIAS.length} categorías`);

    // Máquinas de ejemplo (sin fotos: súbelas desde el panel de administrador)
    let creadas = 0;
    for (const [cat, nombre, marca, modelo, tarifa, ubicacion, estado, specs] of MAQUINAS) {
      const existe = await c.query('SELECT 1 FROM maquinas WHERE nombre = $1', [nombre]);
      if (existe.rowCount) continue;
      await c.query(
        `INSERT INTO maquinas (categoria_id, nombre, marca, modelo, tarifa_diaria, ubicacion, estado,
                               especificaciones, descripcion, publicada_en, creado_por)
         VALUES ($1, $2, $3, $4, $5, $6, $7, $8, $9, CASE WHEN $10::boolean THEN now() END, $11)`,
        [idsCategoria[cat], nombre, marca, modelo, tarifa, ubicacion, estado, JSON.stringify(specs),
          `${nombre} ${marca} en excelente estado, con mantenimiento al día. Incluye manual de operación.`,
          estado === 'PUBLICADA', adminId]
      );
      creadas++;
    }
    console.log(`✔ ${creadas} máquina(s) de ejemplo nuevas`);
  });
}

sembrar()
  .catch((e) => {
    console.error('Error al sembrar datos:', e.message);
    process.exitCode = 1;
  })
  .finally(() => pool.end());
