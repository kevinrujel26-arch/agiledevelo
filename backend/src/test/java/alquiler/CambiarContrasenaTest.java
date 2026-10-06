package alquiler;

import alquiler.auth.VerificadorGoogle.CuentaGoogle;
import alquiler.json.Json;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

/** Pruebas de cambiar contraseña en Mi cuenta (PATCH /api/auth/cambiar-contrasena). */
class CambiarContrasenaTest extends PruebaBase {

    private static final String TOKEN = "token-google";

    @BeforeEach
    void configurarGoogle() {
        when(verificadorGoogle.configurado()).thenReturn(true);
    }

    private void googleResponde(String id, String correo, String nombre) {
        when(verificadorGoogle.verificar(TOKEN)).thenReturn(
                new CuentaGoogle(id, correo, true, nombre));
    }

    private Resp entrarConGoogle() {
        return post("/api/auth/google", Json.obj("credential", TOKEN), null);
    }

    private Resp registrarCliente(String correo, String contrasena) {
        return post("/api/auth/registro", Json.obj(
                "nombre", "Test",
                "correo", correo,
                "telefono", "987654321",
                "contrasena", contrasena), null);
    }

    private Resp iniciarSesion(String correo, String contrasena) {
        return post("/api/auth/login", Json.obj(
                "correo", correo,
                "contrasena", contrasena), null);
    }

    @Test
    @DisplayName("Cambiar contraseña: cambio correcto devuelve 200 y actualiza")
    void cambioCorrectoDosContraseñasValidas() {
        registrarCliente("ana@test.com", "Abc123456");
        Resp r1 = iniciarSesion("ana@test.com", "Abc123456");
        String token = (String) r1.json().get("token");

        // Primero verifica que el token funciona con otro endpoint
        assertEquals(200, get("/api/auth/yo", token).estado(), "Token debe funcionar con GET /api/auth/yo");

        Resp r = patch("/api/auth/cambiar-contrasena", Json.obj(
                "actual", "Abc123456",
                "nueva", "Xyz987654",
                "confirmacion", "Xyz987654"), token);

        assertEquals(200, r.estado(), String.valueOf(r.cuerpo()));
        assertTrue(r.json().containsKey("usuario"));
    }

    @Test
    @DisplayName("Cambiar contraseña: contraseña actual incorrecta devuelve 401 y sesión sigue válida")
    void contraseñaActualIncorrectaDevuelve401() {
        registrarCliente("beto@test.com", "Abc123456");
        Resp r1 = iniciarSesion("beto@test.com", "Abc123456");
        String token = (String) r1.json().get("token");

        // Intenta cambiar con contraseña actual incorrecta
        Resp r = patch("/api/auth/cambiar-contrasena", Json.obj(
                "actual", "Incorrecta",
                "nueva", "Xyz987654",
                "confirmacion", "Xyz987654"), token);

        assertEquals(401, r.estado());
        assertEquals("La contraseña actual es incorrecta", r.error());

        // La sesión debe seguir siendo válida: otra llamada autenticada funciona
        Resp r2 = get("/api/auth/yo", token);
        assertEquals(200, r2.estado());
        assertEquals("beto@test.com", r2.objeto("usuario").get("correo"));
    }

    @Test
    @DisplayName("Cambiar contraseña: nueva igual a la actual devuelve 400")
    void nuevaIgualAActualRechazada() {
        registrarCliente("carlos@test.com", "Abc123456");
        Resp r1 = iniciarSesion("carlos@test.com", "Abc123456");
        String token = (String) r1.json().get("token");

        Resp r = patch("/api/auth/cambiar-contrasena", Json.obj(
                "actual", "Abc123456",
                "nueva", "Abc123456",
                "confirmacion", "Abc123456"), token);

        assertEquals(400, r.estado());
        assertEquals("La nueva contraseña no puede ser igual a la actual", r.error());
    }

    @Test
    @DisplayName("Cambiar contraseña: nueva que incumple reglas devuelve 400")
    void nuevaIncumpleReglasRechazada() {
        registrarCliente("diana@test.com", "Abc123456");
        Resp r1 = iniciarSesion("diana@test.com", "Abc123456");
        String token = (String) r1.json().get("token");

        // Contraseña con menos de 8 caracteres
        Resp r = patch("/api/auth/cambiar-contrasena", Json.obj(
                "actual", "Abc123456",
                "nueva", "Abc123",
                "confirmacion", "Abc123"), token);

        assertEquals(400, r.estado());
    }

    @Test
    @DisplayName("Cambiar contraseña: cuenta de Google sin contraseña rechazada")
    void cuentaGoogleSinContraseñaRechazada() {
        googleResponde("g-300", "emma@gmail.com", "Emma");
        Resp r1 = entrarConGoogle();
        String token = (String) r1.json().get("token");

        Resp r = patch("/api/auth/cambiar-contrasena", Json.obj(
                "actual", "cualquier",
                "nueva", "Abc123456",
                "confirmacion", "Abc123456"), token);

        assertEquals(403, r.estado());
        assertEquals("Esta cuenta usa Google para iniciar sesión. No tiene contraseña", r.error());
    }

    @Test
    @DisplayName("Cambiar contraseña: tras el cambio, las otras sesiones se cierran")
    void otrasSessionesSeCierran() {
        registrarCliente("fabio@test.com", "Abc123456");
        Resp r1 = iniciarSesion("fabio@test.com", "Abc123456");
        String token1 = (String) r1.json().get("token");

        // Abre una segunda sesión
        Resp r2 = iniciarSesion("fabio@test.com", "Abc123456");
        String token2 = (String) r2.json().get("token");

        // Verifica que ambas sesiones son válidas
        assertEquals(200, get("/api/auth/yo", token1).estado());
        assertEquals(200, get("/api/auth/yo", token2).estado());

        // Cambia la contraseña con token1
        patch("/api/auth/cambiar-contrasena", Json.obj(
                "actual", "Abc123456",
                "nueva", "Xyz987654",
                "confirmacion", "Xyz987654"), token1);

        // token1 sigue siendo válido
        assertEquals(200, get("/api/auth/yo", token1).estado());

        // token2 se cerró (sus otras sesiones fueron revocadas)
        assertEquals(401, get("/api/auth/yo", token2).estado());
    }

    @Test
    @DisplayName("Cambiar contraseña: sin sesión devuelve 401")
    void sinSesiónRechazado() {
        Resp r = patch("/api/auth/cambiar-contrasena", Json.obj(
                "actual", "Abc123456",
                "nueva", "Xyz987654",
                "confirmacion", "Xyz987654"), null);

        assertEquals(401, r.estado());
    }

    @Test
    @DisplayName("Cambiar contraseña: tras el cambio se puede iniciar sesión con la nueva")
    void puedeiniciarSesiónConNuevaContrasena() {
        registrarCliente("gloria@test.com", "Abc123456");
        Resp r1 = iniciarSesion("gloria@test.com", "Abc123456");
        String token = (String) r1.json().get("token");

        // Cambia la contraseña
        patch("/api/auth/cambiar-contrasena", Json.obj(
                "actual", "Abc123456",
                "nueva", "Xyz987654",
                "confirmacion", "Xyz987654"), token);

        // Cierra la sesión
        post("/api/auth/logout", null, token);

        // Intenta iniciar sesión con la vieja contraseña (debe fallar)
        Resp r3 = iniciarSesion("gloria@test.com", "Abc123456");
        assertEquals(401, r3.estado());

        // Intenta iniciar sesión con la nueva contraseña (debe funcionar)
        Resp r4 = iniciarSesion("gloria@test.com", "Xyz987654");
        assertEquals(200, r4.estado());
        assertTrue(r4.json().containsKey("token"));
    }
}
