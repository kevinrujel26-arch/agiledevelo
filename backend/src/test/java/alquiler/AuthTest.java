package alquiler;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import alquiler.bd.ErrorBd;
import alquiler.bd.Fila;
import alquiler.json.Json;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Pruebas de aceptación: HU-01, HU-02, EN-03 y EN-05. */
class AuthTest extends PruebaBase {

    private static Map<String, Object> registroValido() {
        return Json.obj("nombre", "Ana Torres", "correo", "Ana@Correo.com", "telefono", "987654321",
                "contrasena", "secreta123");
    }

    // ------------------------------------------------------------ HU-01
    @Test
    @DisplayName("HU-01: registra con rol CLIENTE, contraseña cifrada y mensaje de confirmación")
    void registraCliente() {
        Map<String, Object> datos = registroValido();
        datos.put("rol", "ADMINISTRADOR"); // se debe ignorar
        Resp r = post("/api/auth/registro", datos, null);

        assertEquals(201, r.estado());
        assertTrue(((String) r.json().get("mensaje")).contains("Registro exitoso"));
        assertEquals("ana@correo.com", r.objeto("usuario").get("correo"));
        assertEquals("CLIENTE", r.objeto("usuario").get("rol"));

        Fila f = bd().uno("SELECT contrasena_hash, rol FROM usuarios");
        assertEquals("CLIENTE", f.texto("rol"));
        assertNotEquals("secreta123", f.texto("contrasena_hash"));
        assertTrue(f.texto("contrasena_hash").startsWith("pbkdf2_sha256$"));
    }

    @Test
    @DisplayName("HU-01: el correo no se puede repetir (sin importar mayúsculas)")
    void correoRepetido() {
        assertEquals(201, post("/api/auth/registro", registroValido(), null).estado());
        Map<String, Object> otro = registroValido();
        otro.put("correo", "ANA@correo.com");
        assertEquals(409, post("/api/auth/registro", otro, null).estado());
    }

    @Test
    @DisplayName("HU-01: la contraseña exige mínimo 8 caracteres")
    void contrasenaCorta() {
        Map<String, Object> datos = registroValido();
        datos.put("contrasena", "1234567");
        Resp r = post("/api/auth/registro", datos, null);
        assertEquals(400, r.estado());
        assertTrue(r.error().contains("8 caracteres"));
    }

    @Test
    @DisplayName("HU-01: un correo con formato inválido no permite continuar")
    void correoInvalido() {
        Map<String, Object> datos = registroValido();
        datos.put("correo", "ana@");
        assertEquals(400, post("/api/auth/registro", datos, null).estado());
    }

    @Test
    @DisplayName("HU-01: nombre, correo, celular y contraseña son obligatorios")
    void camposObligatorios() {
        for (String campo : new String[]{"nombre", "correo", "telefono", "contrasena"}) {
            Map<String, Object> datos = registroValido();
            datos.remove(campo);
            assertEquals(400, post("/api/auth/registro", datos, null).estado(), "sin " + campo);
        }
    }

    // ------------------------------------------------------------ HU-02 (v7): celular
    @Test
    @DisplayName("HU-02: registrarse sin celular responde 400 con el detalle del campo")
    void registroSinTelefono() {
        Map<String, Object> datos = registroValido();
        datos.remove("telefono");
        Resp r = post("/api/auth/registro", datos, null);
        assertEquals(400, r.estado());
        assertEquals("telefono", r.lista("detalles").get(0).get("campo"));
        assertEquals(0L, bd().uno("SELECT count(*) AS n FROM usuarios").entero("n"));
    }

    @Test
    @DisplayName("HU-02: un celular inválido responde 400")
    void telefonoInvalido() {
        for (String malo : new String[]{"887654321", "98765432", "9876543210", "98765432a", "+52 987654321"}) {
            Map<String, Object> datos = registroValido();
            datos.put("telefono", malo);
            Resp r = post("/api/auth/registro", datos, null);
            assertEquals(400, r.estado(), malo);
            assertEquals("telefono", r.lista("detalles").get(0).get("campo"), malo);
            assertEquals("Ingresa un celular válido de 9 dígitos que empiece con 9",
                    r.lista("detalles").get(0).get("mensaje"), malo);
        }
    }

    @Test
    @DisplayName("HU-02: el celular se guarda normalizado y lo devuelven el registro, el login y /yo")
    void telefonoNormalizado() {
        Map<String, Object> datos = registroValido();
        datos.put("telefono", "+51 987-654-321");
        Resp r = post("/api/auth/registro", datos, null);
        assertEquals(201, r.estado());
        assertEquals("987654321", r.objeto("usuario").get("telefono"));
        assertEquals("987654321", bd().uno("SELECT telefono FROM usuarios").texto("telefono"));

        Resp login = post("/api/auth/login", Json.obj("correo", "ana@correo.com", "contrasena", "secreta123"), null);
        assertEquals("987654321", login.objeto("usuario").get("telefono"));
        Resp yo = get("/api/auth/yo", (String) login.json().get("token"));
        assertEquals("987654321", yo.objeto("usuario").get("telefono"));
    }

    @Test
    @DisplayName("HU-02: también acepta el prefijo 51 sin '+'")
    void telefonoConPrefijo51() {
        Map<String, Object> datos = registroValido();
        datos.put("telefono", "51 912 345 678");
        assertEquals("912345678", post("/api/auth/registro", datos, null).objeto("usuario").get("telefono"));
    }

    @Test
    @DisplayName("HU-02: un usuario antiguo sin celular sigue pudiendo iniciar sesión (telefono = null)")
    void usuarioSinTelefono() {
        Usuario u = crearCliente();
        bd().ejecutar("UPDATE usuarios SET telefono = NULL WHERE id = ?", u.id());
        Resp yo = get("/api/auth/yo", token(u, false));
        assertEquals(200, yo.estado());
        assertTrue(yo.objeto("usuario").containsKey("telefono"));
        assertNull(yo.objeto("usuario").get("telefono"));
    }

    @Test
    @DisplayName("HU-02: la BD rechaza un celular mal formado")
    void bdRechazaTelefonoInvalido() {
        Usuario u = crearCliente();
        ErrorBd e = assertThrows(ErrorBd.class,
                () -> bd().ejecutar("UPDATE usuarios SET telefono = '12345' WHERE id = ?", u.id()));
        assertEquals("ck_usuarios_telefono", e.getRestriccion());
    }

    // ------------------------------------------------------------ Validaciones del registro
    private Resp registrarCon(String campo, Object valor) {
        Map<String, Object> datos = registroValido();
        datos.put(campo, valor);
        return post("/api/auth/registro", datos, null);
    }

    @Test
    @DisplayName("Validación: el nombre acepta tildes, ñ, apóstrofo y guion; recorta y colapsa espacios")
    void nombreValido() {
        Resp r = registrarCon("nombre", "  José   María O'Connor-Pérez  ");
        assertEquals(201, r.estado(), String.valueOf(r.cuerpo()));
        assertEquals("José María O'Connor-Pérez", r.objeto("usuario").get("nombre"));
        assertEquals(201, post("/api/auth/registro", Json.obj("nombre", "Íñigo Güemes", "correo", "inigo@correo.pe",
                "telefono", "912345678", "contrasena", "secreta123"), null).estado());
    }

    @Test
    @DisplayName("Validación: el nombre rechaza números, símbolos, HTML, control y largos fuera de 2–120")
    void nombreInvalido() {
        Map<String, String> casos = Map.of(
                "745865258482", "El nombre solo puede contener letras, espacios, apóstrofos y guiones",
                "Ana123", "El nombre solo puede contener letras, espacios, apóstrofos y guiones",
                "@@@", "El nombre solo puede contener letras, espacios, apóstrofos y guiones",
                "'-'", "El nombre debe contener al menos una letra",
                "A", "El nombre debe tener al menos 2 caracteres",
                "a".repeat(121), "El nombre debe tener como máximo 120 caracteres",
                "   ", "El nombre es obligatorio",
                "Ana <script>", "El nombre no puede contener los signos < ni >",
                "Ana\u0007Torres", "El nombre contiene caracteres no permitidos");
        casos.forEach((malo, mensaje) -> {
            Resp r = registrarCon("nombre", malo);
            assertEquals(400, r.estado(), malo);
            assertEquals(mensaje, r.errorDe("nombre"), malo);
        });
        assertEquals(0L, bd().uno("SELECT count(*) AS n FROM usuarios").entero("n"));
    }

    @Test
    @DisplayName("Validación: el correo no admite espacios, dominios sin punto ni más de 160 caracteres")
    void correoReglas() {
        assertEquals("El correo no tiene un formato válido", registrarCon("correo", "a@b").errorDe("correo"));
        assertEquals("El correo no puede contener espacios", registrarCon("correo", "ana torres@correo.com").errorDe("correo"));
        assertEquals("El correo debe tener como máximo 160 caracteres",
                registrarCon("correo", "a".repeat(150) + "@correo.com").errorDe("correo"));
        assertEquals("El correo es obligatorio", registrarCon("correo", "   ").errorDe("correo"));
        Resp ok = registrarCon("correo", "  Ana.Torres@Correo.PE ");
        assertEquals(201, ok.estado());
        assertEquals("ana.torres@correo.pe", ok.objeto("usuario").get("correo"));
    }

    @Test
    @DisplayName("Validación: la contraseña exige 8–72 caracteres, una letra, un número y sin espacios en los extremos")
    void contrasenaReglas() {
        Map<String, String> casos = Map.of(
                "abcdefgh", "La contraseña debe tener al menos un número",
                "12345678", "La contraseña debe tener al menos una letra",
                " clave123", "La contraseña no puede empezar ni terminar con espacios",
                "clave123 ", "La contraseña no puede empezar ni terminar con espacios",
                "clave12", "La contraseña debe tener al menos 8 caracteres",
                "a1".repeat(37), "La contraseña debe tener como máximo 72 caracteres",
                "", "La contraseña es obligatoria");
        casos.forEach((mala, mensaje) -> assertEquals(mensaje, registrarCon("contrasena", mala).errorDe("contrasena"), mala));
        assertEquals(201, registrarCon("contrasena", "mi clave 123").estado());
    }

    @Test
    @DisplayName("Validación: el login solo revisa el formato (correo válido y contraseña no vacía)")
    void loginSoloFormato() {
        Resp correoMalo = post("/api/auth/login", Json.obj("correo", "ana@", "contrasena", "x"), null);
        assertEquals(400, correoMalo.estado());
        assertEquals("El correo no tiene un formato válido", correoMalo.errorDe("correo"));
        Resp sinClave = post("/api/auth/login", Json.obj("correo", "ana@correo.pe", "contrasena", "   "), null);
        assertEquals(400, sinClave.estado());
        assertEquals("Ingresa tu contraseña", sinClave.errorDe("contrasena"));
    }

    @Test
    @DisplayName("Validación: un usuario antiguo con nombre y contraseña fuera de las reglas nuevas sigue entrando")
    void usuarioAntiguoSigueEntrando() {
        bd().ejecutar("""
                INSERT INTO usuarios (nombre, correo, telefono, contrasena_hash, rol)
                VALUES ('@@ 745865258482', 'antiguo@prueba.pe', '987654321', ?, 'CLIENTE')""",
                new alquiler.seguridad.Contrasenas(1000).cifrar("corta"));
        Resp r = post("/api/auth/login", Json.obj("correo", "antiguo@prueba.pe", "contrasena", "corta"), null);
        assertEquals(200, r.estado(), String.valueOf(r.cuerpo()));
        assertEquals("@@ 745865258482", r.objeto("usuario").get("nombre"));
    }

    // ------------------------------------------------------------ Roles (EN-05)
    @Test
    @DisplayName("EN-05: la BD solo admite los roles CLIENTE y ADMINISTRADOR (no existe PROVEEDOR)")
    void bdRechazaRolProveedor() {
        ErrorBd e = assertThrows(ErrorBd.class, () -> bd().ejecutar("""
                INSERT INTO usuarios (nombre, correo, contrasena_hash, rol)
                VALUES ('Prov', 'prov@prueba.pe', 'x', 'PROVEEDOR')"""));
        assertEquals("usuarios_rol_check", e.getRestriccion());
    }

    @Test
    @DisplayName("EN-05: el registro sigue creando usuarios con rol CLIENTE")
    void registroSigueCreandoCliente() {
        Map<String, Object> datos = registroValido();
        datos.put("rol", "PROVEEDOR"); // se debe ignorar
        assertEquals(201, post("/api/auth/registro", datos, null).estado());
        assertEquals("CLIENTE", bd().uno("SELECT rol FROM usuarios").texto("rol"));
    }

    // ------------------------------------------------------------ HU-02
    @Test
    @DisplayName("HU-02: con credenciales correctas devuelve token y rol")
    void loginCorrecto() {
        Usuario admin = crearUsuario("ADMINISTRADOR", true);
        Resp r = post("/api/auth/login", Json.obj("correo", admin.correo(), "contrasena", admin.contrasena()), null);
        assertEquals(200, r.estado());
        assertNotNull(r.json().get("token"));
        assertEquals("ADMINISTRADOR", r.objeto("usuario").get("rol"));
    }

    @Test
    @DisplayName("HU-02: credenciales incorrectas muestran el mismo error genérico")
    void errorGenerico() {
        Usuario u = crearCliente();
        Resp claveMala = post("/api/auth/login", Json.obj("correo", u.correo(), "contrasena", "otraClave1"), null);
        Resp noExiste = post("/api/auth/login", Json.obj("correo", "nadie@x.pe", "contrasena", "otraClave1"), null);
        assertEquals(401, claveMala.estado());
        assertEquals(401, noExiste.estado());
        assertEquals(claveMala.error(), noExiste.error());
    }

    @Test
    @DisplayName("HU-02: tras 5 intentos fallidos la cuenta se bloquea temporalmente")
    void bloqueoPorIntentos() {
        Usuario u = crearCliente();
        Map<String, Object> mala = Json.obj("correo", u.correo(), "contrasena", "incorrecta");
        for (int i = 0; i < 4; i++) assertEquals(401, post("/api/auth/login", mala, null).estado());
        assertEquals(423, post("/api/auth/login", mala, null).estado());
        // Aun con la contraseña correcta, sigue bloqueada
        Resp correcta = post("/api/auth/login", Json.obj("correo", u.correo(), "contrasena", u.contrasena()), null);
        assertEquals(423, correcta.estado());
    }

    @Test
    @DisplayName("HU-02: un login correcto reinicia el contador de intentos")
    void reiniciaIntentos() {
        Usuario u = crearCliente();
        Map<String, Object> mala = Json.obj("correo", u.correo(), "contrasena", "incorrecta");
        for (int i = 0; i < 4; i++) post("/api/auth/login", mala, null);
        assertEquals(200, post("/api/auth/login", Json.obj("correo", u.correo(), "contrasena", u.contrasena()), null).estado());
        assertEquals(401, post("/api/auth/login", mala, null).estado());
    }

    @Test
    @DisplayName("HU-02: cerrar sesión invalida el token")
    void cerrarSesion() {
        String token = token(crearCliente(), false);
        assertEquals(200, get("/api/auth/yo", token).estado());
        assertEquals(204, post("/api/auth/logout", null, token).estado());
        assertEquals(401, get("/api/auth/yo", token).estado());
    }

    @Test
    @DisplayName("HU-02: la sesión expira tras 30 minutos de inactividad")
    void expiraPorInactividad() {
        String token = token(crearCliente(), false);
        bd().ejecutar("UPDATE sesiones SET ultima_actividad = now() - interval '31 minutes'");
        Resp r = get("/api/auth/yo", token);
        assertEquals(401, r.estado());
        assertTrue(r.error().contains("inactividad"));
    }

    @Test
    @DisplayName("HU-02: \"Recordarme\" extiende la sesión")
    void recordarme() {
        String token = token(crearCliente(), true);
        bd().ejecutar("UPDATE sesiones SET ultima_actividad = now() - interval '2 hours'");
        assertEquals(200, get("/api/auth/yo", token).estado());
        Fila f = bd().uno("SELECT expira_en > now() + interval '20 days' AS larga FROM sesiones");
        assertTrue(f.bool("larga"));
    }

    @Test
    @DisplayName("HU-15: un usuario desactivado no puede iniciar sesión")
    void usuarioDesactivado() {
        Usuario u = crearUsuario("CLIENTE", false);
        assertEquals(403, post("/api/auth/login", Json.obj("correo", u.correo(), "contrasena", u.contrasena()), null).estado());
    }

    // ------------------------------------------------------------ EN-05
    @Test
    @DisplayName("EN-05: sin token, las rutas de administrador responden 401")
    void sinToken() {
        assertEquals(401, get("/api/admin/categorias", null).estado());
    }

    @Test
    @DisplayName("EN-05: un cliente no puede entrar a rutas de administrador")
    void clienteNoEsAdmin() {
        String token = token(crearCliente(), false);
        assertEquals(403, get("/api/admin/categorias", token).estado());
    }

    @Test
    @DisplayName("EN-03: un token alterado es rechazado")
    void tokenAlterado() {
        String token = token(crearCliente(), false);
        assertEquals(401, get("/api/auth/yo", token + "x").estado());
    }
}
