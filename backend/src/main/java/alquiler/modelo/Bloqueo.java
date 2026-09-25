package alquiler.modelo;

import alquiler.bd.Fila;
import alquiler.json.Json;

import java.time.Instant;
import java.util.Map;

/** HU-09: rango de fechas bloqueado (inclusivo) de una máquina. */
public record Bloqueo(long id, long maquinaId, String fechaInicio, String fechaFin, String motivo,
                      String creadoPor, Instant creadoEn) implements Json.Convertible {

    public static Bloqueo desde(Fila f) {
        return new Bloqueo(f.entero("id"), f.entero("maquina_id"), f.fecha("fecha_inicio"), f.fecha("fecha_fin"),
                f.texto("motivo"), f.texto("creado_por_nombre"), f.instante("creado_en"));
    }

    @Override
    public Map<String, Object> aJson() {
        return Json.obj("id", id, "fechaInicio", fechaInicio, "fechaFin", fechaFin, "motivo", motivo,
                "creadoPor", creadoPor, "creadoEn", creadoEn);
    }
}
