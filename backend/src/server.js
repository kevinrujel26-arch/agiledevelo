const app = require('./app');
const config = require('./config');
const { pool } = require('./db');

const servidor = app.listen(config.puerto, () => {
  console.log(`API escuchando en http://localhost:${config.puerto} (${config.entorno})`);
});

function apagar() {
  servidor.close(() => pool.end().then(() => process.exit(0)));
}
process.on('SIGTERM', apagar);
process.on('SIGINT', apagar);
