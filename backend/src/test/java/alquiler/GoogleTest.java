package alquiler;

import alquiler.auth.AuthServicio;
import alquiler.auth.VerificadorGoogle.CuentaGoogle;
import alquiler.bd.ErrorBd;
import alquiler.bd.Fila;
import alquiler.json.Json;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/** Pruebas de aceptación: registrarse e iniciar sesión con Google (ID token simulado). */
class GoogleTest extends PruebaBase {

    private static final String TOKEN = "token-de-google";

    @BeforeEach
    void configurarGoogle() {
        when(verificadorGoogle.configurado()).thenReturn(true);
    }

    private void googleResponde(String id, String correo, boolean verificado, String nombre) {
        when(verificadorGoogle.verificar(TOKEN)).thenReturn(new CuentaGoogle(id, correo, verificado, nombre));
    }

    private static Resp entrarConGoogle() {
        return post("/api/auth/google", Json.obj("credential", TOKEN), null);
    }

    private static long cuantosUsuarios() {
        return bd().uno("SELECT count(*) AS n FROM usuarios").entero("n");
    }

    // ------------------------------------------------------------ Usuario nuevo
    @Test
    @DisplayName("Google: un correo nuevo crea un CLIENTE sin contraseña ni celular y abre sesión")
    void usuarioNuevo() {
        googleResponde("g-100", "Luis.Rojas@Gmail.com", true, "Luis Rojas");
        Resp r = entrarConGoogle();

        assertEquals(200, r.estado(), String.valueOf(r.cuerpo()));
        assertEquals(true, r.json().get("nuevo"));
        assertNotNull(r.json().get("token"));
        assertNotNull(r.json().get("expiraEn"));
        Map<String, Object> usuario = r.objeto("usuario");
        assertEquals("luis.rojas@gmail.com", usuario.get("correo"));
        assertEquals("Luis Rojas", usuario.get("nombre"));
        assertEquals("CLIENTE", usuario.get("rol"));
        assertTrue(usuario.containsKey("telefono"));
        assertNull(usuario.get("telefono"));

        Fila f = bd().uno("SELECT google_id, contrasena_hash, telefono, rol FROM usuarios");
        assertEquals("g-100", f.texto("google_id"));
        assertNull(f.texto("contrasena_hash"));
        assertNull(f.texto("telefono"));
        assertEquals("CLIENTE", f.texto("rol"));

        // Es la misma sesión del login normal: /yo y logout funcionan igual
        String token = (String) r.json().get("token");
        assertEquals("luis.rojas@gmail.com", get("/api/auth/yo", token).objeto("usuario").get("correo"));
        assertEquals(204, post("/api/auth/logout", null, token).estado());
        assertEquals(401, get("/api/auth/yo", token).estado());
    }

    @Test
    @DisplayName("Google: entrar dos veces con la misma cuenta no duplica el usuario")
    void segundaVezNoDuplica() {
        googleResponde("g-100", "luis@gmail.com", true, "Luis");
        long id = numero(entrarConGoogle().objeto("usuario").get("id"));
        Resp otra = entrarConGoogle();
        assertEquals(200, otra.estado());
        assertEquals(false, otra.json().get("nuevo"));
        assertEquals(id, numero(otra.objeto("usuario").get("id")));
        assertEquals(1L, cuantosUsuarios());
    }

    @Test
    @DisplayName("Google: nunca crea un ADMINISTRADOR aunque lo pidan")
    void nuncaCreaAdmin() {
        googleResponde("g-100", "luis@gmail.com", true, "Luis");
        Resp r = post("/api/auth/google", Json.obj("credential", TOKEN, "rol", "ADMINISTRADOR"), null);
        assertEquals("CLIENTE", r.objeto("usuario").get("rol"));
        assertEquals("CLIENTE", bd().uno("SELECT rol FROM usuarios").texto("rol"));
    }

    @Test
    @DisplayName("Google: sin nombre usa el correo; limpia < > y recorta a 120 caracteres")
    void nombreLimpio() {
        googleResponde("g-1", "sin.nombre@gmail.com", true, null);
        assertEquals("sin.nombre", entrarConGoogle().objeto("usuario").get("nombre"));

        googleResponde("g-2", "largo@gmail.com", true, "Ana <b>" + "x".repeat(200));
        String nombre = (String) entrarConGoogle().objeto("usuario").get("nombre");
        assertFalse(nombre.contains("<"));
        assertEquals(120, nombre.length());
    }

    @Test
    @DisplayName("Google: \"recordarme\" extiende la sesión igual que en el login normal")
    void recordarme() {
        googleResponde("g-100", "luis@gmail.com", true, "Luis");
        Resp r = post("/api/auth/google", Json.obj("credential", TOKEN, "recordarme", true), null);
        assertEquals(true, r.json().get("recordarme"));
        assertTrue(bd().uno("SELECT expira_en > now() + interval '20 days' AS larga FROM sesiones").bool("larga"));
    }

    // ------------------------------------------------------------ Usuario existente
    @Test
    @DisplayName("Google: si el correo ya existe, vincula la cuenta y conserva rol, nombre y celular")
    void vinculaUsuarioExistente() {
        Usuario u = crearCliente();
        googleResponde("g-200", u.correo().toUpperCase(), true, "Otro Nombre");
        Resp r = entrarConGoogle();

        assertEquals(200, r.estado(), String.valueOf(r.cuerpo()));
        assertEquals(false, r.json().get("nuevo"));
        assertEquals(u.id(), numero(r.objeto("usuario").get("id")));
        assertEquals("987654321", r.objeto("usuario").get("telefono"));
        assertEquals(1L, cuantosUsuarios());
        Fila f = bd().uno("SELECT google_id, nombre, contrasena_hash FROM usuarios WHERE id = ?", u.id());
        assertEquals("g-200", f.texto("google_id"));
        assertTrue(f.texto("nombre").startsWith("Usuario "), "no se cambia el nombre por el de Google");
        assertNotNull(f.texto("contrasena_hash"));

        // El login normal sigue funcionando con su contraseña
        assertEquals(200, post("/api/auth/login", Json.obj("correo", u.correo(), "contrasena", u.contrasena()), null).estado());
    }

    @Test
    @DisplayName("Google: un administrador existente entra con su rol, sin cambiarlo")
    void adminExistenteConservaRol() {
        Usuario admin = crearUsuario("ADMINISTRADOR", true);
        googleResponde("g-300", admin.correo(), true, "Admin");
        Resp r = entrarConGoogle();
        assertEquals("ADMINISTRADOR", r.objeto("usuario").get("rol"));
        assertEquals(200, get("/api/admin/categorias", (String) r.json().get("token")).estado());
    }

    @Test
    @DisplayName("Google: si el correo ya está vinculado a otra cuenta de Google responde 409")
    void correoVinculadoAOtraCuenta() {
        Usuario u = crearCliente();
        bd().ejecutar("UPDATE usuarios SET google_id = 'g-original' WHERE id = ?", u.id());
        googleResponde("g-otro", u.correo(), true, "Intruso");
        Resp r = entrarConGoogle();
        assertEquals(409, r.estado());
        assertEquals("g-original", bd().uno("SELECT google_id FROM usuarios").texto("google_id"));
    }

    @Test
    @DisplayName("Google: un usuario desactivado no puede entrar (403) y no se vincula")
    void usuarioDesactivado() {
        Usuario u = crearUsuario("CLIENTE", false);
        googleResponde("g-400", u.correo(), true, "Inactivo");
        assertEquals(403, entrarConGoogle().estado());
        assertNull(bd().uno("SELECT google_id FROM usuarios").texto("google_id"));
    }

    // ------------------------------------------------------------ Token inválido / correo sin verificar
    @Test
    @DisplayName("Google: un token inválido responde 401 con mensaje claro y no crea nada")
    void tokenInvalido() {
        when(verificadorGoogle.verificar(anyString())).thenReturn(null);
        Resp r = entrarConGoogle();
        assertEquals(401, r.estado());
        assertEquals("No se pudo verificar tu cuenta de Google. Intenta nuevamente", r.error());
        assertFalse(r.json().containsKey("token"));
        assertEquals(0L, cuantosUsuarios());
    }

    @Test
    @DisplayName("Google: si el correo de Google no está verificado responde 401 y no crea ni vincula")
    void correoNoVerificado() {
        Usuario u = crearCliente();
        googleResponde("g-500", u.correo(), false, "Sin verificar");
        Resp r = entrarConGoogle();
        assertEquals(401, r.estado());
        assertTrue(r.error().contains("no está verificado"));
        assertNull(bd().uno("SELECT google_id FROM usuarios").texto("google_id"));

        googleResponde("g-501", "nuevo@gmail.com", false, "Nuevo");
        assertEquals(401, entrarConGoogle().estado());
        assertEquals(1L, cuantosUsuarios());
    }

    @Test
    @DisplayName("Google: sin credential responde 400 con el detalle del campo")
    void sinCredencial() {
        Resp r = post("/api/auth/google", Json.obj(), null);
        assertEquals(400, r.estado());
        assertEquals("Falta el token de Google", r.errorDe("credential"));
    }

    @Test
    @DisplayName("Google: sin GOOGLE_CLIENT_ID el endpoint responde 503")
    void sinConfigurar() {
        when(verificadorGoogle.configurado()).thenReturn(false);
        Resp r = entrarConGoogle();
        assertEquals(503, r.estado());
        assertEquals("El inicio de sesión con Google no está disponible", r.error());
    }

    // ------------------------------------------------------------ Login normal
    @Test
    @DisplayName("Google: una cuenta creada con Google no entra con correo y contraseña (\"Esta cuenta usa Google\")")
    void cuentaGoogleNoAceptaContrasena() {
        googleResponde("g-100", "luis@gmail.com", true, "Luis");
        entrarConGoogle();

        for (String clave : new String[]{"cualquiera1", "x", "null", "contrasena-de-relleno"}) {
            Resp r = post("/api/auth/login", Json.obj("correo", "luis@gmail.com", "contrasena", clave), null);
            assertEquals(401, r.estado(), clave);
            assertEquals(AuthServicio.MENSAJE_CUENTA_GOOGLE, r.error(), clave);
        }
        // Contraseña vacía: ni siquiera pasa la validación
        assertEquals(400, post("/api/auth/login", Json.obj("correo", "luis@gmail.com", "contrasena", ""), null).estado());
        // Esos intentos no bloquean la cuenta: con Google sigue entrando
        assertEquals(0L, bd().uno("SELECT intentos_fallidos AS n FROM usuarios").entero("n"));
        assertEquals(200, entrarConGoogle().estado());
    }

    @Test
    @DisplayName("Google: el login normal sigue igual para las cuentas con contraseña")
    void loginNormalIntacto() {
        Usuario u = crearCliente();
        Resp ok = post("/api/auth/login", Json.obj("correo", u.correo(), "contrasena", u.contrasena()), null);
        assertEquals(200, ok.estado());
        assertNotNull(ok.json().get("token"));
        assertFalse(ok.json().containsKey("nuevo"));
        Resp mala = post("/api/auth/login", Json.obj("correo", u.correo(), "contrasena", "otraClave1"), null);
        assertEquals(401, mala.estado());
        assertEquals(AuthServicio.MENSAJE_CREDENCIALES, mala.error());
    }

    @Test
    @DisplayName("Google: la BD exige que toda cuenta tenga contraseña o Google")
    void bdExigeFormaDeIngreso() {
        ErrorBd e = assertThrows(ErrorBd.class, () -> bd().ejecutar("""
                INSERT INTO usuarios (nombre, correo, rol) VALUES ('Nadie', 'nadie@prueba.pe', 'CLIENTE')"""));
        assertEquals("ck_usuarios_forma_de_ingreso", e.getRestriccion());
    }

    @Test
    @DisplayName("Google: google_id no se repite en la BD")
    void bdGoogleIdUnico() {
        Usuario a = crearCliente();
        Usuario b = crearCliente();
        bd().ejecutar("UPDATE usuarios SET google_id = 'g-1' WHERE id = ?", a.id());
        ErrorBd e = assertThrows(ErrorBd.class,
                () -> bd().ejecutar("UPDATE usuarios SET google_id = 'g-1' WHERE id = ?", b.id()));
        assertEquals("ux_usuarios_google_id", e.getRestriccion());
    }

    // ------------------------------------------------------------ Completar el celular
    @Test
    @DisplayName("Mi cuenta: quien entró con Google completa su celular con la misma validación de 9 dígitos")
    void completarCelular() {
        googleResponde("g-100", "luis@gmail.com", true, "Luis");
        String token = (String) entrarConGoogle().json().get("token");

        Resp malo = patch("/api/auth/yo", Json.obj("telefono", "887654321"), token);
        assertEquals(400, malo.estado());
        assertEquals("Ingresa un celular válido de 9 dígitos que empiece con 9", malo.errorDe("telefono"));
        assertEquals("El celular es obligatorio", patch("/api/auth/yo", Json.obj(), token).errorDe("telefono"));

        Resp ok = patch("/api/auth/yo", Json.obj("telefono", "+51 912-345-678"), token);
        assertEquals(200, ok.estado(), String.valueOf(ok.cuerpo()));
        assertEquals("912345678", ok.objeto("usuario").get("telefono"));
        assertEquals("912345678", get("/api/auth/yo", token).objeto("usuario").get("telefono"));
    }

    @Test
    @DisplayName("Mi cuenta: sin sesión no se puede cambiar el celular (401)")
    void completarCelularSinSesion() {
        assertEquals(401, patch("/api/auth/yo", Json.obj("telefono", "912345678"), null).estado());
    }
}
