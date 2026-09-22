package pe.upao.alquiler.disponibilidad;

import pe.upao.alquiler.bd.Consultas;
import pe.upao.alquiler.bd.Fila;
import pe.upao.alquiler.modelo.Bloqueo;

import java.util.List;

/** Acceso a "bloqueos_disponibilidad" y consultas de ocupación (HU-09). */
public class BloqueoRepositorio {

    public List<Bloqueo> listar(Consultas bd, long maquinaId, boolean incluirPasados, String hoy) {
        return bd.consultar("""
                SELECT b.*, u.nombre AS creado_por_nombre
                  FROM bloqueos_disponibilidad b
                  LEFT JOIN usuarios u ON u.id = b.creado_por
                 WHERE b.maquina_id = ? AND (?::boolean OR b.fecha_fin >= ?::date)
                 ORDER BY b.fecha_inicio""", maquinaId, incluirPasados, hoy).stream().map(Bloqueo::desde).toList();
    }

    /** Primera reserva PAGADA que se cruza con el rango, o null. */
    public Fila reservaPagadaQueSeCruza(Consultas bd, long maquinaId, String inicio, String fin) {
        return bd.uno("""
                SELECT fecha_inicio, fecha_fin FROM reservas
                 WHERE maquina_id = ? AND estado = 'PAGADA'
                   AND daterange(fecha_inicio, fecha_fin, '[]') && daterange(?::date, ?::date, '[]')
                 ORDER BY fecha_inicio LIMIT 1""", maquinaId, inicio, fin);
    }

    public Bloqueo insertar(Consultas bd, long maquinaId, String inicio, String fin, String motivo, long creadoPor) {
        Fila f = bd.uno("""
                INSERT INTO bloqueos_disponibilidad (maquina_id, fecha_inicio, fecha_fin, motivo, creado_por)
                VALUES (?, ?::date, ?::date, ?, ?)
                RETURNING *""", maquinaId, inicio, fin, motivo, creadoPor);
        return Bloqueo.desde(f);
    }

    public boolean eliminar(Consultas bd, long maquinaId, long bloqueoId) {
        return bd.ejecutar("DELETE FROM bloqueos_disponibilidad WHERE id = ? AND maquina_id = ?", bloqueoId, maquinaId) > 0;
    }

    /** Bloqueos y reservas vigentes (pendientes o pagadas) que se cruzan con el periodo. */
    public List<Fila> ocupacion(Consultas bd, long maquinaId, String desde, String hasta) {
        return bd.consultar("""
                SELECT fecha_inicio, fecha_fin, 'BLOQUEO' AS tipo
                  FROM bloqueos_disponibilidad
                 WHERE maquina_id = ?
                   AND daterange(fecha_inicio, fecha_fin, '[]') && daterange(?::date, ?::date, '[]')
                UNION ALL
                SELECT fecha_inicio, fecha_fin, 'RESERVA' AS tipo
                  FROM reservas
                 WHERE maquina_id = ? AND estado IN ('PENDIENTE_PAGO', 'PAGADA')
                   AND daterange(fecha_inicio, fecha_fin, '[]') && daterange(?::date, ?::date, '[]')
                 ORDER BY fecha_inicio""", maquinaId, desde, hasta, maquinaId, desde, hasta);
    }
}
