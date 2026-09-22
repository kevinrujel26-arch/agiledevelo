// Pruebas de aceptación: HU-08 Registrar y publicar máquina · HU-03 Ver catálogo
const {
  api, pool, consultar, limpiarBD, tokenAdmin, crearCategoria, datosMaquina, PNG, JPG,
} = require('./ayudantes');

let token;
let categoriaId;
const conToken = (req) => req.set('Authorization', `Bearer ${token}`);

async function crearMaquina(extra) {
  const res = await conToken(api().post('/api/admin/maquinas')).send(datosMaquina(categoriaId, extra));
  expect(res.status).toBe(201);
  return res.body;
}

function subir(id, ...archivos) {
  let req = conToken(api().post(`/api/admin/maquinas/${id}/fotos`));
  archivos.forEach(([buffer, nombre, tipo]) => {
    req = req.attach('fotos', buffer, { filename: nombre, contentType: tipo });
  });
  return req;
}

async function crearPublicada(extra) {
  const m = await crearMaquina(extra);
  await subir(m.id, [PNG, 'foto.png', 'image/png']).expect(201);
  await conToken(api().post(`/api/admin/maquinas/${m.id}/publicar`)).expect(200);
  return m;
}

beforeEach(async () => {
  await limpiarBD();
  token = await tokenAdmin();
  categoriaId = await crearCategoria('Excavadoras');
});
afterAll(() => pool.end());

describe('HU-08 Registrar y publicar máquina', () => {
  test('pide nombre, categoría, marca, modelo, tarifa y ubicación', async () => {
    const res = await conToken(api().post('/api/admin/maquinas')).send({ nombre: 'Sin datos' });
    expect(res.status).toBe(400);
    const campos = res.body.detalles.map((d) => d.campo);
    expect(campos).toEqual(expect.arrayContaining(['categoriaId', 'marca', 'modelo', 'tarifaDiaria', 'ubicacion']));
  });

  test('se guarda como borrador y no aparece en el catálogo', async () => {
    const m = await crearMaquina();
    expect(m.estado).toBe('BORRADOR');
    const catalogo = await api().get('/api/maquinas');
    expect(catalogo.body.datos).toHaveLength(0);
    await api().get(`/api/maquinas/${m.id}`).expect(404);
  });

  test('sube fotos JPG/PNG y la primera queda como principal', async () => {
    const m = await crearMaquina();
    const res = await subir(m.id, [JPG, 'a.jpg', 'image/jpeg'], [PNG, 'b.png', 'image/png']);
    expect(res.status).toBe(201);
    expect(res.body.fotos).toHaveLength(2);
    expect(res.body.fotos.filter((f) => f.esPrincipal)).toHaveLength(1);
  });

  test('rechaza formatos que no son JPG o PNG', async () => {
    const m = await crearMaquina();
    const gif = await subir(m.id, [Buffer.from('GIF89a'), 'a.gif', 'image/gif']);
    expect(gif.status).toBe(400);
    // Archivo que dice ser PNG pero no lo es
    const falso = await subir(m.id, [Buffer.from('no soy imagen'), 'falso.png', 'image/png']);
    expect(falso.status).toBe(400);
  });

  test('permite como máximo 5 fotos', async () => {
    const m = await crearMaquina();
    const cinco = Array.from({ length: 5 }, (_, i) => [PNG, `f${i}.png`, 'image/png']);
    await subir(m.id, ...cinco).expect(201);
    const sexta = await subir(m.id, [PNG, 'f6.png', 'image/png']);
    expect(sexta.status).toBe(400);
    expect(sexta.body.error).toMatch(/máximo de 5/);
  });

  test('se puede cambiar la foto principal', async () => {
    const m = await crearMaquina();
    const { body } = await subir(m.id, [PNG, 'a.png', 'image/png'], [PNG, 'b.png', 'image/png']);
    const otra = body.fotos.find((f) => !f.esPrincipal);
    const res = await conToken(api().patch(`/api/admin/maquinas/${m.id}/fotos/${otra.id}/principal`));
    expect(res.body.fotos.find((f) => f.esPrincipal).id).toBe(otra.id);
  });

  test('no se publica sin foto principal', async () => {
    const m = await crearMaquina();
    const res = await conToken(api().post(`/api/admin/maquinas/${m.id}/publicar`));
    expect(res.status).toBe(409);
  });

  test('al publicarla aparece de inmediato en el catálogo', async () => {
    const m = await crearPublicada();
    const catalogo = await api().get('/api/maquinas');
    expect(catalogo.body.datos.map((x) => x.id)).toContain(m.id);
  });

  test('se puede editar después de publicada', async () => {
    const m = await crearPublicada();
    const res = await conToken(api().put(`/api/admin/maquinas/${m.id}`)).send({ tarifaDiaria: 1600.5 });
    expect(res.status).toBe(200);
    expect(res.body.tarifaDiaria).toBe(1600.5);
    expect(res.body.estado).toBe('PUBLICADA');
  });

  test('se retira del catálogo sin borrar su historial de reservas', async () => {
    const m = await crearPublicada();
    const { rows } = await consultar("SELECT id FROM usuarios WHERE rol = 'ADMINISTRADOR'");
    await consultar(
      `INSERT INTO reservas (maquina_id, cliente_id, fecha_inicio, fecha_fin, tarifa_diaria, monto_total, estado)
       VALUES ($1, $2, '2025-01-10', '2025-01-12', 1450, 4350, 'FINALIZADA')`,
      [m.id, rows[0].id]
    );
    await conToken(api().post(`/api/admin/maquinas/${m.id}/retirar`)).expect(200);

    const catalogo = await api().get('/api/maquinas');
    expect(catalogo.body.datos).toHaveLength(0);
    const reservas = await consultar('SELECT count(*) AS total FROM reservas WHERE maquina_id = $1', [m.id]);
    expect(reservas.rows[0].total).toBe(1);
  });
});

describe('HU-03 Ver catálogo de maquinaria', () => {
  test('es visible sin iniciar sesión y muestra foto, nombre, categoría y tarifa', async () => {
    await crearPublicada();
    const res = await api().get('/api/maquinas');
    expect(res.status).toBe(200);
    const tarjeta = res.body.datos[0];
    expect(tarjeta).toMatchObject({
      nombre: 'Excavadora 320',
      categoria: { nombre: 'Excavadoras' },
      tarifaDiaria: 1450,
    });
    expect(tarjeta.fotoPrincipal).toMatch(/^\/uploads\/maquinas\/.+\.png$/);
  });

  test('está paginado', async () => {
    for (let i = 0; i < 5; i++) await crearPublicada({ nombre: `Máquina ${i}` });
    const res = await api().get('/api/maquinas?pagina=2&tamanio=2');
    expect(res.body.datos).toHaveLength(2);
    expect(res.body.paginacion).toMatchObject({ pagina: 2, tamanio: 2, total: 5, totalPaginas: 3 });
  });

  test('el detalle de una máquina publicada se abre por su URL', async () => {
    const m = await crearPublicada();
    const res = await api().get(`/api/maquinas/${m.id}`);
    expect(res.status).toBe(200);
    expect(res.body.especificaciones).toEqual({ Potencia: '146 HP' });
    expect(res.body.fotos).toHaveLength(1);
  });
});
