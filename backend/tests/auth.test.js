// Pruebas de aceptación: HU-01, HU-02, EN-03, EN-05
const { api, pool, consultar, limpiarBD, crearUsuario, tokenDe } = require('./ayudantes');

beforeEach(limpiarBD);
afterAll(() => pool.end());

describe('HU-01 Registrar cliente', () => {
  const valido = { nombre: 'Ana Torres', correo: 'Ana@Correo.com', contrasena: 'secreta123' };

  test('registra con rol CLIENTE, guarda la contraseña cifrada y confirma', async () => {
    const res = await api().post('/api/auth/registro').send({ ...valido, rol: 'ADMINISTRADOR' });

    expect(res.status).toBe(201);
    expect(res.body.mensaje).toMatch(/registro exitoso/i);
    expect(res.body.usuario).toMatchObject({ correo: 'ana@correo.com', rol: 'CLIENTE' });

    const { rows } = await consultar('SELECT contrasena_hash, rol FROM usuarios');
    expect(rows[0].rol).toBe('CLIENTE');
    expect(rows[0].contrasena_hash).not.toBe(valido.contrasena);
    expect(rows[0].contrasena_hash).toMatch(/^\$2[aby]\$/);
  });

  test('no permite un correo repetido (sin importar mayúsculas)', async () => {
    await api().post('/api/auth/registro').send(valido).expect(201);
    const res = await api().post('/api/auth/registro').send({ ...valido, correo: 'ANA@correo.com' });
    expect(res.status).toBe(409);
  });

  test('exige contraseña de mínimo 8 caracteres', async () => {
    const res = await api().post('/api/auth/registro').send({ ...valido, contrasena: '1234567' });
    expect(res.status).toBe(400);
    expect(res.body.error).toMatch(/8 caracteres/);
  });

  test('rechaza un correo con formato inválido', async () => {
    const res = await api().post('/api/auth/registro').send({ ...valido, correo: 'ana@' });
    expect(res.status).toBe(400);
  });

  test.each(['nombre', 'correo', 'contrasena'])('%s es obligatorio', async (campo) => {
    const datos = { ...valido };
    delete datos[campo];
    const res = await api().post('/api/auth/registro').send(datos);
    expect(res.status).toBe(400);
  });
});

describe('HU-02 Iniciar y cerrar sesión', () => {
  test('con credenciales correctas devuelve token y rol', async () => {
    const admin = await crearUsuario({ rol: 'ADMINISTRADOR' });
    const res = await api().post('/api/auth/login').send({ correo: admin.correo, contrasena: admin.contrasena });
    expect(res.status).toBe(200);
    expect(res.body.token).toBeDefined();
    expect(res.body.usuario.rol).toBe('ADMINISTRADOR');
  });

  test('con credenciales incorrectas muestra el mismo error genérico', async () => {
    const u = await crearUsuario();
    const claveMala = await api().post('/api/auth/login').send({ correo: u.correo, contrasena: 'otraClave1' });
    const noExiste = await api().post('/api/auth/login').send({ correo: 'nadie@x.pe', contrasena: 'otraClave1' });
    expect(claveMala.status).toBe(401);
    expect(noExiste.status).toBe(401);
    expect(claveMala.body.error).toBe(noExiste.body.error);
  });

  test('bloquea la cuenta tras 5 intentos fallidos seguidos', async () => {
    const u = await crearUsuario();
    for (let i = 0; i < 4; i++) {
      await api().post('/api/auth/login').send({ correo: u.correo, contrasena: 'incorrecta' }).expect(401);
    }
    const quinto = await api().post('/api/auth/login').send({ correo: u.correo, contrasena: 'incorrecta' });
    expect(quinto.status).toBe(423);

    // Aun con la contraseña correcta, sigue bloqueada
    const correcto = await api().post('/api/auth/login').send({ correo: u.correo, contrasena: u.contrasena });
    expect(correcto.status).toBe(423);
  });

  test('un login correcto reinicia el contador de intentos', async () => {
    const u = await crearUsuario();
    for (let i = 0; i < 4; i++) {
      await api().post('/api/auth/login').send({ correo: u.correo, contrasena: 'incorrecta' });
    }
    await api().post('/api/auth/login').send({ correo: u.correo, contrasena: u.contrasena }).expect(200);
    await api().post('/api/auth/login').send({ correo: u.correo, contrasena: 'incorrecta' }).expect(401);
  });

  test('cerrar sesión invalida el token', async () => {
    const token = await tokenDe(await crearUsuario());
    await api().get('/api/auth/yo').set('Authorization', `Bearer ${token}`).expect(200);
    await api().post('/api/auth/logout').set('Authorization', `Bearer ${token}`).expect(204);
    await api().get('/api/auth/yo').set('Authorization', `Bearer ${token}`).expect(401);
  });

  test('la sesión expira tras 30 minutos de inactividad', async () => {
    const token = await tokenDe(await crearUsuario());
    await consultar("UPDATE sesiones SET ultima_actividad = now() - interval '31 minutes'");
    const res = await api().get('/api/auth/yo').set('Authorization', `Bearer ${token}`);
    expect(res.status).toBe(401);
    expect(res.body.error).toMatch(/inactividad/);
  });

  test('"Recordarme" extiende la sesión', async () => {
    const u = await crearUsuario();
    const token = await tokenDe(u, { recordarme: true });
    await consultar("UPDATE sesiones SET ultima_actividad = now() - interval '2 hours'");
    await api().get('/api/auth/yo').set('Authorization', `Bearer ${token}`).expect(200);

    const { rows } = await consultar("SELECT expira_en > now() + interval '20 days' AS larga FROM sesiones");
    expect(rows[0].larga).toBe(true);
  });

  test('un usuario desactivado no puede iniciar sesión', async () => {
    const u = await crearUsuario({ activo: false });
    const res = await api().post('/api/auth/login').send({ correo: u.correo, contrasena: u.contrasena });
    expect(res.status).toBe(403);
  });
});

describe('EN-05 Control de acceso por rol', () => {
  test('sin token, las rutas de administrador responden 401', async () => {
    await api().get('/api/admin/categorias').expect(401);
  });

  test('un cliente no puede entrar a rutas de administrador', async () => {
    const token = await tokenDe(await crearUsuario({ rol: 'CLIENTE' }));
    await api().get('/api/admin/categorias').set('Authorization', `Bearer ${token}`).expect(403);
  });

  test('un token alterado es rechazado', async () => {
    const token = await tokenDe(await crearUsuario());
    await api().get('/api/auth/yo').set('Authorization', `Bearer ${token}x`).expect(401);
  });
});
