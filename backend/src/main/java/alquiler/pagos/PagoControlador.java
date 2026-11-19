package alquiler.pagos;

import alquiler.http.Respuesta;
import alquiler.http.Solicitud;
import alquiler.json.Json;
import alquiler.util.Validador;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/** HU-07: pago de reservas con Culqi. */
@RestController
public class PagoControlador {

    private final PagoServicio servicio;

    public PagoControlador(PagoServicio servicio) {
        this.servicio = servicio;
    }

    /** GET /api/pagos/config (público) -> { publicKey }: clave pública para el formulario de tarjeta. */
    @GetMapping("/api/pagos/config")
    public ResponseEntity<Object> configuracion() {
        return Respuesta.ok(Json.obj("datos", Json.obj("publicKey", servicio.clavePublica())));
    }

    /**
     * POST /api/reservas/{id}/pagar-con-tarjeta
     * { token, metodoPagoId, emisorId?, correo, tipoDocumento?, numeroDocumento? }
     * El formulario de Culqi convierte la tarjeta en un token: los datos de la tarjeta nunca llegan aquí.
     */
    @PostMapping("/api/reservas/{id}/pagar-con-tarjeta")
    public ResponseEntity<Object> pagarConTarjeta(HttpServletRequest peticion) {
        Solicitud s = Solicitud.de(peticion);
        Validador v = Validador.de(s.json());
        String token = v.texto("token", 8, 200, true, "Falta el token de la tarjeta");
        String metodo = v.texto("metodoPagoId", 2, 40, false, null);
        String emisor = v.texto("emisorId", 1, 10, false, null);
        String correo = v.correo("correo");
        String tipoDoc = v.texto("tipoDocumento", 2, 10, false, null);
        String numeroDoc = v.texto("numeroDocumento", 5, 20, false, null);
        String dispositivo = v.texto("dispositivoId", 1, 1000, false, null);
        if (emisor != null && !emisor.matches("\\d{1,10}")) v.error("emisorId", "No es válido");
        v.validar();
        @SuppressWarnings("unchecked")
        Map<String, Object> autenticacion = s.json().get("parametros3ds") instanceof Map<?, ?> m ? (Map<String, Object>) m : null;
        PasarelaPago.DatosTarjeta tarjeta = new PasarelaPago.DatosTarjeta(token, metodo, emisor, correo, tipoDoc, numeroDoc, dispositivo, null, null, null, autenticacion);
        return Respuesta.ok(Json.obj("datos", servicio.pagarConTarjeta(s.usuario().id(), s.id("id"), tarjeta)));
    }

    /** GET /api/reservas/{id}/pago -> estado de la reserva y de su último pago. */
    @GetMapping("/api/reservas/{id}/pago")
    public ResponseEntity<Object> estado(HttpServletRequest peticion) {
        Solicitud s = Solicitud.de(peticion);
        return Respuesta.ok(Json.obj("datos", servicio.estado(s.usuario().id(), s.id("id"))));
    }
}
