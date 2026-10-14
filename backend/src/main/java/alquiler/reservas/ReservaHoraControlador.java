package alquiler.reservas;

import alquiler.http.Respuesta;
import alquiler.http.Solicitud;
import alquiler.json.Json;
import alquiler.modelo.ReservaHora;
import alquiler.util.ErrorApp;
import alquiler.util.Validador;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/** Rutas de reserva de máquinas por horas (HU-06). */
@RestController
public class ReservaHoraControlador {

    private final ReservaHoraServicio servicio;

    public ReservaHoraControlador(ReservaHoraServicio servicio) {
        this.servicio = servicio;
    }

    /**
     * POST /api/reservas
     * Crea una nueva reserva por horas.
     * Body: {
     *   "maquinaId": long,
     *   "fecha": "AAAA-MM-DD",
     *   "horaInicio": int,
     *   "horas": int,
     *   "nombreSolicitante": string,
     *   "ruc": string,
     *   "telefono": string,
     *   "email": string,
     *   "ubicacionObra": string,
     *   "detalle": string (opcional),
     *   "aceptaPolitica": boolean
     * }
     */
    @PostMapping("/api/reservas")
    public ResponseEntity<Object> crearReserva(HttpServletRequest peticion) {
        Solicitud s = Solicitud.de(peticion);
        Validador v = Validador.de(s.json());

        Long maquinaId = v.idPositivo("maquinaId", true, "Selecciona una máquina");
        String fecha = v.fecha("fecha", "Elige una fecha");
        int horaInicio = v.entero("horaInicio", -1, 0, 23);
        int horas = v.entero("horas", -1, 1, 12);
        String nombreSolicitante = v.linea("nombreSolicitante", "Nombre completo", 1, 160, true, null);
        String ruc = v.texto("ruc", 11, 11, true, "RUC requerido");
        String telefono = v.linea("telefono", "Teléfono", 1, 20, true, null);
        String email = v.correo("email");
        String ubicacionObra = v.linea("ubicacionObra", "Ubicación de la obra", 1, 200, true, null);
        String detalle = v.linea("detalle", "Detalle", 0, 300, false, null);
        Boolean aceptaPolitica = v.booleano("aceptaPolitica", false);

        v.validar();

        ReservaHora reserva = servicio.crearReserva(
                s.usuario().id(), maquinaId, fecha,
                horaInicio, horas,
                nombreSolicitante, ruc, telefono, email, ubicacionObra, detalle,
                aceptaPolitica != null && aceptaPolitica);

        return Respuesta.creado(Json.obj("datos", reserva.json(), "codigo", reserva.codigo()));
    }

    /**
     * GET /api/maquinas/{id}/disponibilidad/horas?fecha=AAAA-MM-DD
     * Obtiene la disponibilidad de horas para un día específico.
     */
    @GetMapping("/api/maquinas/{id}/disponibilidad/horas")
    public ResponseEntity<Object> obtenerDisponibilidadPorHoras(HttpServletRequest peticion) {
        Solicitud s = Solicitud.de(peticion);
        long id = s.id("id");
        String fecha = s.query().get("fecha");

        if (fecha == null || fecha.isEmpty()) {
            throw ErrorApp.solicitudInvalida("Parámetro 'fecha' requerido");
        }

        Map<String, Object> disponibilidad = servicio.obtenerDisponibilidadPorHoras(id, fecha);
        return Respuesta.ok(Json.obj("datos", disponibilidad));
    }

    /**
     * GET /api/admin/reservas
     * Lista todas las reservas por horas (solo admin).
     */
    @GetMapping("/api/admin/reservas")
    public ResponseEntity<Object> listarTodas(HttpServletRequest peticion) {
        Solicitud s = Solicitud.de(peticion);
        return Respuesta.ok(Json.obj("datos", servicio.listarTodasParaAdmin()));
    }

    /**
     * PATCH /api/admin/reservas/{id}/cancelar
     * Cancela una reserva (solo admin).
     */
    @PatchMapping("/api/admin/reservas/{id}/cancelar")
    public ResponseEntity<Object> cancelar(HttpServletRequest peticion) {
        Solicitud s = Solicitud.de(peticion);
        long id = s.id("id");
        servicio.cancelar(id);
        return Respuesta.sinContenido();
    }
}
