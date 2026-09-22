// Pruebas de aceptación: HU-09 Gestionar disponibilidad
const {
  api, pool, consultar, limpiarBD, tokenAdmin, crearCategoria, crearUsuario,
} = require('./ayudantes');
const { hoy } = require('../src/utils/fechas');

let token;
let maquinaId;
const conToken = (req) => req.set('Authorization', `Bearer ${token}`);

function dentroDe(dias) {
  const d = new Date(`${hoy()}T00:00:00Z`);
  d.setUTCDate(d.getUTCDate() + dias);
  return d.toISOString().slice(0, 10);
}

const bloquear = (rangos, motivo = 'Mantenimiento') =>
  conToken(api().post(`/api/admin/maquinas/${maquinaId}/bloqueos`)).send({ rangos, motivo });

beforeEach(async () => {
  await limpiarBD();
  token = await tokenAdmin();
  const categoriaId = await crearCategoria();
  const { rows } = await consultar(
    `INSERT INTO maquinas (categoria_id, nombre, marca, modelo, tarifa_diaria, ubicacion, estado, publicada_en)
     VALUES ($1, 'Excavadora', 'CAT', '320', 1000, 'Trujillo', 'PUBLICADA', now()) RETURNING id`,
    [categoriaId]
  );
  maquinaId = rows[0].id;
});
afterAll(() => pool.end());

describe('HU-09 Gestionar disponibilidad', () => {
  test('bloquea uno o varios rangos de fechas a la vez', async () => {
    const res = await bloquear([
      { fechaInicio: dentroDe(1), fechaFin: dentroDe(3) },
      { fechaInicio: dentroDe(10), fechaFin: dentroDe(10) },
    ]);
    expect(res.status).toBe(201);
    expect(res.body.datos).toHaveLength(2);
  });

  test('las fechas bloqueadas aparecen ocupadas en el calendario público de inmediato', async () => {
    await bloquear([{ fechaInicio: dentroDe(5), fechaFin: dentroDe(6) }]).expect(201);
    const res = await api().get(`/api/maquinas/${maquinaId}/disponibilidad`);
    expect(res.status).toBe(200);
    expect(res.body.ocupados).toEqual([
      { fechaInicio: dentroDe(5), fechaFin: dentroDe(6), tipo: 'BLOQUEO' },
    ]);
  });

  test('no se puede bloquear una fecha con una reserva ya pagada', async () => {
    const cliente = await crearUsuario();
    await consultar(
      `INSERT INTO reservas (maquina_id, cliente_id, fecha_inicio, fecha_fin, tarifa_diaria, monto_total, estado)
       VALUES ($1, $2, $3, $4, 1000, 3000, 'PAGADA')`,
      [maquinaId, cliente.id, dentroDe(7), dentroDe(9)]
    );
    const res = await bloquear([{ fechaInicio: dentroDe(9), fechaFin: dentroDe(12) }]);
    expect(res.status).toBe(409);
    expect(res.body.error).toMatch(/reserva pagada/);
    // Nada se guardó (todo o nada)
    const { rows } = await consultar('SELECT count(*) AS total FROM bloqueos_disponibilidad');
    expect(rows[0].total).toBe(0);
  });

  test('no permite rangos superpuestos con un bloqueo existente', async () => {
    await bloquear([{ fechaInicio: dentroDe(1), fechaFin: dentroDe(5) }]).expect(201);
    const res = await bloquear([{ fechaInicio: dentroDe(5), fechaFin: dentroDe(8) }]);
    expect(res.status).toBe(409);
  });

  test('valida que la fecha fin no sea anterior a la de inicio ni que sea pasada', async () => {
    await bloquear([{ fechaInicio: dentroDe(5), fechaFin: dentroDe(2) }]).expect(400);
    await bloquear([{ fechaInicio: dentroDe(-3), fechaFin: dentroDe(2) }]).expect(400);
  });

  test('se pueden desbloquear fechas bloqueadas antes', async () => {
    const { body } = await bloquear([{ fechaInicio: dentroDe(1), fechaFin: dentroDe(2) }]);
    const bloqueoId = body.datos[0].id;
    await conToken(api().delete(`/api/admin/maquinas/${maquinaId}/bloqueos/${bloqueoId}`)).expect(204);
    const res = await api().get(`/api/maquinas/${maquinaId}/disponibilidad`);
    expect(res.body.ocupados).toHaveLength(0);
  });
});
