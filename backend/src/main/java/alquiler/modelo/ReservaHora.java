package alquiler.modelo;

import alquiler.bd.Fila;
import alquiler.json.Json;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;

/** HU-06: Reserva de máquina por horas (usa tabla reservas de 001 + campos de 008). */
public record ReservaHora(
        long id,
        String codigo,
        long clienteId,
        long maquinaId,
        Instant fechaInicio,
        Instant fechaFin,
        Instant finBloqueo,
        int horas,
        BigDecimal tarifaHoraria,
        BigDecimal montoTotal,
        BigDecimal combustibleTotal,
        EstadoReserva estado,
        String nombreSolicitante,
        String ruc,
        String telefonoContacto,
        String emailContacto,
        String ubicacionObra,
        String detalle,
        boolean aceptaPolitica,
        Instant creadoEn,
        Instant canceladaEn) {

    public static ReservaHora desde(Fila f) {
        return new ReservaHora(
                f.entero("id"),
                f.texto("codigo"),
                f.entero("cliente_id"),
                f.entero("maquina_id"),
                f.instante("fecha_inicio"),
                f.instante("fecha_fin"),
                f.instante("fin_bloqueo"),
                f.entero("horas").intValue(),
                f.decimal("tarifa_horaria"),
                f.decimal("monto_total"),
                f.decimal("combustible_total"),
                EstadoReserva.valueOf(f.texto("estado")),
                f.texto("nombre_solicitante"),
                f.texto("ruc"),
                f.texto("telefono_contacto"),
                f.texto("email_contacto"),
                f.texto("ubicacion_obra"),
                f.texto("detalle"),
                f.bool("acepta_politica"),
                f.instante("creado_en"),
                f.instante("cancelada_en"));
    }

    public Map<String, Object> json() {
        return Json.obj(
                "id", id,
                "codigo", codigo,
                "maquinaId", maquinaId,
                "inicio", fechaInicio,
                "fin", fechaFin,
                "horas", horas,
                "tarifaHora", tarifaHoraria,
                "total", montoTotal,
                "combustibleTotal", combustibleTotal,
                "estado", estado.name(),
                "nombreSolicitante", nombreSolicitante,
                "ruc", ruc,
                "telefonoContacto", telefonoContacto,
                "emailContacto", emailContacto,
                "ubicacionObra", ubicacionObra,
                "detalle", detalle,
                "aceptaPolitica", aceptaPolitica,
                "creadaEn", creadoEn,
                "canceladaEn", canceladaEn);
    }
}
