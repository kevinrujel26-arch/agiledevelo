package alquiler.disponibilidad;

import alquiler.bd.Consultas;
import alquiler.bd.Fila;
import alquiler.modelo.Bloqueo;

import java.util.List;

/**
 * Acceso a "bloqueos_disponibilidad" y consultas de ocupación (HU-09).
 *
 * Por dentro, "fecha_inicio"/"fecha_fin" ahora son TIMESTAMPTZ (para que la
 * base de datos quede lista para reservas por hora en el Sprint 3), con
 * fecha_fin EXCLUSIVA (medianoche del día siguiente al último día bloqueado).
 * Pero de cara al resto de la app (admin, catálogo) un bloqueo sigue siendo
 * "del día X al día Y" tal como antes: por eso cada consulta convierte con
 * ::date / +interval '1 day' al entrar y al salir, y nada fuera de esta
 * clase se entera del cambio.
 */
public class BloqueoRepositorio {

    public List<Bloqueo> listar(Consultas bd, long maquinaId, boolean incluirPasados, String hoy) {
        return bd.consultar("""
                SELECT b.id, b.maquina_id, b.fecha_inicio::date AS fecha_inicio,
                       (b.fecha_fin - interval '1 day')::date AS fecha_fin, b.motivo, b.creado_en,
                       u.nombre AS creado_por_nombre
                  FROM bloqueos_disponibilidad b
                  LEFT JOIN usuarios u ON u.id = b.creado_por
                 WHERE b.maquina_id = ? AND (?::boolean OR b.fecha_fin > ?::date)
                 ORDER BY b.fecha_inicio""", maquinaId, incluirPasados, hoy).stream().map(Bloqueo::desde).toList();
    }

    /** Primera reserva PAGADA que se cruza con el rango (días inclusivos [inicio, fin]), o null. */
    public Fila reservaPagadaQueSeCruza(Consultas bd, long maquinaId, String inicio, String fin) {
        return bd.uno("""
                SELECT fecha_inicio::date AS fecha_inicio, (fecha_fin - interval '1 day')::date AS fecha_fin
                  FROM reservas
                 WHERE maquina_id = ? AND estado = 'PAGADA'
                   AND tstzrange(fecha_inicio, fecha_fin, '[)') && tstzrange(?::date, (?::date + interval '1 day'), '[)')
                 ORDER BY fecha_inicio LIMIT 1""", maquinaId, inicio, fin);
    }

    public Bloqueo insertar(Consultas bd, long maquinaId, String inicio, String fin, String motivo, long creadoPor) {
        Fila f = bd.uno("""
                INSERT INTO bloqueos_disponibilidad (maquina_id, fecha_inicio, fecha_fin, motivo, creado_por)
                VALUES (?, ?::date, (?::date + interval '1 day'), ?, ?)
                RETURNING id, maquina_id, fecha_inicio::date AS fecha_inicio,
                          (fecha_fin - interval '1 day')::date AS fecha_fin, motivo, creado_en""",
                maquinaId, inicio, fin, motivo, creadoPor);
        return Bloqueo.desde(f);
    }

    public boolean eliminar(Consultas bd, long maquinaId, long bloqueoId) {
        return bd.ejecutar("DELETE FROM bloqueos_disponibilidad WHERE id = ? AND maquina_id = ?", bloqueoId, maquinaId) > 0;
    }

    /** Bloqueos y reservas vigentes (pendientes o pagadas) que se cruzan con el periodo (días inclusivos). */
    public List<Fila> ocupacion(Consultas bd, long maquinaId, String desde, String hasta) {
        return bd.consultar("""
                SELECT fecha_inicio::date AS fecha_inicio, (fecha_fin - interval '1 day')::date AS fecha_fin, 'BLOQUEO' AS tipo
                  FROM bloqueos_disponibilidad
                 WHERE maquina_id = ?
                   AND tstzrange(fecha_inicio, fecha_fin, '[)') && tstzrange(?::date, (?::date + interval '1 day'), '[)')
                UNION ALL
                SELECT fecha_inicio::date AS fecha_inicio, (fecha_fin - interval '1 day')::date AS fecha_fin, 'RESERVA' AS tipo
                  FROM reservas
                 WHERE maquina_id = ? AND estado IN ('PENDIENTE_PAGO', 'PAGADA')
                   AND tstzrange(fecha_inicio, fecha_fin, '[)') && tstzrange(?::date, (?::date + interval '1 day'), '[)')
                 ORDER BY fecha_inicio""", maquinaId, desde, hasta, maquinaId, desde, hasta);
    }
}
