package alquiler.json;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Lector y escritor de JSON escrito a mano (sin librerías).
 *
 * <ul>
 *   <li>Objeto JSON  -> {@code LinkedHashMap<String, Object>} (conserva el orden)</li>
 *   <li>Arreglo      -> {@code ArrayList<Object>}</li>
 *   <li>Número       -> {@code Long} si es entero, si no {@code BigDecimal}</li>
 *   <li>Texto        -> {@code String}; true/false -> {@code Boolean}; null -> {@code null}</li>
 * </ul>
 */
public final class Json {

    private Json() {
    }

    /** Error de sintaxis al leer un JSON. */
    public static class JsonInvalidoException extends RuntimeException {
        public JsonInvalidoException(String mensaje) {
            super(mensaje);
        }
    }

    /** Un objeto que sabe convertirse a JSON (lo usan los modelos). */
    public interface Convertible {
        Map<String, Object> aJson();
    }

    // =====================================================================
    // Construcción rápida de objetos:  Json.obj("id", 1, "nombre", "Ana")
    // =====================================================================
    public static Map<String, Object> obj(Object... claveValor) {
        if (claveValor.length % 2 != 0) {
            throw new IllegalArgumentException("Json.obj necesita pares clave-valor");
        }
        Map<String, Object> mapa = new LinkedHashMap<>();
        for (int i = 0; i < claveValor.length; i += 2) {
            mapa.put((String) claveValor[i], claveValor[i + 1]);
        }
        return mapa;
    }

    // =====================================================================
    // Escritura
    // =====================================================================
    public static String escribir(Object valor) {
        StringBuilder sb = new StringBuilder();
        escribir(valor, sb);
        return sb.toString();
    }

    private static void escribir(Object v, StringBuilder sb) {
        if (v == null) {
            sb.append("null");
        } else if (v instanceof String s) {
            escribirTexto(s, sb);
        } else if (v instanceof Boolean b) {
            sb.append(b);
        } else if (v instanceof BigDecimal d) {
            sb.append(d.signum() == 0 ? "0" : d.stripTrailingZeros().toPlainString());
        } else if (v instanceof Double || v instanceof Float) {
            double d = ((Number) v).doubleValue();
            if (Double.isNaN(d) || Double.isInfinite(d)) {
                sb.append("null");
            } else if (d == Math.rint(d) && Math.abs(d) < 1e15) {
                sb.append((long) d);
            } else {
                sb.append(d);
            }
        } else if (v instanceof Number n) {
            sb.append(n.longValue());
        } else if (v instanceof Map<?, ?> mapa) {
            sb.append('{');
            boolean primero = true;
            for (Map.Entry<?, ?> e : mapa.entrySet()) {
                if (!primero) sb.append(',');
                primero = false;
                escribirTexto(String.valueOf(e.getKey()), sb);
                sb.append(':');
                escribir(e.getValue(), sb);
            }
            sb.append('}');
        } else if (v instanceof Collection<?> lista) {
            sb.append('[');
            boolean primero = true;
            for (Object item : lista) {
                if (!primero) sb.append(',');
                primero = false;
                escribir(item, sb);
            }
            sb.append(']');
        } else if (v instanceof Convertible c) {
            escribir(c.aJson(), sb);
        } else if (v instanceof Instant i) {
            escribirTexto(i.toString(), sb);
        } else if (v instanceof OffsetDateTime o) {
            escribirTexto(o.toInstant().toString(), sb);
        } else if (v instanceof LocalDate f) {
            escribirTexto(f.toString(), sb);
        } else if (v instanceof Enum<?> e) {
            escribirTexto(e.name(), sb);
        } else {
            escribirTexto(v.toString(), sb);
        }
    }

    private static void escribirTexto(String s, StringBuilder sb) {
        sb.append('"');
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                case '\b' -> sb.append("\\b");
                case '\f' -> sb.append("\\f");
                default -> {
                    if (c < 0x20 || c == ' ' || c == ' ') {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
                }
            }
        }
        sb.append('"');
    }

    // =====================================================================
    // Lectura (analizador descendente recursivo)
    // =====================================================================
    public static Object leer(String texto) {
        if (texto == null) throw new JsonInvalidoException("JSON vacío");
        Lector lector = new Lector(texto);
        lector.saltarEspacios();
        Object valor = lector.valor(0);
        lector.saltarEspacios();
        if (!lector.fin()) throw lector.error("Contenido extra al final");
        return valor;
    }

    private static final class Lector {
        private static final int PROFUNDIDAD_MAXIMA = 64;
        private final String s;
        private int pos;

        Lector(String s) {
            this.s = s;
        }

        boolean fin() {
            return pos >= s.length();
        }

        JsonInvalidoException error(String mensaje) {
            return new JsonInvalidoException(mensaje + " (posición " + pos + ")");
        }

        void saltarEspacios() {
            while (!fin()) {
                char c = s.charAt(pos);
                if (c == ' ' || c == '\n' || c == '\r' || c == '\t') pos++;
                else break;
            }
        }

        Object valor(int profundidad) {
            if (profundidad > PROFUNDIDAD_MAXIMA) throw error("JSON demasiado anidado");
            if (fin()) throw error("Se esperaba un valor");
            char c = s.charAt(pos);
            return switch (c) {
                case '{' -> objeto(profundidad);
                case '[' -> arreglo(profundidad);
                case '"' -> texto();
                case 't' -> literal("true", Boolean.TRUE);
                case 'f' -> literal("false", Boolean.FALSE);
                case 'n' -> literal("null", null);
                default -> {
                    if (c == '-' || (c >= '0' && c <= '9')) yield numero();
                    throw error("Carácter inesperado '" + c + "'");
                }
            };
        }

        Object literal(String palabra, Object valor) {
            if (!s.startsWith(palabra, pos)) throw error("Se esperaba " + palabra);
            pos += palabra.length();
            return valor;
        }

        Map<String, Object> objeto(int profundidad) {
            Map<String, Object> mapa = new LinkedHashMap<>();
            pos++; // {
            saltarEspacios();
            if (!fin() && s.charAt(pos) == '}') {
                pos++;
                return mapa;
            }
            while (true) {
                saltarEspacios();
                if (fin() || s.charAt(pos) != '"') throw error("Se esperaba el nombre de un campo");
                String clave = texto();
                saltarEspacios();
                if (fin() || s.charAt(pos) != ':') throw error("Se esperaba ':'");
                pos++;
                saltarEspacios();
                mapa.put(clave, valor(profundidad + 1));
                saltarEspacios();
                if (fin()) throw error("Objeto sin cerrar");
                char c = s.charAt(pos++);
                if (c == '}') return mapa;
                if (c != ',') throw error("Se esperaba ',' o '}'");
            }
        }

        List<Object> arreglo(int profundidad) {
            List<Object> lista = new ArrayList<>();
            pos++; // [
            saltarEspacios();
            if (!fin() && s.charAt(pos) == ']') {
                pos++;
                return lista;
            }
            while (true) {
                saltarEspacios();
                lista.add(valor(profundidad + 1));
                saltarEspacios();
                if (fin()) throw error("Arreglo sin cerrar");
                char c = s.charAt(pos++);
                if (c == ']') return lista;
                if (c != ',') throw error("Se esperaba ',' o ']'");
            }
        }

        String texto() {
            pos++; // "
            StringBuilder sb = new StringBuilder();
            while (true) {
                if (fin()) throw error("Texto sin cerrar");
                char c = s.charAt(pos++);
                if (c == '"') return sb.toString();
                if (c < 0x20) throw error("Carácter de control dentro de un texto");
                if (c != '\\') {
                    sb.append(c);
                    continue;
                }
                if (fin()) throw error("Escape incompleto");
                char e = s.charAt(pos++);
                switch (e) {
                    case '"' -> sb.append('"');
                    case '\\' -> sb.append('\\');
                    case '/' -> sb.append('/');
                    case 'b' -> sb.append('\b');
                    case 'f' -> sb.append('\f');
                    case 'n' -> sb.append('\n');
                    case 'r' -> sb.append('\r');
                    case 't' -> sb.append('\t');
                    case 'u' -> {
                        if (pos + 4 > s.length()) throw error("Escape \\u incompleto");
                        try {
                            sb.append((char) Integer.parseInt(s.substring(pos, pos + 4), 16));
                        } catch (NumberFormatException ex) {
                            throw error("Escape \\u inválido");
                        }
                        pos += 4;
                    }
                    default -> throw error("Escape inválido \\" + e);
                }
            }
        }

        Object numero() {
            int inicio = pos;
            if (s.charAt(pos) == '-') pos++;
            if (fin()) throw error("Número incompleto");
            if (s.charAt(pos) == '0') {
                pos++;
            } else if (Character.isDigit(s.charAt(pos))) {
                while (!fin() && Character.isDigit(s.charAt(pos))) pos++;
            } else {
                throw error("Número inválido");
            }
            boolean entero = true;
            if (!fin() && s.charAt(pos) == '.') {
                entero = false;
                pos++;
                if (fin() || !Character.isDigit(s.charAt(pos))) throw error("Número inválido");
                while (!fin() && Character.isDigit(s.charAt(pos))) pos++;
            }
            if (!fin() && (s.charAt(pos) == 'e' || s.charAt(pos) == 'E')) {
                entero = false;
                pos++;
                if (!fin() && (s.charAt(pos) == '+' || s.charAt(pos) == '-')) pos++;
                if (fin() || !Character.isDigit(s.charAt(pos))) throw error("Número inválido");
                while (!fin() && Character.isDigit(s.charAt(pos))) pos++;
            }
            String num = s.substring(inicio, pos);
            if (entero && num.length() <= 18) return Long.parseLong(num);
            return new BigDecimal(num);
        }
    }
}
