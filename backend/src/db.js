const { Pool, types } = require('pg');
const config = require('./config');

// NUMERIC (1700) -> number de JavaScript (tarifas y montos)
types.setTypeParser(1700, (v) => (v === null ? null : parseFloat(v)));
// DATE (1082) -> texto 'YYYY-MM-DD' (evita corrimientos de un día por zona horaria)
types.setTypeParser(1082, (v) => v);
// BIGINT (20) -> number (conteos)
types.setTypeParser(20, (v) => (v === null ? null : parseInt(v, 10)));

const pool = new Pool({
  connectionString: config.bd.url,
  ssl: config.bd.ssl ? { rejectUnauthorized: false } : undefined,
  max: 10,
});

function consultar(texto, parametros) {
  return pool.query(texto, parametros);
}

/**
 * Ejecuta fn(cliente) dentro de una transacción.
 * Si fn lanza un error se hace ROLLBACK automáticamente.
 */
async function transaccion(fn) {
  const cliente = await pool.connect();
  try {
    await cliente.query('BEGIN');
    const resultado = await fn(cliente);
    await cliente.query('COMMIT');
    return resultado;
  } catch (error) {
    await cliente.query('ROLLBACK');
    throw error;
  } finally {
    cliente.release();
  }
}

module.exports = { pool, consultar, transaccion };
