package alquiler;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import alquiler.bd.Fila;
import alquiler.json.Json;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Pruebas de aceptación: HU-01, HU-02, EN-03 y EN-05. */
class AuthTest extends PruebaBase {

    private static Map<String, Object> registroValido() {
        return Json.obj("nombre", "Ana Torres", "correo", "Ana@Correo.com", "contrasena", "secreta123");
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
    @DisplayName("HU-01: nombre, correo y contraseña son obligatorios")
    void camposObligatorios() {
        for (String campo : new String[]{"nombre", "correo", "contrasena"}) {
            Map<String, Object> datos = registroValido();
            datos.remove(campo);
            assertEquals(400, post("/api/auth/registro", datos, null).estado(), "sin " + campo);
        }
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
