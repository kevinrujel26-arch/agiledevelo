package alquiler.reservas;

import alquiler.bd.Bd;
import alquiler.bd.Fila;
import alquiler.modelo.EstadoReserva;
import alquiler.modelo.ReservaHora;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

@Repository
public class ReservaHoraRepositorio {

    private final Bd bd;

    public ReservaHoraRepositorio(Bd bd) {
        this.bd = bd;
    }

    /**
     * Crea una nueva reserva por horas en la tabla reservas.
     */
    public ReservaHora insertar(String codigo, long clienteId, long maquinaId,
                                 Instant fechaInicio, Instant fechaFin, Instant finBloqueo,
                                 int horas, java.math.BigDecimal tarifaHoraria, java.math.BigDecimal montoTotal,
                                 java.math.BigDecimal combustibleTotal,
                                 String nombreSolicitante, String ruc, String telefonoContacto,
                                 String emailContacto, String ubicacionObra, String detalle,
                                 boolean aceptaPolitica, Instant expiraPagoEn) {
        Fila f = bd.uno("""
                INSERT INTO reservas (codigo, cliente_id, maquina_id, fecha_inicio, fecha_fin, fin_bloqueo,
                                      tarifa_horaria, monto_total, combustible_total,
                                      nombre_solicitante, ruc, telefono_contacto, email_contacto,
                                      ubicacion_obra, detalle, acepta_politica, expira_pago_en, estado)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'PENDIENTE_PAGO')
                RETURNING *""",
                codigo, clienteId, maquinaId, fechaInicio, fechaFin, finBloqueo,
                tarifaHoraria, montoTotal, combustibleTotal,
                nombreSolicitante, ruc, telefonoContacto, emailContacto,
                ubicacionObra, detalle, aceptaPolitica, expiraPagoEn);
        return ReservaHora.desde(f);
    }

    /**
     * Busca una reserva por ID.
     */
    public ReservaHora buscarPorId(long id) {
        Fila f = bd.uno("""
                SELECT * FROM reservas WHERE id = ?""", id);
        return f == null ? null : ReservaHora.desde(f);
    }

    /**
     * Busca una reserva por código.
     */
    public ReservaHora buscarPorCodigo(String codigo) {
        Fila f = bd.uno("""
                SELECT * FROM reservas WHERE codigo = ?""", codigo);
        return f == null ? null : ReservaHora.desde(f);
    }

    /**
     * Cuenta cuántas reservas PENDIENTE_PAGO tiene un usuario.
     */
    public int contarPendientesPorUsuario(long usuarioId) {
        Fila f = bd.uno("""
                SELECT COUNT(*) as cantidad FROM reservas
                WHERE cliente_id = ? AND estado = ?""",
                usuarioId, EstadoReserva.PENDIENTE_PAGO.name());
        return (int)(long) f.entero("cantidad");
    }

    /**
     * Lista todas las reservas de un usuario.
     */
    public List<ReservaHora> listarPorUsuario(long usuarioId) {
        return bd.consultar("""
                SELECT * FROM reservas
                WHERE cliente_id = ?
                ORDER BY creado_en DESC""",
                usuarioId).stream()
                .map(ReservaHora::desde)
                .toList();
    }

    /**
     * Lista todas las reservas para el panel de admin (incluye datos de máquina y cliente).
     */
    public List<Fila> listarTodasConDetalles() {
        return bd.consultar("""
                SELECT r.*, m.nombre AS maquina_nombre, u.nombre AS cliente_nombre, u.correo AS cliente_correo
                FROM reservas r
                JOIN maquinas m ON r.maquina_id = m.id
                JOIN usuarios u ON r.cliente_id = u.id
                ORDER BY r.creado_en DESC, r.id DESC""");
    }

    /**
     * Cancela una reserva.
     */
    public void cancelar(long id) {
        bd.ejecutar("""
                UPDATE reservas SET estado = ?, cancelada_en = now()
                WHERE id = ?""",
                EstadoReserva.CANCELADA.name(), id);
    }

    /**
     * Cancela las reservas pendientes de pago cuyo plazo de 2 horas ya venció (libera el horario).
     */
    public int cancelarVencidas() {
        return bd.ejecutar("""
                UPDATE reservas
                   SET estado = 'CANCELADA', cancelada_en = now(),
                       motivo_cancelacion = 'No se pagó dentro de las 2 horas'
                 WHERE estado = 'PENDIENTE_PAGO'
                   AND expira_pago_en IS NOT NULL AND expira_pago_en <= now()""");
    }
}
