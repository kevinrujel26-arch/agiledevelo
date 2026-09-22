const { Router } = require('express');
const z = require('../../utils/zod-es');
const { validar } = require('../../middleware/validar');
const { esFechaValida, hoy, diasEntre } = require('../../utils/fechas');
const servicio = require('./disponibilidad.servicio');

const id = z.coerce.number().int().positive();
const fecha = z.string().refine(esFechaValida, 'Fecha inválida, usa el formato AAAA-MM-DD');

const rango = z
  .object({ fechaInicio: fecha, fechaFin: fecha })
  .refine((r) => r.fechaFin >= r.fechaInicio, {
    message: 'La fecha de fin no puede ser anterior a la de inicio',
    path: ['fechaFin'],
  });

const esquemaBloqueo = z.object({
  rangos: z.array(rango).min(1, 'Agrega al menos un rango de fechas').max(20),
  motivo: z.string().trim().max(160).nullish(),
});

function sumarDias(fechaTexto, dias) {
  const d = new Date(`${fechaTexto}T00:00:00Z`);
  d.setUTCDate(d.getUTCDate() + dias);
  return d.toISOString().slice(0, 10);
}

const esquemaRangoConsulta = z
  .object({ desde: fecha.optional(), hasta: fecha.optional() })
  .transform((q) => {
    const desde = q.desde ?? hoy();
    return { desde, hasta: q.hasta ?? sumarDias(desde, 180) };
  })
  .refine((q) => q.hasta >= q.desde, 'La fecha "hasta" debe ser posterior a "desde"')
  .refine((q) => diasEntre(q.desde, q.hasta) <= 400, 'Consulta como máximo 400 días a la vez');

// Público: /api/maquinas/:id/disponibilidad
const publico = Router({ mergeParams: true });
publico.get(
  '/',
  validar({ params: z.object({ id }), query: esquemaRangoConsulta }),
  async (req, res) => {
    res.json(await servicio.ocupacionPublica(req.datos.params.id, req.datos.query));
  }
);

// Administrador: /api/admin/maquinas/:id/bloqueos
const admin = Router({ mergeParams: true });

admin.get(
  '/',
  validar({
    params: z.object({ id }),
    query: z.object({ incluirPasados: z.enum(['true', 'false']).optional() }),
  }),
  async (req, res) => {
    const datos = await servicio.listarBloqueos(req.datos.params.id, {
      incluirPasados: req.datos.query.incluirPasados === 'true',
    });
    res.json({ datos });
  }
);

admin.post('/', validar({ params: z.object({ id }), body: esquemaBloqueo }), async (req, res) => {
  const datos = await servicio.bloquear(req.datos.params.id, req.datos.body, req.usuario.id);
  res.status(201).json({ datos });
});

admin.delete('/:bloqueoId', validar({ params: z.object({ id, bloqueoId: id }) }), async (req, res) => {
  await servicio.desbloquear(req.datos.params.id, req.datos.params.bloqueoId);
  res.status(204).end();
});

module.exports = { publico, admin };
