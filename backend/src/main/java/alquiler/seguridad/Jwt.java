package alquiler.seguridad;

import alquiler.json.Json;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * JSON Web Tokens (RFC 7519) firmados con HMAC-SHA256, implementados con el JDK.
 *
 * Un token tiene tres partes separadas por puntos: cabecera.datos.firma
 * La firma impide que alguien modifique los datos (por ejemplo, su rol).
 */
public final class Jwt {

    private static final String CABECERA = base64Url(
            "{\"alg\":\"HS256\",\"typ\":\"JWT\"}".getBytes(StandardCharsets.UTF_8));

    private final byte[] secreto;

    public Jwt(String secreto) {
        if (secreto == null || secreto.length() < 16) {
            throw new IllegalStateException("JWT_SECRET debe tener al menos 16 caracteres");
        }
        this.secreto = secreto.getBytes(StandardCharsets.UTF_8);
    }

    /** Crea un token con los datos dados y vencimiento absoluto. */
    public String firmar(Map<String, Object> datos, String sujeto, Instant expira) {
        Map<String, Object> carga = new LinkedHashMap<>(datos);
        carga.put("sub", sujeto);
        carga.put("iat", Instant.now().getEpochSecond());
        carga.put("exp", expira.getEpochSecond());
        String cuerpo = base64Url(Json.escribir(carga).getBytes(StandardCharsets.UTF_8));
        String contenido = CABECERA + "." + cuerpo;
        return contenido + "." + base64Url(hmac(contenido));
    }

    /**
     * Verifica firma y vencimiento. Devuelve los datos del token
     * o null si el token es inválido, fue alterado o ya venció.
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> verificar(String token) {
        if (token == null) return null;
        String[] partes = token.split("\\.");
        if (partes.length != 3 || !CABECERA.equals(partes[0])) return null;
        try {
            byte[] firmaRecibida = Base64.getUrlDecoder().decode(partes[2]);
            byte[] firmaCorrecta = hmac(partes[0] + "." + partes[1]);
            if (!MessageDigest.isEqual(firmaRecibida, firmaCorrecta)) return null;

            String json = new String(Base64.getUrlDecoder().decode(partes[1]), StandardCharsets.UTF_8);
            Object datos = Json.leer(json);
            if (!(datos instanceof Map)) return null;
            Map<String, Object> carga = (Map<String, Object>) datos;
            Object exp = carga.get("exp");
            if (!(exp instanceof Number n) || n.longValue() <= Instant.now().getEpochSecond()) return null;
            return carga;
        } catch (RuntimeException e) { // Base64 o JSON mal formado
            return null;
        }
    }

    private byte[] hmac(String contenido) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secreto, "HmacSHA256"));
            return mac.doFinal(contenido.getBytes(StandardCharsets.UTF_8));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("HmacSHA256 no disponible", e);
        }
    }

    private static String base64Url(byte[] datos) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(datos);
    }
}
