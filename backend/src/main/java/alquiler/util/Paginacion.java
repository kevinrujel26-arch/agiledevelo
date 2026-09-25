package alquiler.util;

import alquiler.json.Json;

import java.util.List;
import java.util.Map;

/** Parámetros ?pagina=&tamanio= y formato de respuesta paginada. */
public record Paginacion(int pagina, int tamanio) {

    public static final int TAMANIO_POR_DEFECTO = 12;
    public static final int TAMANIO_MAXIMO = 50;

    /** Lee pagina (≥1, por defecto 1) y tamanio (1..50, por defecto 12). */
    public static Paginacion desde(Validador v) {
        int pagina = v.entero("pagina", 1, 1, Integer.MAX_VALUE);
        int tamanio = v.entero("tamanio", TAMANIO_POR_DEFECTO, 1, TAMANIO_MAXIMO);
        return new Paginacion(pagina, tamanio);
    }

    public int limite() {
        return tamanio;
    }

    public long desplazamiento() {
        return (long) (pagina - 1) * tamanio;
    }

    public Map<String, Object> respuesta(List<?> datos, long total) {
        long totalPaginas = Math.max(1, (total + tamanio - 1) / tamanio);
        return Json.obj(
                "datos", datos,
                "paginacion", Json.obj(
                        "pagina", pagina,
                        "tamanio", tamanio,
                        "total", total,
                        "totalPaginas", totalPaginas));
    }
}
