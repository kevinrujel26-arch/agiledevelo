// Pruebas de aceptación: HU-14 Gestionar categorías
const { api, pool, limpiarBD, tokenAdmin, crearCategoria, consultar } = require('./ayudantes');

let token;
const conToken = (req) => req.set('Authorization', `Bearer ${token}`);

beforeEach(async () => {
  await limpiarBD();
  token = await tokenAdmin();
});
afterAll(() => pool.end());

describe('HU-14 Gestionar categorías', () => {
  test('crea, renombra y desactiva una categoría', async () => {
    const creada = await conToken(api().post('/api/admin/categorias')).send({ nombre: 'Grúas' });
    expect(creada.status).toBe(201);

    const renombrada = await conToken(api().put(`/api/admin/categorias/${creada.body.id}`)).send({ nombre: 'Grúas torre' });
    expect(renombrada.body.nombre).toBe('Grúas torre');

    const desactivada = await conToken(api().patch(`/api/admin/categorias/${creada.body.id}/estado`)).send({ activa: false });
    expect(desactivada.body.activa).toBe(false);
  });

  test('el nombre no puede repetirse (sin distinguir mayúsculas)', async () => {
    await crearCategoria('Excavadoras');
    const res = await conToken(api().post('/api/admin/categorias')).send({ nombre: '  EXCAVADORAS ' });
    expect(res.status).toBe(409);
  });

  test('una categoría desactivada no aparece en el catálogo público', async () => {
    await crearCategoria('Activa', true);
    await crearCategoria('Inactiva', false);
    const res = await api().get('/api/categorias');
    expect(res.body.datos.map((c) => c.nombre)).toEqual(['Activa']);
  });

  test('una categoría con máquinas no se elimina, solo se desactiva', async () => {
    const id = await crearCategoria('Montacargas');
    await consultar(
      `INSERT INTO maquinas (categoria_id, nombre, marca, modelo, tarifa_diaria, ubicacion)
       VALUES ($1, 'M1', 'Toyota', '8FD', 200, 'Lima')`,
      [id]
    );
    const res = await conToken(api().delete(`/api/admin/categorias/${id}`));
    expect(res.status).toBe(409);
    expect(res.body.error).toMatch(/desactívala/i);
  });

  test('una categoría sin máquinas sí se puede eliminar', async () => {
    const id = await crearCategoria('Temporal');
    await conToken(api().delete(`/api/admin/categorias/${id}`)).expect(204);
  });
});
