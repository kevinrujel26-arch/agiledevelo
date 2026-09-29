package alquiler.util;

import alquiler.json.Json;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
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
    private static final Pattern CELULAR = Pattern.compile("^9\\d{8}$");
    private static final Pattern ENTERO = Pattern.compile("^-?\\d{1,18}$");
    // Reglas de texto compartidas con el frontend (frontend/src/utils/validaciones.js)
    private static final Pattern CONTROL = Pattern.compile("\\p{Cc}");
    private static final Pattern SALTOS = Pattern.compile("[\\n\\r\\t]");
    private static final Pattern HTML = Pattern.compile("[<>]");
    private static final Pattern ESPACIOS = Pattern.compile("\\s+", Pattern.UNICODE_CHARACTER_CLASS);
    private static final Pattern ESPACIO = Pattern.compile("\\s", Pattern.UNICODE_CHARACTER_CLASS);
    private static final Pattern LETRA = Pattern.compile("\\p{L}");
    private static final Pattern NUMERO = Pattern.compile("[0-9]");
    private static final Pattern NOMBRE_PERSONA = Pattern.compile("^[\\p{L}\\p{M}'’ -]+$");
    private static final Pattern NOMBRE_CATEGORIA = Pattern.compile("^[\\p{L}\\p{M}0-9 -]+$");
    private static final Pattern MARCAS = Pattern.compile("\\p{M}+");
    private static final Pattern TEXTO_MAQUINA = Pattern.compile("^[\\p{L}\\p{M}0-9 ./+()-]+$");
    private static final Pattern LETRA_O_NUMERO = Pattern.compile("[\\p{L}0-9]");
    private static final Pattern DECIMAL = Pattern.compile("^-?\\d+(\\.\\d+)?$");
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
    /**
     * Texto de una línea (nombres, marcas, motivos...). Rechaza caracteres de control y los
     * signos {@code < >}, recorta los extremos y colapsa los espacios repetidos antes de medirlo.
     * Si no es obligatorio y viene vacío (o solo con espacios), devuelve null.
     *
     * @param etiqueta sujeto de los mensajes, p. ej. "El nombre" o "La marca"
     */
    public String linea(String campo, String etiqueta, int min, int max, boolean obligatorio, String mensajeObligatorio) {
        String s = textoCrudo(campo);
        if (s == null || s.isBlank()) {
            if (obligatorio && !errorDeTipo(campo)) error(campo, mensajeObligatorio != null ? mensajeObligatorio : OBLIGATORIO);
            return null;
        }
        if (!textoSeguro(campo, etiqueta, s, false)) return null;
        String t = ESPACIOS.matcher(s.strip()).replaceAll(" ");
        if (t.length() < min) {
            error(campo, etiqueta + " debe tener al menos " + min + " caracteres");
            return null;
        }
        if (t.length() > max) {
            error(campo, etiqueta + " debe tener como máximo " + max + " caracteres");
            return null;
        }
        return t;
    }

    /** Texto de varias líneas (descripciones): como {@link #linea}, pero conserva los saltos de línea y es opcional. */
    public String parrafo(String campo, String etiqueta, int max) {
        String s = textoCrudo(campo);
        if (s == null || s.isBlank()) return null;
        if (!textoSeguro(campo, etiqueta, s, true)) return null;
        String t = s.strip();
        if (t.length() > max) {
            error(campo, etiqueta + " debe tener como máximo " + max + " caracteres");
            return null;
        }
        return t;
    }

    /** Nombre de una persona: letras (con tildes, ñ, ü), espacios, apóstrofo y guion; 2 a 120 caracteres. */
    public String nombrePersona(String campo) {
        String t = linea(campo, "El nombre", 2, 120, true, "El nombre es obligatorio");
        if (t == null) return null;
        if (!NOMBRE_PERSONA.matcher(t).matches()) {
            error(campo, "El nombre solo puede contener letras, espacios, apóstrofos y guiones");
            return null;
        }
        if (!LETRA.matcher(t).find()) {
            error(campo, "El nombre debe contener al menos una letra");
            return null;
        }
        return t;
    }

    /** Nombre de categoría: 2 a 80 caracteres con al menos una letra; admite números, espacios y guiones. */
    public String nombreCategoria(String campo) {
        String t = linea(campo, "El nombre", 2, 80, true, "El nombre es obligatorio");
        if (t == null) return null;
        if (!NOMBRE_CATEGORIA.matcher(t).matches()) {
            error(campo, "El nombre solo puede contener letras, números, espacios y guiones");
            return null;
        }
        if (!LETRA.matcher(t).find()) {
            error(campo, "El nombre debe contener al menos una letra");
            return null;
        }
        return t;
    }

    /**
     * Nombre, marca o modelo de una máquina: letras, números, espacios y los signos - . / + ( ),
     * con al menos una letra o un número.
     */
    public String textoMaquina(String campo, String etiqueta, int min, int max, String mensajeObligatorio) {
        String t = linea(campo, etiqueta, min, max, true, mensajeObligatorio);
        if (t == null) return null;
        if (!TEXTO_MAQUINA.matcher(t).matches()) {
            error(campo, etiqueta + " solo puede contener letras, números, espacios y los signos - . / + ( )");
            return null;
        }
        if (!LETRA_O_NUMERO.matcher(t).find()) {
            error(campo, etiqueta + " debe contener al menos una letra o un número");
            return null;
        }
        return t;
    }

    /** Texto de una línea obligatorio que debe tener al menos una letra (p. ej. la ubicación). */
    public String lineaConLetra(String campo, String etiqueta, int min, int max, String mensajeObligatorio) {
        String t = linea(campo, etiqueta, min, max, true, mensajeObligatorio);
        if (t == null) return null;
        if (!LETRA.matcher(t).find()) {
            error(campo, etiqueta + " debe contener al menos una letra");
            return null;
        }
        return t;
    }

    /** Clave para comparar textos sin distinguir mayúsculas, tildes ni espacios sobrantes ("Grúas " -> "gruas"). */
    public static String clave(String texto) {
        if (texto == null) return "";
        String sinTildes = MARCAS.matcher(Normalizer.normalize(texto, Normalizer.Form.NFD)).replaceAll("");
        return ESPACIOS.matcher(sinTildes.strip()).replaceAll(" ").toLowerCase(Locale.ROOT);
    }

    /** Contraseña nueva: 8 a 72 caracteres, al menos una letra y un número, sin espacios en los extremos. */
    public String contrasenaNueva(String campo) {
        String s = textoCrudo(campo);
        if (s == null || s.isEmpty()) {
            if (!errorDeTipo(campo)) error(campo, "La contraseña es obligatoria");
            return null;
        }
        String mensaje = null;
        if (!s.equals(s.strip())) mensaje = "La contraseña no puede empezar ni terminar con espacios";
        else if (s.length() < 8) mensaje = "La contraseña debe tener al menos 8 caracteres";
        else if (s.length() > 72) mensaje = "La contraseña debe tener como máximo 72 caracteres";
        else if (!LETRA.matcher(s).find()) mensaje = "La contraseña debe tener al menos una letra";
        else if (!NUMERO.matcher(s).find()) mensaje = "La contraseña debe tener al menos un número";
        if (mensaje != null) {
            error(campo, mensaje);
            return null;
        }
        return s;
    }

    /** El valor si es texto; si viene otro tipo registra el error y devuelve null. */
    private String textoCrudo(String campo) {
        Object v = datos.get(campo);
        if (v == null || v instanceof String) return (String) v;
        error(campo, TIPO_INVALIDO);
        return null;
    }

    private boolean errorDeTipo(String campo) {
        Object v = datos.get(campo);
        return v != null && !(v instanceof String);
    }

    /** Rechaza caracteres de control y etiquetas HTML (signos < y >). */
    private boolean textoSeguro(String campo, String etiqueta, String s, boolean permiteSaltos) {
        String revisar = permiteSaltos ? SALTOS.matcher(s).replaceAll("") : s;
        if (CONTROL.matcher(revisar).find()) {
            error(campo, etiqueta + " contiene caracteres no permitidos");
            return false;
        }
        if (HTML.matcher(s).find()) {
            error(campo, etiqueta + " no puede contener los signos < ni >");
            return false;
        }
        return true;
    }

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

    /** Correo obligatorio, sin espacios, hasta 160 caracteres y con formato válido. Se devuelve en minúsculas. */
    public String correo(String campo) {
        String s = textoCrudo(campo);
        if (s == null || s.isBlank()) {
            if (!errorDeTipo(campo)) error(campo, "El correo es obligatorio");
            return null;
        }
        String c = s.strip();
        String mensaje = null;
        if (ESPACIO.matcher(c).find()) mensaje = "El correo no puede contener espacios";
        else if (c.length() > 160) mensaje = "El correo debe tener como máximo 160 caracteres";
        else if (!CORREO.matcher(c).matches()) mensaje = "El correo no tiene un formato válido";
        if (mensaje != null) {
            error(campo, mensaje);
            return null;
        }
        return c.toLowerCase(Locale.ROOT);
    }

    /**
     * Celular peruano: 9 dígitos que empiezan con 9. Acepta espacios, guiones
     * y el prefijo +51 o 51 (ej. "+51 987-654-321") y lo devuelve normalizado
     * con solo los 9 dígitos ("987654321").
     */
    public String celular(String campo, boolean obligatorio) {
        Object v = datos.get(campo);
        if (v == null || (v instanceof String s && s.isBlank())) {
            if (obligatorio) error(campo, "El celular es obligatorio");
            return null;
        }
        if (!(v instanceof String s)) {
            error(campo, TIPO_INVALIDO);
            return null;
        }
        String numero = s.replaceAll("[\\s-]", "");
        if (numero.startsWith("+51")) numero = numero.substring(3);
        else if (numero.startsWith("51") && numero.length() == 11) numero = numero.substring(2);
        if (!CELULAR.matcher(numero).matches()) {
            error(campo, "Ingresa un celular válido de 9 dígitos que empiece con 9");
            return null;
        }
        return numero;
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

    /**
     * Número decimal (acepta número JSON o texto como "150.50"). Devuelve null si no vino o si hay error.
     *
     * @param etiqueta     sujeto de los mensajes, p. ej. "La tarifa por hora" o "El precio mínimo"
     * @param mayorQueCero true exige {@code > 0}; false admite 0 pero no negativos
     * @param maxDecimales cantidad máxima de decimales (sin contar ceros finales: "150.00" tiene 0)
     */
    public BigDecimal decimal(String campo, String etiqueta, boolean obligatorio, String mensajeObligatorio,
                              boolean mayorQueCero, BigDecimal maximo, int maxDecimales) {
        Object v = datos.get(campo);
        if (v == null || (v instanceof String s && s.isBlank())) {
            if (obligatorio) error(campo, mensajeObligatorio);
            return null;
        }
        BigDecimal d = aDecimal(v);
        String mensaje = null;
        if (d == null) mensaje = etiqueta + " debe ser un número";
        else if (mayorQueCero && d.signum() <= 0) mensaje = etiqueta + " debe ser mayor a 0";
        else if (d.signum() < 0) mensaje = etiqueta + " no puede ser negativo";
        else if (Math.max(d.stripTrailingZeros().scale(), 0) > maxDecimales) {
            mensaje = etiqueta + " puede tener como máximo " + maxDecimales + (maxDecimales == 1 ? " decimal" : " decimales");
        } else if (maximo != null && d.compareTo(maximo) > 0) {
            mensaje = etiqueta + " no puede ser mayor a " + maximo.toPlainString();
        }
        if (mensaje != null) {
            error(campo, mensaje);
            return null;
        }
        return d;
    }

    private static BigDecimal aDecimal(Object v) {
        if (v instanceof BigDecimal b) return b;
        if (v instanceof Long || v instanceof Integer) return new BigDecimal(v.toString());
        if (v instanceof String s && DECIMAL.matcher(s.strip()).matches()) return new BigDecimal(s.strip());
        return null;
    }

    /** Decimal opcional mayor o igual a 0 (filtros de precio). Si no viene, devuelve null. */
    public BigDecimal decimalNoNegativoOpcional(String campo, String etiqueta, BigDecimal maximo) {
        Object v = datos.get(campo);
        if (v == null || (v instanceof String s && s.isBlank())) return null;
        BigDecimal d;
        try {
            if (v instanceof Boolean) throw new NumberFormatException();
            d = v instanceof BigDecimal b ? b : new BigDecimal(v.toString().trim());
        } catch (NumberFormatException e) {
            error(campo, etiqueta + " debe ser un número");
            return null;
        }
        if (d.signum() < 0) {
            error(campo, etiqueta + " no puede ser negativo");
            return null;
        }
        if (maximo != null && d.compareTo(maximo) > 0) {
            error(campo, etiqueta + " es demasiado alto");
            return null;
        }
        return d;
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
    /**
     * Especificaciones técnicas { "Potencia": "146 HP" }: máximo 30 pares, nombre de 1 a 60 caracteres
     * y valor hasta 200, sin nombres vacíos ni repetidos (sin distinguir mayúsculas ni tildes).
     * Todos los errores se registran en el campo "especificaciones".
     */
    public Map<String, String> especificaciones(String campo) {
        Object v = datos.get(campo);
        if (v == null) return null;
        if (!(v instanceof Map<?, ?> mapa)) {
            error(campo, TIPO_INVALIDO);
            return null;
        }
        if (mapa.size() > 30) {
            error(campo, "Puedes agregar como máximo 30 especificaciones");
            return null;
        }
        Map<String, String> resultado = new LinkedHashMap<>();
        Set<String> vistas = new HashSet<>();
        for (Map.Entry<?, ?> e : mapa.entrySet()) {
            if (!(e.getValue() instanceof String valorTexto)) {
                error(campo, TIPO_INVALIDO);
                continue;
            }
            String claveTexto = String.valueOf(e.getKey());
            String nombre = ESPACIOS.matcher(claveTexto.strip()).replaceAll(" ");
            String valor = ESPACIOS.matcher(valorTexto.strip()).replaceAll(" ");
            String mensaje = null;
            if (nombre.isEmpty()) mensaje = "Cada especificación necesita un nombre";
            else if (CONTROL.matcher(claveTexto + valorTexto).find()) mensaje = "Las especificaciones contienen caracteres no permitidos";
            else if (HTML.matcher(claveTexto + valorTexto).find()) mensaje = "Las especificaciones no pueden contener los signos < ni >";
            else if (nombre.length() > 60) mensaje = "El nombre de una especificación debe tener como máximo 60 caracteres";
            else if (valor.length() > 200) mensaje = "El valor de \"" + nombre + "\" debe tener como máximo 200 caracteres";
            else if (!vistas.add(clave(nombre))) mensaje = "La especificación \"" + nombre + "\" está repetida";
            if (mensaje != null) {
                error(campo, mensaje);
                continue;
            }
            resultado.put(nombre, valor);
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
