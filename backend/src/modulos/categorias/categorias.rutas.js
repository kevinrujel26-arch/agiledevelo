const { Router } = require('express');
const z = require('../../utils/zod-es');
const { validar } = require('../../middleware/validar');
const servicio = require('./categorias.servicio');

const paramsId = z.object({ id: z.coerce.number().int().positive() });

const esquemaCrear = z.object({
  nombre: z.string().trim().min(1, 'El nombre es obligatorio').max(80),
  descripcion: z.string().trim().max(255).nullish(),
});

const esquemaActualizar = esquemaCrear
  .partial()
  .refine((d) => Object.keys(d).length > 0, 'Envía al menos un campo para actualizar');

const esquemaEstado = z.object({ activa: z.boolean() });

/** Rutas públicas: /api/categorias */
const publico = Router();
publico.get('/', async (_req, res) => {
  res.json({ datos: await servicio.listarActivas() });
});

/** Rutas de administrador: /api/admin/categorias */
const admin = Router();

admin.get('/', async (_req, res) => {
  res.json({ datos: await servicio.listarTodas() });
});

admin.post('/', validar({ body: esquemaCrear }), async (req, res) => {
  res.status(201).json(await servicio.crear(req.datos.body));
});

admin.put('/:id', validar({ params: paramsId, body: esquemaActualizar }), async (req, res) => {
  res.json(await servicio.actualizar(req.datos.params.id, req.datos.body));
});

admin.patch('/:id/estado', validar({ params: paramsId, body: esquemaEstado }), async (req, res) => {
  res.json(await servicio.cambiarEstado(req.datos.params.id, req.datos.body.activa));
});

admin.delete('/:id', validar({ params: paramsId }), async (req, res) => {
  await servicio.eliminar(req.datos.params.id);
  res.status(204).end();
});

module.exports = { publico, admin };
