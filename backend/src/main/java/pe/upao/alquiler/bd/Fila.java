package pe.upao.alquiler.bd;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.LinkedHashMap;

/** Una fila de resultado: columna -> valor, con métodos para leer cada tipo. */
public class Fila extends LinkedHashMap<String, Object> {

    public String texto(String columna) {
        Object v = get(columna);
        return v == null ? null : v.toString();
    }

    public Long entero(String columna) {
        Object v = get(columna);
        if (v == null) return null;
        if (v instanceof Number n) return n.longValue();
        return Long.parseLong(v.toString());
    }

    public long enteroOCero(String columna) {
        Long v = entero(columna);
        return v == null ? 0 : v;
    }

    public BigDecimal decimal(String columna) {
        Object v = get(columna);
        if (v == null) return null;
        if (v instanceof BigDecimal d) return d;
        return new BigDecimal(v.toString());
    }

    public boolean bool(String columna) {
        Object v = get(columna);
        return v instanceof Boolean b && b;
    }

    /** Columnas DATE: se devuelven como texto 'AAAA-MM-DD' (sin problemas de zona horaria). */
    public String fecha(String columna) {
        return texto(columna);
    }

    public Instant instante(String columna) {
        Object v = get(columna);
        return v instanceof Instant i ? i : null;
    }

    /** Columnas JSON/JSONB ya convertidas a Map/List. */
    public Object json(String columna) {
        return get(columna);
    }
}
