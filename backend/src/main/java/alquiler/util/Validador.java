package alquiler.util;

import alquiler.json.Json;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Valida los datos que llegan en una petición y acumula los errores por campo.
 *
 * <pre>
 *   Validador v = Validador.de(cuerpo);
 *   String nombre = v.texto("nombre", 1, 120, true, "El nombre es obligatorio");
 *   String correo = v.correo("correo");
 *   v.validar();   // lanza 400 con {"error": "...", "detalles": [{campo, mensaje}]}
 * </pre>
 */
public final class Validador {

    private static final Pattern CORREO =
            Pattern.compile("^[A-Za-z0-9._%+'-]+@[A-Za-z0-9-]+(\\.[A-Za-z0-9-]+)*\\.[A-Za-z]{2,}$");
    private static final Pattern ENTERO = Pattern.compile("^-?\\d{1,18}$");
    private static final String OBLIGATORIO = "Este campo es obligatorio";
    private static final String TIPO_INVALIDO = "Tipo de dato inválido";

    private final Map<String, ?> datos;
    private final String prefijo;
    private final List<Map<String, Object>> errores;

    private Validador(Map<String, ?> datos, String prefijo, List<Map<String, Object>> errores) {
        this.datos = datos == null ? Map.of() : datos;
        this.prefijo = prefijo;
        this.errores = errores;
    }

    public static Validador de(Map<String, ?> datos) {
        return new Validador(datos, "", new ArrayList<>());
    }

    /** Validador para un objeto anidado; sus errores se agregan a este con el prefijo dado. */
    public Validador anidado(Map<String, ?> subdatos, String prefijoCampo) {
        return new Validador(subdatos, prefijo + prefijoCampo + ".", errores);
    }

    // ------------------------------------------------------------------
    public boolean tiene(String campo) {
        return datos.containsKey(campo);
    }

    public void error(String campo, String mensaje) {
        errores.add(Json.obj("campo", prefijo + campo, "mensaje", mensaje));
    }

    public boolean hayErrores() {
        return !errores.isEmpty();
    }

    /** Lanza ErrorApp 400 si hubo errores. El mensaje principal es "campo: mensaje" del primero. */
    public void validar() {
        if (errores.isEmpty()) return;
        Map<String, Object> primero = errores.get(0);
        String campo = (String) primero.get("campo");
        String mensaje = campo.isEmpty() ? (String) primero.get("mensaje") : campo + ": " + primero.get("mensaje");
        throw new ErrorApp(400, mensaje, List.copyOf(errores));
    }

    // ------------------------------------------------------------------
    // Textos
    // ------------------------------------------------------------------
    /** Texto recortado (trim). Si no es obligatorio y no viene, devuelve null. */
    public String texto(String campo, int min, int max, boolean obligatorio, String mensajeObligatorio) {
        Object v = datos.get(campo);
        String msgObl = mensajeObligatorio != null ? mensajeObligatorio : OBLIGATORIO;
        if (v == null) {
            if (obligatorio) error(campo, msgObl);
            return null;
        }
        if (!(v instanceof String s)) {
            error(campo, TIPO_INVALIDO);
            return null;
        }
        String t = s.trim();
        if (obligatorio && t.isEmpty()) {
            error(campo, msgObl);
            return null;
        }
        if (t.length() < min) {
            error(campo, "Debe tener al menos " + min + " caracteres");
            return null;
        }
        if (t.length() > max) {
            error(campo, "Debe tener como máximo " + max + " caracteres");
            return null;
        }
        return t;
    }

    /** Texto sin recortar (contraseñas). */
    public String textoExacto(String campo, int min, int max, String mensajeMin, String mensajeMax) {
        Object v = datos.get(campo);
        if (v == null) {
            error(campo, OBLIGATORIO);
            return null;
        }
        if (!(v instanceof String s)) {
            error(campo, TIPO_INVALIDO);
            return null;
        }
        if (s.length() < min) {
            error(campo, mensajeMin);
            return null;
        }
        if (s.length() > max) {
            error(campo, mensajeMax);
            return null;
        }
        return s;
    }

    /** Correo obligatorio, en minúsculas y con formato válido. */
    public String correo(String campo) {
        String c = texto(campo, 1, 160, true, OBLIGATORIO);
        if (c == null) return null;
        c = c.toLowerCase();
        if (!CORREO.matcher(c).matches()) {
            error(campo, "El correo no tiene un formato válido");
            return null;
        }
        return c;
    }

    /** Valor de una lista fija de opciones. */
    public String opcion(String campo, Collection<String> opciones, boolean obligatorio) {
        Object v = datos.get(campo);
        if (v == null || (v instanceof String s && s.isBlank())) {
            if (obligatorio) error(campo, OBLIGATORIO);
            return null;
        }
        String t = v.toString();
        if (!opciones.contains(t)) {
            error(campo, "Valor inválido. Opciones: " + String.join(", ", opciones));
            return null;
        }
        return t;
    }

    /** Fecha 'AAAA-MM-DD' real (no acepta 2026-02-30). */
    public String fecha(String campo, boolean obligatorio) {
        Object v = datos.get(campo);
        if (v == null || (v instanceof String s && s.isBlank())) {
            if (obligatorio) error(campo, OBLIGATORIO);
            return null;
        }
        if (!(v instanceof String s) || !Fechas.esFechaValida(s)) {
            error(campo, "Fecha inválida, usa el formato AAAA-MM-DD");
            return null;
        }
        return s;
    }

    // ------------------------------------------------------------------
    // Números y booleanos (aceptan número o texto numérico, como en un formulario)
    // ------------------------------------------------------------------
    public Long idPositivo(String campo, boolean obligatorio, String mensajeObligatorio) {
        Object v = datos.get(campo);
        if (v == null || (v instanceof String s && s.isBlank())) {
            if (obligatorio) error(campo, mensajeObligatorio != null ? mensajeObligatorio : OBLIGATORIO);
            return null;
        }
        Long n = aEntero(v);
        if (n == null) {
            error(campo, "Debe ser un número entero");
            return null;
        }
        if (n <= 0) {
            error(campo, "Debe ser mayor a 0");
            return null;
        }
        return n;
    }

    /** Entero con valor por defecto y rango (para ?pagina= y ?tamanio=). */
    public int entero(String campo, int porDefecto, int min, int max) {
        Object v = datos.get(campo);
        if (v == null || (v instanceof String s && s.isBlank())) return porDefecto;
        Long n = aEntero(v);
        if (n == null) {
            error(campo, "Debe ser un número entero");
            return porDefecto;
        }
        if (n < min) {
            error(campo, "Debe ser mayor o igual a " + min);
            return porDefecto;
        }
        if (n > max) {
            error(campo, "Debe ser menor o igual a " + max);
            return porDefecto;
        }
        return n.intValue();
    }

    /** Decimal mayor a 0, redondeado a 2 decimales (montos en soles). */
    public BigDecimal decimalPositivo(String campo, String etiqueta, boolean obligatorio, BigDecimal maximo) {
        Object v = datos.get(campo);
        if (v == null || (v instanceof String s && s.isBlank())) {
            if (obligatorio) error(campo, etiqueta + " debe ser un número");
            return null;
        }
        BigDecimal d;
        try {
            d = v instanceof BigDecimal b ? b : new BigDecimal(v.toString().trim());
        } catch (NumberFormatException e) {
            error(campo, etiqueta + " debe ser un número");
            return null;
        }
        if (v instanceof Boolean) {
            error(campo, etiqueta + " debe ser un número");
            return null;
        }
        if (d.signum() <= 0) {
            error(campo, etiqueta + " debe ser mayor a 0");
            return null;
        }
        if (maximo != null && d.compareTo(maximo) > 0) {
            error(campo, etiqueta + " es demasiado alta");
            return null;
        }
        return d.setScale(2, RoundingMode.HALF_UP);
    }

    /** Booleano (true/false en JSON). Si no viene, devuelve el valor por defecto. */
    public Boolean booleano(String campo, Boolean porDefecto) {
        Object v = datos.get(campo);
        if (v == null) return porDefecto;
        if (v instanceof Boolean b) return b;
        if ("true".equals(v)) return true;
        if ("false".equals(v)) return false;
        error(campo, TIPO_INVALIDO);
        return porDefecto;
    }

    // ------------------------------------------------------------------
    // Estructuras
    // ------------------------------------------------------------------
    /** Objeto { "clave": "valor" } de textos (especificaciones técnicas). */
    public Map<String, String> mapaDeTextos(String campo, int maxEntradas, int maxClave, int maxValor) {
        Object v = datos.get(campo);
        if (v == null) return null;
        if (!(v instanceof Map<?, ?> mapa)) {
            error(campo, TIPO_INVALIDO);
            return null;
        }
        if (mapa.size() > maxEntradas) {
            error(campo, "Máximo " + maxEntradas + " elementos");
            return null;
        }
        Map<String, String> resultado = new LinkedHashMap<>();
        for (Map.Entry<?, ?> e : mapa.entrySet()) {
            String clave = String.valueOf(e.getKey()).trim();
            if (!(e.getValue() instanceof String valorTexto)) {
                error(campo + "." + clave, TIPO_INVALIDO);
                continue;
            }
            String valor = valorTexto.trim();
            if (clave.isEmpty() || clave.length() > maxClave) {
                error(campo, "Cada nombre debe tener entre 1 y " + maxClave + " caracteres");
                continue;
            }
            if (valor.length() > maxValor) {
                error(campo + "." + clave, "Debe tener como máximo " + maxValor + " caracteres");
                continue;
            }
            resultado.put(clave, valor);
        }
        return resultado;
    }

    /** Lista de objetos JSON (por ejemplo, los rangos de fechas a bloquear). */
    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> listaDeObjetos(String campo, int min, int max, String mensajeMin) {
        Object v = datos.get(campo);
        if (v == null) {
            error(campo, OBLIGATORIO);
            return List.of();
        }
        if (!(v instanceof List<?> lista)) {
            error(campo, TIPO_INVALIDO);
            return List.of();
        }
        if (lista.size() < min) {
            error(campo, mensajeMin);
            return List.of();
        }
        if (lista.size() > max) {
            error(campo, "Debe tener como máximo " + max + " elemento(s)");
            return List.of();
        }
        List<Map<String, Object>> resultado = new ArrayList<>();
        for (int i = 0; i < lista.size(); i++) {
            if (lista.get(i) instanceof Map<?, ?> m) {
                resultado.add((Map<String, Object>) m);
            } else {
                error(campo + "." + i, TIPO_INVALIDO);
            }
        }
        return resultado;
    }

    // ------------------------------------------------------------------
    private static Long aEntero(Object v) {
        if (v instanceof Long l) return l;
        if (v instanceof Integer i) return (long) i;
        if (v instanceof BigDecimal d) {
            try {
                return d.longValueExact();
            } catch (ArithmeticException e) {
                return null;
            }
        }
        if (v instanceof String s && ENTERO.matcher(s.trim()).matches()) return Long.parseLong(s.trim());
        return null;
    }
}
