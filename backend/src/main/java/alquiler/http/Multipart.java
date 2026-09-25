package alquiler.http;

import alquiler.util.ErrorApp;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Lector de formularios "multipart/form-data" (RFC 7578), que es como el
 * navegador envía archivos. El JDK no trae uno, así que está hecho a mano.
 */
public final class Multipart {

    private static final Pattern NOMBRE = Pattern.compile("(?i)\\bname=\"([^\"]*)\"");
    private static final Pattern ARCHIVO = Pattern.compile("(?i)\\bfilename=\"([^\"]*)\"");
    private static final Pattern BOUNDARY = Pattern.compile("(?i)boundary=(?:\"([^\"]+)\"|([^;\\s]+))");
    private static final byte[] CRLF = {'\r', '\n'};
    private static final byte[] FIN_CABECERAS = {'\r', '\n', '\r', '\n'};

    private Multipart() {
    }

    /** Una parte del formulario: un campo de texto o un archivo. */
    public record Parte(String campo, String nombreArchivo, String tipo, byte[] datos) {
        public boolean esArchivo() {
            return nombreArchivo != null;
        }
    }

    public static boolean esMultipart(String contentType) {
        return contentType != null && contentType.toLowerCase(Locale.ROOT).startsWith("multipart/form-data");
    }

    public static List<Parte> leer(byte[] cuerpo, String contentType) {
        Matcher mb = BOUNDARY.matcher(contentType == null ? "" : contentType);
        if (!mb.find()) throw ErrorApp.solicitudInvalida("Formulario multipart sin boundary");
        String boundary = mb.group(1) != null ? mb.group(1) : mb.group(2);
        byte[] delimitador = ("--" + boundary).getBytes(StandardCharsets.ISO_8859_1);
        byte[] separador = ("\r\n--" + boundary).getBytes(StandardCharsets.ISO_8859_1);

        List<Parte> partes = new ArrayList<>();
        int pos = indiceDe(cuerpo, delimitador, 0);
        if (pos < 0) throw ErrorApp.solicitudInvalida("Formulario multipart vacío o mal formado");
        pos += delimitador.length;

        while (true) {
            // "--" después del delimitador = fin del formulario
            if (pos + 1 < cuerpo.length && cuerpo[pos] == '-' && cuerpo[pos + 1] == '-') break;
            if (!empiezaCon(cuerpo, pos, CRLF)) throw ErrorApp.solicitudInvalida("Formulario multipart mal formado");
            pos += 2;

            int finCabeceras = indiceDe(cuerpo, FIN_CABECERAS, pos);
            if (finCabeceras < 0) throw ErrorApp.solicitudInvalida("Formulario multipart mal formado");
            String cabeceras = new String(cuerpo, pos, finCabeceras - pos, StandardCharsets.UTF_8);
            int inicioDatos = finCabeceras + FIN_CABECERAS.length;

            int finDatos = indiceDe(cuerpo, separador, inicioDatos);
            if (finDatos < 0) throw ErrorApp.solicitudInvalida("Formulario multipart incompleto");

            String campo = null;
            String archivo = null;
            String tipo = "text/plain";
            for (String linea : cabeceras.split("\r\n")) {
                int dosPuntos = linea.indexOf(':');
                if (dosPuntos < 0) continue;
                String nombre = linea.substring(0, dosPuntos).trim().toLowerCase(Locale.ROOT);
                String valor = linea.substring(dosPuntos + 1).trim();
                if (nombre.equals("content-disposition")) {
                    Matcher mn = NOMBRE.matcher(valor);
                    if (mn.find()) campo = mn.group(1);
                    Matcher ma = ARCHIVO.matcher(valor);
                    if (ma.find()) archivo = ma.group(1);
                } else if (nombre.equals("content-type")) {
                    tipo = valor.toLowerCase(Locale.ROOT);
                }
            }
            partes.add(new Parte(campo, archivo, tipo, Arrays.copyOfRange(cuerpo, inicioDatos, finDatos)));
            pos = finDatos + separador.length;
        }
        return partes;
    }

    private static boolean empiezaCon(byte[] datos, int desde, byte[] prefijo) {
        if (desde + prefijo.length > datos.length) return false;
        for (int i = 0; i < prefijo.length; i++) {
            if (datos[desde + i] != prefijo[i]) return false;
        }
        return true;
    }

    static int indiceDe(byte[] datos, byte[] buscado, int desde) {
        int ultimo = datos.length - buscado.length;
        byte primero = buscado[0];
        for (int i = Math.max(0, desde); i <= ultimo; i++) {
            if (datos[i] != primero) continue;
            int j = 1;
            while (j < buscado.length && datos[i + j] == buscado[j]) j++;
            if (j == buscado.length) return i;
        }
        return -1;
    }
}
