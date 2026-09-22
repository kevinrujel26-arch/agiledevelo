require('./utils/zod-es'); // mensajes de validación en español
const express = require('express');
const cors = require('cors');
const helmet = require('helmet');
const morgan = require('morgan');
const config = require('./config');
const { consultar } = require('./db');
const { autenticar, autorizar, ROLES } = require('./middleware/autenticacion');
const { manejadorErrores, rutaNoEncontrada } = require('./middleware/manejadorErrores');

const auth = require('./modulos/auth/auth.rutas');
const categorias = require('./modulos/categorias/categorias.rutas');
const maquinas = require('./modulos/maquinas/maquinas.rutas');
const disponibilidad = require('./modulos/disponibilidad/disponibilidad.rutas');

const app = express();

app.set('trust proxy', 1); // detrás del proxy de Render/Railway, para obtener la IP real
app.use(helmet({ crossOriginResourcePolicy: { policy: 'cross-origin' } })); // permite mostrar fotos desde el frontend
app.use(cors({ origin: config.corsOrigenes }));
app.use(express.json({ limit: '100kb' }));
if (!config.esPrueba) app.use(morgan('dev'));

// Fotos subidas (HU-08)
app.use('/uploads', express.static(config.dirSubidas, { maxAge: '7d' }));

// Salud del servicio (EN-04: sirve para verificar el despliegue)
app.get('/api/salud', async (_req, res) => {
  await consultar('SELECT 1');
  res.json({ estado: 'ok', fecha: new Date().toISOString() });
});

// ---------------- Rutas públicas ----------------
app.use('/api/auth', auth);
app.use('/api/categorias', categorias.publico);
app.use('/api/maquinas/:id/disponibilidad', disponibilidad.publico);
app.use('/api/maquinas', maquinas.publico);

// ---------------- Rutas de administrador (EN-05) ----------------
const admin = express.Router();
admin.use(autenticar, autorizar(ROLES.ADMINISTRADOR));
admin.use('/categorias', categorias.admin);
admin.use('/maquinas/:id/bloqueos', disponibilidad.admin);
admin.use('/maquinas', maquinas.admin);
app.use('/api/admin', admin);

app.use(rutaNoEncontrada);
app.use(manejadorErrores);

module.exports = app;
