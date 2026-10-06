package alquiler;

import alquiler.auth.VerificadorGoogle.CuentaGoogle;
import alquiler.json.Json;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

/** Pruebas de completar registro tras entrar con Google (PATCH /api/auth/yo con celular). */
class CompletarRegistroTest extends PruebaBase {

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

    private String obtenToken() {
        googleResponde("g-200", "juan@gmail.com", "Juan");
        return (String) entrarConGoogle().json().get("token");
    }

    @Test
    @DisplayName("Completar celular: celular válido se guarda y devuelve tieneCelular=true")
    void celularValidoSeGuarda() {
        String token = obtenToken();

        Resp r = patch("/api/auth/yo", Json.obj("telefono", "987654321"), token);

        assertEquals(200, r.estado(), String.valueOf(r.cuerpo()));
        Map<String, Object> usuario = r.objeto("usuario");
        assertEquals("987654321", usuario.get("telefono"));
        assertTrue((Boolean) usuario.get("tieneCelular"));
    }

    @Test
    @DisplayName("Completar celular: celular con 8 dígitos devuelve 400")
    void celularOchoDígitosRechazado() {
        String token = obtenToken();

        Resp r = patch("/api/auth/yo", Json.obj("telefono", "98765432"), token);

        assertEquals(400, r.estado());
        assertEquals("Ingresa un celular válido de 9 dígitos que empiece con 9", r.errorDe("telefono"));
    }

    @Test
    @DisplayName("Completar celular: celular que no empieza con 9 devuelve 400")
    void celularSinNueveRechazado() {
        String token = obtenToken();

        Resp r = patch("/api/auth/yo", Json.obj("telefono", "887654321"), token);

        assertEquals(400, r.estado());
        assertEquals("Ingresa un celular válido de 9 dígitos que empiece con 9", r.errorDe("telefono"));
    }

    @Test
    @DisplayName("Completar celular: celular con letras devuelve 400")
    void celularConLetrasRechazado() {
        String token = obtenToken();

        Resp r = patch("/api/auth/yo", Json.obj("telefono", "98765432a"), token);

        assertEquals(400, r.estado());
        assertEquals("Ingresa un celular válido de 9 dígitos que empiece con 9", r.errorDe("telefono"));
    }

    @Test
    @DisplayName("Completar celular: sin sesión devuelve 401")
    void sinSesiónRechazado() {
        Resp r = patch("/api/auth/yo", Json.obj("telefono", "987654321"), null);

        assertEquals(401, r.estado());
    }

    @Test
    @DisplayName("Completar celular: usuario que ya tiene celular puede actualizarlo")
    void actualizarCelularExistente() {
        String token = obtenToken();
        // Primero guarda un celular
        patch("/api/auth/yo", Json.obj("telefono", "912345678"), token);

        // Ahora lo actualiza
        Resp r = patch("/api/auth/yo", Json.obj("telefono", "987654321"), token);

        assertEquals(200, r.estado());
        Map<String, Object> usuario = r.objeto("usuario");
        assertEquals("987654321", usuario.get("telefono"));
        assertTrue((Boolean) usuario.get("tieneCelular"));
    }
}
