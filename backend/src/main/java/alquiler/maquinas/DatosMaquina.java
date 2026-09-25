package alquiler.maquinas;

import java.math.BigDecimal;
import java.util.Map;

/** Datos que envía el administrador al registrar una máquina (HU-08 criterio 1). */
public record DatosMaquina(
        Long categoriaId,
        String nombre,
        String marca,
        String modelo,
        String descripcion,
        Map<String, String> especificaciones,
        BigDecimal tarifaDiaria,
        String ubicacion,
        Boolean enMantenimiento) {
}
