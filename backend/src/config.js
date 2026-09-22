const path = require('path');
require('dotenv').config({ path: path.resolve(__dirname, '../.env') });

const entorno = process.env.NODE_ENV || 'development';
const esPrueba = entorno === 'test';

function requerida(nombre, valorPorDefecto) {
  const valor = process.env[nombre];
  if (valor) return valor;
  if (valorPorDefecto !== undefined) return valorPorDefecto;
  throw new Error(`Falta la variable de entorno ${nombre}. Revisa tu archivo .env`);
}

const config = {
  entorno,
  esPrueba,
  puerto: Number(process.env.PORT || 3000),

  bd: {
    url: esPrueba
      ? requerida('DATABASE_URL_TEST')
      : requerida('DATABASE_URL'),
    ssl: process.env.DB_SSL === 'true',
  },

  jwtSecreto: requerida('JWT_SECRET', esPrueba ? 'secreto-de-pruebas' : undefined),

  // HU-02: reglas de sesión
  sesion: {
    inactividadMinutos: 30,            // sin "Recordarme": expira tras 30 min sin actividad
    duracionHoras: 8,                  // sin "Recordarme": vence a las 8 h aunque haya actividad
    recordarmeInactividadDias: 7,      // con "Recordarme": 7 días sin actividad
    recordarmeDuracionDias: 30,        // con "Recordarme": vence a los 30 días
  },
  login: {
    maxIntentosFallidos: 5,            // HU-02 criterio 5
    minutosBloqueo: 15,
  },

  corsOrigenes: (process.env.CORS_ORIGIN || 'http://localhost:5173')
    .split(',')
    .map((o) => o.trim())
    .filter(Boolean),

  dirSubidas: path.resolve(__dirname, '..', process.env.UPLOAD_DIR || 'uploads'),
  fotos: {
    maxPorMaquina: 5,                  // HU-08 criterio 2
    maxBytes: 5 * 1024 * 1024,
  },

  zonaHoraria: process.env.APP_TZ || 'America/Lima',
};

module.exports = config;
