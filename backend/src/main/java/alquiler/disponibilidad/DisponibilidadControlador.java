package alquiler.disponibilidad;

import alquiler.config.Config;
import alquiler.http.Respuesta;
import alquiler.http.Solicitud;
import alquiler.json.Json;
import alquiler.util.ErrorApp;
import alquiler.util.Fechas;
import alquiler.util.Validador;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;


/** Rutas de disponibilidad (HU-09). */
@RestController
public class DisponibilidadControlador {

    private static final int DIAS_POR_DEFECTO = 180;
    private static final int DIAS_MAXIMOS = 400;

    private final DisponibilidadServicio servicio;
    private final Config config;

    public DisponibilidadControlador(DisponibilidadServicio servicio, Config config) {
        this.servicio = servicio;
        this.config = config;
    }

    @GetMapping("/api/admin/maquinas/{id}/bloqueos")
    public ResponseEntity<Object> listarBloqueos(HttpServletRequest peticion) {
        Solicitud s = Solicitud.de(peticion);
        boolean pasados = "true".equals(s.query().get("incluirPasados"));
        return Respuesta.ok(Json.obj("datos", servicio.listar(s.id("id"), pasados)));
    }

    @DeleteMapping("/api/admin/maquinas/{id}/bloqueos/{bloqueoId}")
    public ResponseEntity<Object> desbloquear(HttpServletRequest peticion) {
        Solicitud s = Solicitud.de(peticion);
        servicio.desbloquear(s.id("id"), s.id("bloqueoId"));
        return Respuesta.sinContenido();
    }

    /** ?desde=&hasta= (por defecto: hoy + 180 días) */
    @GetMapping("/api/maquinas/{id}/disponibilidad")
    public ResponseEntity<Object> ocupacion(HttpServletRequest peticion) {
        Solicitud s = Solicitud.de(peticion);
        long id = s.id("id");
        Validador v = Validador.de(s.query());
        String desde = v.fecha("desde", false);
        String hasta = v.fecha("hasta", false);
        v.validar();
        if (desde == null) desde = Fechas.hoy(config.zonaHoraria);
        if (hasta == null) hasta = Fechas.sumarDias(desde, DIAS_POR_DEFECTO);
        if (hasta.compareTo(desde) < 0) throw ErrorApp.solicitudInvalida("La fecha \"hasta\" debe ser posterior a \"desde\"");
        if (Fechas.diasEntre(desde, hasta) > DIAS_MAXIMOS) {
            throw ErrorApp.solicitudInvalida("Consulta como máximo " + DIAS_MAXIMOS + " días a la vez");
        }
        return Respuesta.ok(servicio.ocupacionPublica(id, desde, hasta));
    }

    /** { "rangos": [{ "fechaInicio": "2026-10-01", "fechaFin": "2026-10-03" }], "motivo": "..." } */
    @PostMapping("/api/admin/maquinas/{id}/bloqueos")
    public ResponseEntity<Object> bloquear(HttpServletRequest peticion) {
        Solicitud s = Solicitud.de(peticion);
        long id = s.id("id");
        Validador v = Validador.de(s.json());
        List<Map<String, Object>> lista = v.listaDeObjetos("rangos", 1, 20, "Agrega al menos un rango de fechas");
        String motivo = v.texto("motivo", 0, 160, false, null);
        if (motivo != null && motivo.isEmpty()) motivo = null;

        List<Fechas.Rango> rangos = new ArrayList<>();
        for (int i = 0; i < lista.size(); i++) {
            Validador vr = v.anidado(lista.get(i), "rangos." + i);
            String inicio = vr.fecha("fechaInicio", true);
            String fin = vr.fecha("fechaFin", true);
            if (inicio != null && fin != null) {
                if (fin.compareTo(inicio) < 0) {
                    vr.error("fechaFin", "La fecha de fin no puede ser anterior a la de inicio");
                } else {
                    rangos.add(new Fechas.Rango(inicio, fin));
                }
            }
        }
        v.validar();
        return Respuesta.creado(Json.obj("datos", servicio.bloquear(id, rangos, motivo, s.usuario().id())));
    }
}
