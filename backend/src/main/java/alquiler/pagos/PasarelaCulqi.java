package alquiler.pagos;

import alquiler.config.Config;
import alquiler.json.Json;
import alquiler.util.ErrorApp;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;

public class PasarelaCulqi implements PasarelaPago {

    private static final Logger LOG = LoggerFactory.getLogger(PasarelaCulqi.class);
    private static final String API = "https://api.culqi.com/v2";
    private static final BigDecimal CIEN = new BigDecimal(100);

    private final Config config;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

    public PasarelaCulqi(Config config) {
        this.config = config;
    }

    @Override
    public boolean configurada() {
        return config.culqiSecretKey != null && config.culqiPublicKey != null;
    }

    @Override
    public Preferencia crearPreferencia(String referencia, String titulo, BigDecimal monto, Instant venceEn) {
        throw new ErrorApp(503, "Con Culqi el pago se hace con tarjeta dentro de la página");
    }

    @Override
    public Pago consultarPago(String idPago) {
        Respuesta r = pedir(HttpRequest.newBuilder(URI.create(API + "/charges/" + idPago)).GET());
        if (r.estado == 404) return null;
        if (r.estado / 100 != 2) throw new ErrorApp(502, "No se pudo consultar el pago. Intenta nuevamente");
        return aPago(r.cuerpo);
    }

    @Override
    public String clavePublica() {
        return config.culqiPublicKey;
    }

    @Override
    public Pago pagarConTarjeta(String referencia, String descripcion, BigDecimal monto, DatosTarjeta t) {
        BigDecimal centimos = monto.multiply(CIEN).setScale(0, java.math.RoundingMode.HALF_UP);
        Map<String, Object> cuerpo = Json.obj(
                "amount", centimos.longValueExact(),
                "currency_code", "PEN",
                "email", t.correo(),
                "source_id", t.token(),
                "description", descripcion.length() > 80 ? descripcion.substring(0, 80) : descripcion,
                "capture", true,
                "metadata", Json.obj("reserva", referencia));
        Map<String, Object> antifraude = Json.obj();
        if (t.nombre() != null) antifraude.put("first_name", t.nombre());
        if (t.apellido() != null) antifraude.put("last_name", t.apellido());
        if (t.telefono() != null) antifraude.put("phone_number", t.telefono());
        if (t.dispositivoId() != null) antifraude.put("device_finger_print_id", t.dispositivoId());
        if (!antifraude.isEmpty()) cuerpo.put("antifraud_details", antifraude);
        if (t.autenticacion3ds() != null) cuerpo.put("authentication_3DS", t.autenticacion3ds());

        Respuesta r = pedir(HttpRequest.newBuilder(URI.create(API + "/charges"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(Json.escribir(cuerpo))));
        if ("REVIEW".equals(texto(r.cuerpo.get("action_code")))) {
            return new Pago(null, "pending", null, referencia, monto, DESAFIO_3DS);
        }
        if ("charge".equals(texto(r.cuerpo.get("object")))) {
            return aPago(r.cuerpo);
        }
        String mensaje = texto(r.cuerpo.get("user_message"));
        LOG.warn("Culqi rechazó el cobro de la reserva {}: {}", referencia, texto(r.cuerpo.get("merchant_message")));
        throw new ErrorApp(402, mensaje != null ? mensaje : "El pago fue rechazado. Revisa los datos de tu tarjeta o prueba con otro medio de pago");
    }

    private static Pago aPago(Map<String, Object> c) {
        Object monto = c.get("amount");
        Object reembolsado = c.get("amount_refunded");
        String estado;
        if (reembolsado != null && new BigDecimal(reembolsado.toString()).signum() > 0) {
            estado = "refunded";
        } else if (c.get("outcome") instanceof Map<?, ?> o && "venta_exitosa".equals(texto(o.get("type")))) {
            estado = "approved";
        } else {
            estado = "rejected";
        }
        String referencia = c.get("metadata") instanceof Map<?, ?> m ? texto(m.get("reserva")) : null;
        return new Pago(texto(c.get("id")), estado, texto(c.get("merchant_message")), referencia,
                monto == null ? null : new BigDecimal(monto.toString()).divide(CIEN));
    }

    private record Respuesta(int estado, Map<String, Object> cuerpo) {
    }

    @SuppressWarnings("unchecked")
    private Respuesta pedir(HttpRequest.Builder constructor) {
        HttpRequest peticion = constructor
                .timeout(Duration.ofSeconds(20))
                .header("Authorization", "Bearer " + config.culqiSecretKey)
                .build();
        HttpResponse<String> respuesta;
        try {
            respuesta = http.send(peticion, HttpResponse.BodyHandlers.ofString());
        } catch (IOException e) {
            throw new ErrorApp(503, "No se pudo conectar con Culqi. Intenta nuevamente en unos minutos");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ErrorApp(503, "No se pudo conectar con Culqi. Intenta nuevamente en unos minutos");
        }
        Map<String, Object> cuerpo = Json.obj();
        try {
            if (respuesta.body() != null && !respuesta.body().isBlank()
                    && Json.leer(respuesta.body()) instanceof Map<?, ?> m) {
                cuerpo = (Map<String, Object>) m;
            }
        } catch (Json.JsonInvalidoException e) {
            throw new ErrorApp(502, "Culqi respondió algo inesperado. Intenta nuevamente");
        }
        if (respuesta.statusCode() >= 500) {
            throw new ErrorApp(502, "Culqi no pudo procesar la solicitud. Intenta nuevamente");
        }
        return new Respuesta(respuesta.statusCode(), cuerpo);
    }

    private static String texto(Object v) {
        return v == null ? null : v.toString();
    }
}
