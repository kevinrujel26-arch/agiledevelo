package alquiler.pagos;

import alquiler.config.Config;
import alquiler.json.Json;
import alquiler.util.ErrorApp;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

/**
 * Mercado Pago Checkout Pro por su API REST (sin SDK). El Access Token solo se
 * envía en la cabecera Authorization y nunca se escribe en los logs.
 */
@Component
public class PasarelaMercadoPago implements PasarelaPago {

    private static final Logger LOG = LoggerFactory.getLogger(PasarelaMercadoPago.class);
    private static final String API = "https://api.mercadopago.com";
    private static final DateTimeFormatter FECHA_MP = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSXXX");

    private final Config config;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

    public PasarelaMercadoPago(Config config) {
        this.config = config;
    }

    @Override
    public boolean configurada() {
        return config.mpAccessToken != null;
    }

    @Override
    public Preferencia crearPreferencia(String referencia, String titulo, BigDecimal monto, Instant venceEn) {
        String volver = config.urlFrontend + "/pago/resultado?reserva=" + referencia;
        Map<String, Object> cuerpo = Json.obj(
                "items", List.of(Json.obj(
                        "title", titulo,
                        "quantity", 1,
                        "currency_id", "PEN",
                        "unit_price", monto)),
                "external_reference", referencia,
                "back_urls", Json.obj("success", volver, "failure", volver, "pending", volver),
                "notification_url", config.urlBackend + "/api/pagos/webhook");
        // Mercado Pago no acepta "auto_return" con direcciones locales (http://localhost)
        if (config.urlFrontend.startsWith("https://")) cuerpo.put("auto_return", "approved");
        // Siempre en 1 cuota. Yape solo hasta el monto máximo (su tope por operación es bajo)
        Map<String, Object> metodos = Json.obj("installments", 1, "default_installments", 1);
        if (monto.compareTo(config.mpYapeMaximo) > 0) {
            metodos.put("excluded_payment_methods", List.of(Json.obj("id", "yape")));
        }
        cuerpo.put("payment_methods", metodos);
        if (venceEn != null) {
            cuerpo.put("expires", true);
            cuerpo.put("expiration_date_to", FECHA_MP.format(venceEn.atZone(ZoneId.of(config.zonaHoraria))));
        }

        Map<String, Object> r = pedir(HttpRequest.newBuilder(URI.create(API + "/checkout/preferences"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(Json.escribir(cuerpo))));
        if (r == null || r.get("id") == null || r.get("init_point") == null) {
            throw new ErrorApp(502, "No se pudo iniciar el pago. Intenta nuevamente");
        }
        return new Preferencia(String.valueOf(r.get("id")), String.valueOf(r.get("init_point")));
    }

    @Override
    public Pago consultarPago(String idPago) {
        Map<String, Object> r = pedir(HttpRequest.newBuilder(URI.create(API + "/v1/payments/" + idPago)).GET());
        if (r == null) return null;
        Object monto = r.get("transaction_amount");
        return new Pago(
                String.valueOf(r.get("id")),
                texto(r.get("status")),
                texto(r.get("status_detail")),
                texto(r.get("external_reference")),
                monto == null ? null : new BigDecimal(monto.toString()));
    }

    @Override
    public String clavePublica() {
        return config.mpPublicKey;
    }

    @Override
    public Pago pagarConTarjeta(String referencia, String descripcion, BigDecimal monto, DatosTarjeta t) {
        Map<String, Object> pagador = Json.obj("email", t.correo());
        if (t.tipoDocumento() != null && t.numeroDocumento() != null) {
            pagador.put("identification", Json.obj("type", t.tipoDocumento(), "number", t.numeroDocumento()));
        }
        Map<String, Object> cuerpo = Json.obj(
                "transaction_amount", monto,
                "token", t.token(),
                "description", descripcion,
                "installments", 1,
                "payment_method_id", t.metodoPagoId(),
                "payer", pagador,
                "external_reference", referencia);
        if (t.emisorId() != null) cuerpo.put("issuer_id", Long.valueOf(t.emisorId()));
        // Mercado Pago no acepta direcciones locales como notification_url en los pagos
        if (config.urlBackend.startsWith("https://")) {
            cuerpo.put("notification_url", config.urlBackend + "/api/pagos/webhook");
        }

        Map<String, Object> r = pedir(HttpRequest.newBuilder(URI.create(API + "/v1/payments"))
                .header("Content-Type", "application/json")
                .header("X-Idempotency-Key", java.util.UUID.randomUUID().toString())
                .POST(HttpRequest.BodyPublishers.ofString(Json.escribir(cuerpo))));
        if (r == null || r.get("id") == null) {
            throw new ErrorApp(502, "No se pudo procesar el pago. Intenta nuevamente");
        }
        Object pagado = r.get("transaction_amount");
        return new Pago(String.valueOf(r.get("id")), texto(r.get("status")), texto(r.get("status_detail")),
                texto(r.get("external_reference")), pagado == null ? null : new BigDecimal(pagado.toString()));
    }

    /** Envía la petición. Devuelve el JSON de respuesta, o null si Mercado Pago responde 404. */
    @SuppressWarnings("unchecked")
    private Map<String, Object> pedir(HttpRequest.Builder constructor) {
        HttpRequest peticion = constructor
                .timeout(Duration.ofSeconds(15))
                .header("Authorization", "Bearer " + config.mpAccessToken)
                .build();
        HttpResponse<String> respuesta;
        try {
            respuesta = http.send(peticion, HttpResponse.BodyHandlers.ofString());
        } catch (IOException e) {
            throw new ErrorApp(503, "No se pudo conectar con Mercado Pago. Intenta nuevamente en unos minutos");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ErrorApp(503, "No se pudo conectar con Mercado Pago. Intenta nuevamente en unos minutos");
        }
        if (respuesta.statusCode() == 404) return null;
        if (respuesta.statusCode() / 100 != 2) {
            String detalle = respuesta.body() == null ? "" : respuesta.body().substring(0, Math.min(400, respuesta.body().length()));
            LOG.warn("Mercado Pago respondió {} a {} {}: {}", respuesta.statusCode(), peticion.method(), peticion.uri().getPath(), detalle);
            throw new ErrorApp(502, "Mercado Pago no pudo procesar la solicitud. Intenta nuevamente");
        }
        try {
            Object datos = Json.leer(respuesta.body());
            return datos instanceof Map ? (Map<String, Object>) datos : null;
        } catch (Json.JsonInvalidoException e) {
            throw new ErrorApp(502, "Mercado Pago respondió algo inesperado. Intenta nuevamente");
        }
    }

    private static String texto(Object v) {
        return v == null ? null : v.toString();
    }
}