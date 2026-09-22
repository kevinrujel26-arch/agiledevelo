const { Router } = require('express');
const z = require('../../utils/zod-es');
const { validar } = require('../../middleware/validar');
const { autenticar } = require('../../middleware/autenticacion');
const servicio = require('./auth.servicio');

const correo = z.string().trim().toLowerCase().min(1).max(160).email();

const esquemaRegistro = z.object({
  nombre: z.string().trim().min(1, 'El nombre es obligatorio').max(120),
  correo,
  contrasena: z
    .string()
    .min(8, 'La contraseña debe tener al menos 8 caracteres')
    .max(72, 'La contraseña debe tener como máximo 72 caracteres'),
});

const esquemaLogin = z.object({
  correo,
  contrasena: z.string().min(1, 'Ingresa tu contraseña'),
  recordarme: z.boolean().optional().default(false),
});

const router = Router();

// HU-01
router.post('/registro', validar({ body: esquemaRegistro }), async (req, res) => {
  const usuario = await servicio.registrarCliente(req.datos.body);
  res.status(201).json({
    mensaje: '¡Registro exitoso! Ya puedes iniciar sesión',
    usuario,
  });
});

// HU-02
router.post('/login', validar({ body: esquemaLogin }), async (req, res) => {
  const resultado = await servicio.iniciarSesion(req.datos.body, {
    ip: req.ip,
    agenteUsuario: req.get('user-agent'),
  });
  res.json(resultado);
});

router.post('/logout', autenticar, async (req, res) => {
  await servicio.cerrarSesion(req.sesionId);
  res.status(204).end();
});

router.get('/yo', autenticar, (req, res) => {
  res.json({ usuario: req.usuario });
});

module.exports = router;
