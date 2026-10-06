package alquiler;

import alquiler.auth.VerificadorGoogleOficial;
import alquiler.config.Config;
import alquiler.json.Json;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verificador real de Google (sin Spring ni internet): los casos que se rechazan
 * antes de revisar la firma, que es lo único que necesita las claves de Google.
 */
class VerificadorGoogleTest {

    private static final String CLIENT_ID = "123-abc.apps.googleusercontent.com";

    private static VerificadorGoogleOficial verificador(String clientId) {
        Map<String, String> valores = Map.of(
                "APP_ENV", "test",
                "DATABASE_URL_TEST", "postgres://u:p@localhost:5432/bd",
                "GOOGLE_CLIENT_ID", clientId);
        return new VerificadorGoogleOficial(Config.desde(valores::get));
    }

    /** JWT con la forma de un ID token de Google pero con firma falsa. */
    private static String tokenFalso(String audiencia, String emisor, long expira) {
        Base64.Encoder b64 = Base64.getUrlEncoder().withoutPadding();
        String cabecera = Json.escribir(Json.obj("alg", "RS256", "kid", "clave", "typ", "JWT"));
        String datos = Json.escribir(Json.obj(
                "iss", emisor, "aud", audiencia, "sub", "g-1", "email", "a@gmail.com", "email_verified", true,
                "iat", Instant.now().getEpochSecond(), "exp", expira));
        return b64.encodeToString(cabecera.getBytes(StandardCharsets.UTF_8)) + "."
                + b64.encodeToString(datos.getBytes(StandardCharsets.UTF_8)) + ".ZmlybWE";
    }

    @Test
    @DisplayName("Google: sin GOOGLE_CLIENT_ID el verificador queda desactivado")
    void sinClientId() {
        VerificadorGoogleOficial v = verificador("");
        assertFalse(v.configurado());
        assertNull(v.verificar(tokenFalso(CLIENT_ID, "https://accounts.google.com", Instant.now().getEpochSecond() + 600)));
    }

    @Test
    @DisplayName("Google: rechaza tokens mal formados, de otra audiencia, de otro emisor o vencidos")
    void rechazaTokensInvalidos() {
        VerificadorGoogleOficial v = verificador(CLIENT_ID);
        long enDiezMinutos = Instant.now().getEpochSecond() + 600;
        assertTrue(v.configurado());
        assertNull(v.verificar(null));
        assertNull(v.verificar(""));
        assertNull(v.verificar("no-es-un-jwt"));
        assertNull(v.verificar("a.b.c"));
        assertNull(v.verificar(tokenFalso("otro-cliente.apps.googleusercontent.com", "https://accounts.google.com", enDiezMinutos)));
        assertNull(v.verificar(tokenFalso(CLIENT_ID, "https://evil.example.com", enDiezMinutos)));
        assertNull(v.verificar(tokenFalso(CLIENT_ID, "https://accounts.google.com", Instant.now().getEpochSecond() - 3600)));
    }
}
