package pe.upao.alquiler.bd;

import java.util.List;

/**
 * Operaciones básicas sobre la base de datos. La implementan {@link Bd}
 * (cada llamada usa una conexión del pool) y {@link Bd.Transaccion}
 * (todas las llamadas usan la misma conexión dentro de una transacción).
 *
 * Los parámetros se escriben con "?" en el SQL, en el mismo orden.
 */
public interface Consultas {

    List<Fila> consultar(String sql, Object... parametros);

    /** Primera fila o null si no hay resultados. */
    default Fila uno(String sql, Object... parametros) {
        List<Fila> filas = consultar(sql, parametros);
        return filas.isEmpty() ? null : filas.get(0);
    }

    /** Para INSERT/UPDATE/DELETE sin RETURNING: devuelve cuántas filas cambió. */
    int ejecutar(String sql, Object... parametros);
}
