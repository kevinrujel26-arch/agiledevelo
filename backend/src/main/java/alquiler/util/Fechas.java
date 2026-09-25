package alquiler.util;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Pattern;

/** Utilidades de fechas 'AAAA-MM-DD'. Los rangos son inclusivos: [inicio, fin]. */
public final class Fechas {

    private static final Pattern FORMATO = Pattern.compile("^\\d{4}-\\d{2}-\\d{2}$");

    private Fechas() {
    }

    /** Rango de fechas inclusivo. */
    public record Rango(String fechaInicio, String fechaFin) {
    }

    /** Fecha de hoy en la zona horaria del negocio (por defecto Lima). */
    public static String hoy(String zonaHoraria) {
        return LocalDate.now(ZoneId.of(zonaHoraria)).toString();
    }

    /** true si el texto es una fecha real con formato AAAA-MM-DD. */
    public static boolean esFechaValida(String texto) {
        if (texto == null || !FORMATO.matcher(texto).matches()) return false;
        try {
            LocalDate.parse(texto); // ISO estricto: rechaza 2026-02-30
            return true;
        } catch (DateTimeParseException e) {
            return false;
        }
    }

    /** Dos rangos inclusivos que comparten al menos un día. */
    public static boolean seSuperponen(Rango a, Rango b) {
        return a.fechaInicio().compareTo(b.fechaFin()) <= 0 && b.fechaInicio().compareTo(a.fechaFin()) <= 0;
    }

    /** Devuelve el primer par de rangos que se superponen, o null. */
    public static Rango[] buscarSuperposicion(List<Rango> rangos) {
        List<Rango> ordenados = new ArrayList<>(rangos);
        ordenados.sort(Comparator.comparing(Rango::fechaInicio));
        for (int i = 1; i < ordenados.size(); i++) {
            if (seSuperponen(ordenados.get(i - 1), ordenados.get(i))) {
                return new Rango[]{ordenados.get(i - 1), ordenados.get(i)};
            }
        }
        return null;
    }

    /** Días de un rango inclusivo: del 01 al 01 = 1 día. */
    public static long diasEntre(String inicio, String fin) {
        return ChronoUnit.DAYS.between(LocalDate.parse(inicio), LocalDate.parse(fin)) + 1;
    }

    public static String sumarDias(String fecha, long dias) {
        return LocalDate.parse(fecha).plusDays(dias).toString();
    }
}
