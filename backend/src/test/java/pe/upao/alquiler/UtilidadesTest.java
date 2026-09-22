package pe.upao.alquiler;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import pe.upao.alquiler.http.Multipart;
import pe.upao.alquiler.json.Json;
import pe.upao.alquiler.seguridad.Contrasenas;
import pe.upao.alquiler.seguridad.Jwt;
import pe.upao.alquiler.util.Fechas;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Pruebas unitarias (no usan la base de datos). */
class UtilidadesTest {

    // ------------------------------------------------------------ JSON
    @Test
    @DisplayName("JSON: lee objetos, arreglos, números, textos con escapes y null")
    void jsonLee() {
        Object v = Json.leer("{\"a\":1,\"b\":[true,null,\"x\\n\\u00f1\"],\"c\":1600.50,\"d\":{}}");
        Map<?, ?> m = (Map<?, ?>) v;
        assertEquals(1L, m.get("a"));
        assertEquals(List.of(true, "x\nñ"), List.of(((List<?>) m.get("b")).get(0), ((List<?>) m.get("b")).get(2)));
        assertNull(((List<?>) m.get("b")).get(1));
        assertEquals(new BigDecimal("1600.50"), m.get("c"));
    }

    @Test
    @DisplayName("JSON: escribe en el mismo formato que espera el frontend")
    void jsonEscribe() {
        String s = Json.escribir(Json.obj("n", new BigDecimal("1450.00"), "t", "comillas \" y \\", "nulo", null, "l", List.of(1, 2)));
        assertEquals("{\"n\":1450,\"t\":\"comillas \\\" y \\\\\",\"nulo\":null,\"l\":[1,2]}", s);
    }

    @Test
    @DisplayName("JSON: rechaza textos mal formados")
    void jsonInvalido() {
        for (String malo : new String[]{"{", "{\"a\":}", "[1,]", "{bad}", "01", "\"sin cerrar", "{} extra"}) {
            assertThrows(Json.JsonInvalidoException.class, () -> Json.leer(malo), malo);
        }
    }

    // ------------------------------------------------------------ Seguridad
    @Test
    @DisplayName("Contraseñas: se cifran con sal y solo la correcta coincide")
    void contrasenas() {
        Contrasenas c = new Contrasenas(1000);
        String h1 = c.cifrar("secreta123");
        String h2 = c.cifrar("secreta123");
        assertFalse(h1.equals(h2), "cada hash debe tener su propia sal");
        assertTrue(c.verificar("secreta123", h1));
        assertFalse(c.verificar("otra", h1));
        assertFalse(c.verificar("secreta123", "texto-que-no-es-hash"));
    }

    @Test
    @DisplayName("JWT: firma, verifica y rechaza tokens alterados o vencidos")
    void jwt() {
        Jwt jwt = new Jwt("un-secreto-de-prueba-largo");
        String token = jwt.firmar(Json.obj("sid", "abc", "rol", "CLIENTE"), "7", Instant.now().plusSeconds(60));
        Map<String, Object> datos = jwt.verificar(token);
        assertNotNull(datos);
        assertEquals("abc", datos.get("sid"));
        assertEquals("7", datos.get("sub"));

        String[] p = token.split("\\.");
        String datosFalsos = java.util.Base64.getUrlEncoder().withoutPadding()
                .encodeToString("{\"sid\":\"abc\",\"rol\":\"ADMINISTRADOR\",\"exp\":9999999999}".getBytes(StandardCharsets.UTF_8));
        assertNull(jwt.verificar(p[0] + "." + datosFalsos + "." + p[2]), "no se puede cambiar el rol");
        assertNull(new Jwt("otro-secreto-distinto-largo").verificar(token), "otra clave no sirve");
        assertNull(jwt.verificar(jwt.firmar(Json.obj("sid", "x"), "1", Instant.now().minusSeconds(5))), "vencido");
        assertNull(jwt.verificar("basura"));
    }

    // ------------------------------------------------------------ Fechas
    @Test
    @DisplayName("Fechas: valida formato y fechas reales")
    void fechasValidas() {
        assertTrue(Fechas.esFechaValida("2026-02-28"));
        assertFalse(Fechas.esFechaValida("2026-02-30"));
        assertFalse(Fechas.esFechaValida("28/02/2026"));
        assertFalse(Fechas.esFechaValida(null));
    }

    @Test
    @DisplayName("Fechas: rangos inclusivos, compartir un día ya es superposición")
    void superposicion() {
        Fechas.Rango a = new Fechas.Rango("2026-10-01", "2026-10-05");
        assertTrue(Fechas.seSuperponen(a, new Fechas.Rango("2026-10-05", "2026-10-07")));
        assertFalse(Fechas.seSuperponen(a, new Fechas.Rango("2026-10-06", "2026-10-07")));
        assertNotNull(Fechas.buscarSuperposicion(List.of(
                new Fechas.Rango("2026-10-20", "2026-10-22"),
                new Fechas.Rango("2026-10-01", "2026-10-03"),
                new Fechas.Rango("2026-10-21", "2026-10-25"))));
    }

    @Test
    @DisplayName("Fechas: diasEntre cuenta ambos extremos (mínimo 1 día)")
    void diasEntre() {
        assertEquals(1, Fechas.diasEntre("2026-10-01", "2026-10-01"));
        assertEquals(5, Fechas.diasEntre("2026-10-01", "2026-10-05"));
        assertEquals(3, Fechas.diasEntre("2026-02-27", "2026-03-01"));
    }

    // ------------------------------------------------------------ Multipart
    @Test
    @DisplayName("Multipart: separa campos y archivos respetando los bytes")
    void multipart() {
        byte[] png = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0, 1};
        String b = "XyZ";
        byte[] cuerpo = concatenar(
                ("--" + b + "\r\nContent-Disposition: form-data; name=\"titulo\"\r\n\r\nHola\r\n").getBytes(StandardCharsets.UTF_8),
                ("--" + b + "\r\nContent-Disposition: form-data; name=\"fotos\"; filename=\"a.png\"\r\nContent-Type: image/png\r\n\r\n").getBytes(StandardCharsets.UTF_8),
                png,
                ("\r\n--" + b + "--\r\n").getBytes(StandardCharsets.UTF_8));
        List<Multipart.Parte> partes = Multipart.leer(cuerpo, "multipart/form-data; boundary=" + b);
        assertEquals(2, partes.size());
        assertEquals("titulo", partes.get(0).campo());
        assertFalse(partes.get(0).esArchivo());
        assertEquals("a.png", partes.get(1).nombreArchivo());
        assertEquals("image/png", partes.get(1).tipo());
        assertEquals(png.length, partes.get(1).datos().length);
    }

    private static byte[] concatenar(byte[]... partes) {
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        for (byte[] p : partes) out.writeBytes(p);
        return out.toByteArray();
    }
}
