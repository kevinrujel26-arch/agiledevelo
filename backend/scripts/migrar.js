/**
 * Aplica en orden los archivos .sql de database/migrations que aún no se aplicaron.
 *   npm run db:migrar                 -> aplica las pendientes
 *   node scripts/migrar.js --reiniciar -> BORRA todo y vuelve a crear (solo desarrollo)
 */
const fs = require('fs');
const path = require('path');
const { Client } = require('pg');

async function migrar({ url, ssl = false, reiniciar = false, silencioso = false } = {}) {
  const config = url ? null : require('../src/config');
  const cliente = new Client({
    connectionString: url || config.bd.url,
    ssl: (url ? ssl : config.bd.ssl) ? { rejectUnauthorized: false } : undefined,
  });
  const log = silencioso ? () => {} : console.log;
  const dir = process.env.MIGRATIONS_DIR || path.resolve(__dirname, '../../database/migrations');

  await cliente.connect();
  try {
    if (reiniciar) {
      log('⚠ Borrando el esquema public...');
      await cliente.query('DROP SCHEMA public CASCADE; CREATE SCHEMA public;');
    }
    await cliente.query(`
      CREATE TABLE IF NOT EXISTS schema_migraciones (
        nombre      VARCHAR(200) PRIMARY KEY,
        aplicada_en TIMESTAMPTZ NOT NULL DEFAULT now()
      )`);
    const { rows } = await cliente.query('SELECT nombre FROM schema_migraciones');
    const aplicadas = new Set(rows.map((r) => r.nombre));

    const archivos = fs.readdirSync(dir).filter((a) => a.endsWith('.sql')).sort();
    let nuevas = 0;
    for (const archivo of archivos) {
      if (aplicadas.has(archivo)) continue;
      const sql = fs.readFileSync(path.join(dir, archivo), 'utf8');
      log(`→ Aplicando ${archivo}`);
      await cliente.query('BEGIN');
      try {
        await cliente.query(sql);
        await cliente.query('INSERT INTO schema_migraciones (nombre) VALUES ($1)', [archivo]);
        await cliente.query('COMMIT');
        nuevas++;
      } catch (error) {
        await cliente.query('ROLLBACK');
        throw new Error(`Falló la migración ${archivo}: ${error.message}`);
      }
    }
    log(nuevas ? `✔ ${nuevas} migración(es) aplicada(s)` : '✔ La base de datos ya estaba al día');
  } finally {
    await cliente.end();
  }
}

if (require.main === module) {
  migrar({ reiniciar: process.argv.includes('--reiniciar') }).catch((e) => {
    console.error(e.message);
    process.exit(1);
  });
}

module.exports = { migrar };
