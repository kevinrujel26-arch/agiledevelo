const { Router } = require('express');
const z = require('../../utils/zod-es');
const { validar } = require('../../middleware/validar');
const { subirFotos } = require('../../middleware/subidaFotos');
const { esquemaPaginacion } = require('../../utils/paginacion');
const servicio = require('./maquinas.servicio');

const id = z.coerce.number().int().positive();
const paramsId = z.object({ id });
const paramsFoto = z.object({ id, fotoId: id });

const esquemaMaquina = z.object({
  categoriaId: z.coerce.number().int().positive(),
  nombre: z.string().trim().min(1, 'El nombre es obligatorio').max(120),
  marca: z.string().trim().min(1, 'La marca es obligatoria').max(80),
  modelo: z.string().trim().min(1, 'El modelo es obligatorio').max(80),
  descripcion: z.string().trim().max(2000).nullish(),
  especificaciones: z
    .record(z.string().trim().min(1).max(60), z.string().trim().max(200))
    .refine((o) => Object.keys(o).length <= 30, 'Máximo 30 especificaciones')
    .optional(),
  tarifaDiaria: z.coerce
    .number({ invalid_type_error: 'La tarifa diaria debe ser un número' })
    .positive('La tarifa diaria debe ser mayor a 0')
    .max(99999999, 'La tarifa diaria es demasiado alta')
    .transform((n) => Math.round(n * 100) / 100),
  ubicacion: z.string().trim().min(1, 'La ubicación es obligatoria').max(160),
  enMantenimiento: z.boolean().optional(),
});

const esquemaActualizar = esquemaMaquina
  .partial()
  .refine((d) => Object.keys(d).length > 0, 'Envía al menos un campo para actualizar');

const esquemaFiltrosAdmin = z.object({
  ...esquemaPaginacion,
  estado: z.enum(['BORRADOR', 'PUBLICADA', 'RETIRADA']).optional(),
  categoriaId: z.coerce.number().int().positive().optional(),
  q: z.string().trim().max(80).optional(),
});

// -------------------- Público: /api/maquinas --------------------
const publico = Router();

// HU-03: catálogo paginado, visible sin iniciar sesión
publico.get('/', validar({ query: z.object(esquemaPaginacion) }), async (req, res) => {
  res.json(await servicio.listarCatalogo(req.datos.query));
});

publico.get('/:id', validar({ params: paramsId }), async (req, res) => {
  res.json(await servicio.obtenerPublica(req.datos.params.id));
});

// -------------------- Administrador: /api/admin/maquinas --------------------
const admin = Router();

admin.get('/', validar({ query: esquemaFiltrosAdmin }), async (req, res) => {
  res.json(await servicio.listarAdmin(req.datos.query));
});

admin.get('/:id', validar({ params: paramsId }), async (req, res) => {
  res.json(await servicio.obtenerAdmin(req.datos.params.id));
});

admin.post('/', validar({ body: esquemaMaquina }), async (req, res) => {
  res.status(201).json(await servicio.crear(req.datos.body, req.usuario.id));
});

admin.put('/:id', validar({ params: paramsId, body: esquemaActualizar }), async (req, res) => {
  res.json(await servicio.actualizar(req.datos.params.id, req.datos.body));
});

admin.post('/:id/publicar', validar({ params: paramsId }), async (req, res) => {
  res.json(await servicio.publicar(req.datos.params.id));
});

admin.post('/:id/retirar', validar({ params: paramsId }), async (req, res) => {
  res.json(await servicio.retirar(req.datos.params.id));
});

admin.delete('/:id', validar({ params: paramsId }), async (req, res) => {
  await servicio.eliminar(req.datos.params.id);
  res.status(204).end();
});

// Fotos: multipart/form-data con el campo "fotos" (uno o varios archivos)
admin.post('/:id/fotos', validar({ params: paramsId }), subirFotos, async (req, res) => {
  res.status(201).json({ fotos: await servicio.agregarFotos(req.datos.params.id, req.files) });
});

admin.patch('/:id/fotos/:fotoId/principal', validar({ params: paramsFoto }), async (req, res) => {
  const { id: maquinaId, fotoId } = req.datos.params;
  res.json({ fotos: await servicio.marcarPrincipal(maquinaId, fotoId) });
});

admin.delete('/:id/fotos/:fotoId', validar({ params: paramsFoto }), async (req, res) => {
  const { id: maquinaId, fotoId } = req.datos.params;
  res.json({ fotos: await servicio.eliminarFoto(maquinaId, fotoId) });
});

module.exports = { publico, admin };
