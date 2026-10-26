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
        if (idPago != null && idPago.startsWith("ORD")) return consultarOrden(idPago);
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

    private Map<String, Object> pedir(HttpRequest.Builder constructor) {
        return pedir(constructor, false);
    }

    /** API de Orders: estado de la orden traducido al vocabulario de pagos que usa el resto del sistema. */
    private static Pago pagoDeOrden(Map<String, Object> o) {
        String estadoOrden = texto(o.get("status"));
        String detalleOrden = texto(o.get("status_detail"));
        String estado = switch (estadoOrden == null ? "" : estadoOrden) {
            case "processed" -> "accredited".equals(detalleOrden) ? "approved" : "in_process";
            case "failed" -> "rejected";
            case "canceled", "cancelled", "expired" -> "cancelled";
            case "refunded" -> "refunded";
            default -> "pending";
        };
        String detalleFinal = detalleOrden;
        if ("rejected".equals(estado)) {
            detalleFinal = texto(o.get("motivo"));
            if (detalleFinal == null && o.get("transactions") instanceof Map<?, ?> tr && tr.get("payments") instanceof List<?> ps
                    && !ps.isEmpty() && ps.get(0) instanceof Map<?, ?> p0 && p0.get("status_detail") != null) {
                detalleFinal = texto(p0.get("status_detail"));
            }
            if (detalleFinal == null || "failed".equals(detalleFinal)) detalleFinal = "other_reason";
            if (!detalleFinal.startsWith("cc_")) detalleFinal = "cc_rejected_" + detalleFinal;
        }
        Object total = o.get("total_amount");
        return new Pago(texto(o.get("id")), estado, detalleFinal, texto(o.get("external_reference")),
                total == null ? null : new BigDecimal(total.toString()));
    }

    /** Respuesta 402 de Orders: la orden viene en "data" y el motivo en errors[0].details. */
    private static Map<String, Object> ordenRechazada(Map<String, Object> data, Object errores) {
        Map<String, Object> copia = new java.util.HashMap<>(data);
        if (errores instanceof List<?> l && !l.isEmpty() && l.get(0) instanceof Map<?, ?> e
                && e.get("details") instanceof List<?> det && !det.isEmpty()) {
            String t = String.valueOf(det.get(0));
            int i = t.lastIndexOf(": ");
            copia.put("motivo", i >= 0 ? t.substring(i + 2).trim() : t);
        }
        return copia;
    }
    private Pago consultarOrden(String idOrden) {
        Map<String, Object> o = pedir(HttpRequest.newBuilder(URI.create(API + "/v1/orders/" + idOrden)).GET());
        return o == null ? null : pagoDeOrden(o);
    }
    @Override
    public String clavePublica() {
        return config.mpPublicKey;
    }

    @Override
    public Pago pagarConTarjeta(String referencia, String descripcion, BigDecimal monto, DatosTarjeta t) {
        String importe = monto.setScale(2, java.math.RoundingMode.HALF_UP).toPlainString();
        String tipo = t.metodoPagoId() != null && t.metodoPagoId().startsWith("deb") ? "debit_card" : "credit_card";
        Map<String, Object> pago = Json.obj(
                "amount", importe,
                "payment_method", Json.obj(
                        "id", t.metodoPagoId(),
                        "type", tipo,
                        "token", t.token(),
                        "installments", 1));
        Map<String, Object> cuerpo = Json.obj(
                "type", "online",
                "processing_mode", "automatic",
                "total_amount", importe,
                "external_reference", referencia,
                "transactions", Json.obj("payments", List.of(pago)),
                "payer", Json.obj("email", t.correo()));
        Map<String, Object> r = pedir(HttpRequest.newBuilder(URI.create(API + "/v1/orders"))
                .header("Content-Type", "application/json")
                .header("X-Idempotency-Key", java.util.UUID.randomUUID().toString())
                .POST(HttpRequest.BodyPublishers.ofString(Json.escribir(cuerpo))), true);
        if (r == null || r.get("id") == null) {
            throw new ErrorApp(502, "No se pudo procesar el pago. Intenta nuevamente");
        }
        return pagoDeOrden(r);
    }

    /** Envía la petición. Devuelve el JSON de respuesta, o null si Mercado Pago responde 404. */
    @SuppressWarnings("unchecked")
    private Map<String, Object> pedir(HttpRequest.Builder constructor, boolean tolerarRechazo) {
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
            if (tolerarRechazo && respuesta.body() != null) {
                try {
                    Object posible = Json.leer(respuesta.body());
                    if (posible instanceof Map<?, ?> m && m.get("transactions") != null) return (Map<String, Object>) posible;
                    if (posible instanceof Map<?, ?> m2 && m2.get("data") instanceof Map<?, ?> d && d.get("id") != null) return ordenRechazada((Map<String, Object>) d, m2.get("errors"));
                } catch (Json.JsonInvalidoException ignorado) {
                    // se informa abajo como error generico
                }
            }            throw new ErrorApp(502, "Mercado Pago no pudo procesar la solicitud. Intenta nuevamente");
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