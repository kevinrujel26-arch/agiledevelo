package alquiler.pagos;

import alquiler.http.Respuesta;
import alquiler.http.Solicitud;
import alquiler.json.Json;
import alquiler.util.ErrorApp;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.regex.Pattern;

/** HU-07: pago de reservas con Mercado Pago. */
@RestController
public class PagoControlador {

    private static final Pattern ID_PAGO = Pattern.compile("\\d{1,20}");

    private final PagoServicio servicio;

    public PagoControlador(PagoServicio servicio) {
        this.servicio = servicio;
    }

    /** POST /api/reservas/{id}/pagar -> { urlPago }: el cliente va a esa dirección a pagar. */
    @PostMapping("/api/reservas/{id}/pagar")
    public ResponseEntity<Object> pagar(HttpServletRequest peticion) {
        Solicitud s = Solicitud.de(peticion);
        return Respuesta.ok(Json.obj("datos", servicio.iniciarPago(s.usuario().id(), s.id("id"))));
    }

    /** GET /api/reservas/{id}/pago -> estado de la reserva y de su último pago. */
    @GetMapping("/api/reservas/{id}/pago")
    public ResponseEntity<Object> estado(HttpServletRequest peticion) {
        Solicitud s = Solicitud.de(peticion);
        return Respuesta.ok(Json.obj("datos", servicio.estado(s.usuario().id(), s.id("id"))));
    }

    /**
     * POST /api/reservas/{id}/pago/verificar  { "paymentId": "123" }
     * El cliente vuelve de Mercado Pago: se consulta el pago de inmediato (sin esperar el webhook).
     */
    @PostMapping("/api/reservas/{id}/pago/verificar")
    public ResponseEntity<Object> verificar(HttpServletRequest peticion) {
        Solicitud s = Solicitud.de(peticion);
        long usuarioId = s.usuario().id();
        long reservaId = s.id("id");
        servicio.estado(usuarioId, reservaId); // 404 si la reserva no es de este cliente
        Object idPago = s.json().get("paymentId");
        if (idPago != null) {
            String id = idPago.toString();
            if (!ID_PAGO.matcher(id).matches()) throw ErrorApp.solicitudInvalida("paymentId: no es válido");
            servicio.registrarPago(id, reservaId);
        }
        return Respuesta.ok(Json.obj("datos", servicio.estado(usuarioId, reservaId)));
    }

    /**
     * POST /api/pagos/webhook (público): aviso de Mercado Pago. Se ignora lo que dice el aviso y se consulta
     * el pago a Mercado Pago. Siempre responde 200 salvo que Mercado Pago no responda (así reintenta).
     */
    @PostMapping("/api/pagos/webhook")
    public ResponseEntity<Object> webhook(HttpServletRequest peticion) {
        Solicitud s = Solicitud.de(peticion);
        Map<String, String> query = s.query();
        String tipo = query.getOrDefault("type", query.get("topic"));
        String id = query.getOrDefault("data.id", query.get("id"));
        if (id == null) {
            try {
                Map<String, Object> cuerpo = s.json();
                if (tipo == null && cuerpo.get("type") != null) tipo = cuerpo.get("type").toString();
                if (cuerpo.get("data") instanceof Map<?, ?> data && data.get("id") != null) id = data.get("id").toString();
            } catch (ErrorApp ignorado) {
                // cuerpo inválido: no hay nada que procesar
            }
        }
        if ("payment".equals(tipo) && id != null && ID_PAGO.matcher(id).matches()) {
            servicio.registrarPago(id, null);
        }
        return Respuesta.ok(Json.obj("recibido", true));
    }
}