const bcrypt = require('bcryptjs');
const request = require('supertest');
const app = require('../src/app');
const { consultar, pool } = require('../src/db');

const api = () => request(app);

async function limpiarBD() {
  await consultar(`
    TRUNCATE auditoria, solicitudes_reembolso, pagos, reservas, bloqueos_disponibilidad,
             fotos_maquina, maquinas, categorias, sesiones, usuarios
    RESTART IDENTITY CASCADE`);
}

let contador = 0;
async function crearUsuario({ rol = 'CLIENTE', contrasena = 'Clave12345', activo = true } = {}) {
  contador++;
  const correo = `usuario${contador}@prueba.pe`;
  const hash = await bcrypt.hash(contrasena, 4);
  const { rows } = await consultar(
    `INSERT INTO usuarios (nombre, correo, contrasena_hash, rol, activo)
     VALUES ($1, $2, $3, $4, $5) RETURNING id, correo, rol`,
    [`Usuario ${contador}`, correo, hash, rol, activo]
  );
  return { ...rows[0], contrasena };
}

async function tokenDe(usuario, { recordarme = false } = {}) {
  const res = await api()
    .post('/api/auth/login')
    .send({ correo: usuario.correo, contrasena: usuario.contrasena, recordarme });
  if (res.status !== 200) throw new Error(`No se pudo iniciar sesión: ${JSON.stringify(res.body)}`);
  return res.body.token;
}

async function tokenAdmin() {
  return tokenDe(await crearUsuario({ rol: 'ADMINISTRADOR' }));
}

async function crearCategoria(nombre = 'Excavadoras', activa = true) {
  const { rows } = await consultar(
    'INSERT INTO categorias (nombre, activa) VALUES ($1, $2) RETURNING id',
    [nombre, activa]
  );
  return rows[0].id;
}

function datosMaquina(categoriaId, extra = {}) {
  return {
    categoriaId,
    nombre: 'Excavadora 320',
    marca: 'Caterpillar',
    modelo: '320 GC',
    tarifaDiaria: 1450,
    ubicacion: 'Trujillo',
    especificaciones: { Potencia: '146 HP' },
    ...extra,
  };
}

// Imágenes mínimas con la firma correcta de cada formato
const PNG = Buffer.from([0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a, 0, 0, 0, 0]);
const JPG = Buffer.from([0xff, 0xd8, 0xff, 0xe0, 0, 0x10, 0x4a, 0x46]);

module.exports = {
  api,
  pool,
  consultar,
  limpiarBD,
  crearUsuario,
  tokenDe,
  tokenAdmin,
  crearCategoria,
  datosMaquina,
  PNG,
  JPG,
};
