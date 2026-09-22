// Se ejecuta UNA vez antes de todas las pruebas: recrea la BD de pruebas desde cero
const path = require('path');
require('dotenv').config({ path: path.resolve(__dirname, '../../.env') });
const { migrar } = require('../../scripts/migrar');

module.exports = async () => {
  const url = process.env.DATABASE_URL_TEST;
  if (!url) {
    throw new Error('Define DATABASE_URL_TEST en backend/.env (una BD aparte; se borra en cada ejecución)');
  }
  if (process.env.DATABASE_URL && url === process.env.DATABASE_URL) {
    throw new Error('DATABASE_URL_TEST no puede ser la misma BD que DATABASE_URL');
  }
  await migrar({ url, ssl: process.env.DB_SSL === 'true', reiniciar: true, silencioso: true });
};
