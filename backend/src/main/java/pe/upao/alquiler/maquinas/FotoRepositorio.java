package pe.upao.alquiler.maquinas;

import pe.upao.alquiler.bd.Consultas;
import pe.upao.alquiler.bd.Fila;
import pe.upao.alquiler.modelo.Foto;

import java.util.List;

/**
 * Acceso a la tabla "fotos_maquina" (HU-08). Los métodos reciben las
 * {@link Consultas} para poder usarse dentro de una transacción.
 */
public class FotoRepositorio {

    public List<Foto> listar(Consultas bd, long maquinaId) {
        return bd.consultar("""
                SELECT * FROM fotos_maquina WHERE maquina_id = ?
                 ORDER BY es_principal DESC, orden, id""", maquinaId).stream().map(Foto::desde).toList();
    }

    /** Cuántas fotos tiene, si alguna es principal y el mayor "orden". */
    public Fila resumen(Consultas bd, long maquinaId) {
        return bd.uno("""
                SELECT count(*) AS total,
                       coalesce(bool_or(es_principal), false) AS tiene_principal,
                       coalesce(max(orden), 0) AS max_orden
                  FROM fotos_maquina WHERE maquina_id = ?""", maquinaId);
    }

    public void insertar(Consultas bd, long maquinaId, String ruta, String tipoMime, boolean esPrincipal, long orden) {
        bd.ejecutar("""
                INSERT INTO fotos_maquina (maquina_id, ruta, tipo_mime, es_principal, orden)
                VALUES (?, ?, ?, ?, ?)""", maquinaId, ruta, tipoMime, esPrincipal, (int) orden);
    }

    public boolean pertenece(Consultas bd, long fotoId, long maquinaId) {
        return bd.uno("SELECT 1 FROM fotos_maquina WHERE id = ? AND maquina_id = ?", fotoId, maquinaId) != null;
    }

    /** Deja una sola foto principal: primero quita la actual y luego marca la nueva. */
    public void marcarPrincipal(Consultas bd, long maquinaId, long fotoId) {
        bd.ejecutar("UPDATE fotos_maquina SET es_principal = false WHERE maquina_id = ? AND es_principal", maquinaId);
        bd.ejecutar("UPDATE fotos_maquina SET es_principal = true WHERE id = ?", fotoId);
    }

    public void eliminar(Consultas bd, long fotoId) {
        bd.ejecutar("DELETE FROM fotos_maquina WHERE id = ?", fotoId);
    }
}
