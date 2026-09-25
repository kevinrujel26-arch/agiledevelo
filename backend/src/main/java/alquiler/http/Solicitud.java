package alquiler.http;

import com.sun.net.httpserver.HttpExchange;
import alquiler.json.Json;
import alquiler.seguridad.UsuarioSesion;
import alquiler.util.ErrorApp;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** Datos de una petición HTTP entrante. */
public final class Solicitud {

    /** Tamaño máximo de un cuerpo JSON. */
    public static final int LIMITE_JSON = 100 * 1024;

    private final HttpExchange intercambio;
    private final Map<String, String> params;
    private final Map<String, String> query;
    private byte[] cuerpo;
    private UsuarioSesion usuario;

    public Solicitud(HttpExchange intercambio, Map<String, String> params) {
        this.intercambio = intercambio;
        this.params = params == null ? Map.of() : params;
        this.query = leerQuery(intercambio.getRequestURI().getRawQuery());
    }

    public String metodo() {
        return intercambio.getRequestMethod();
    }

    public String ruta() {
        return intercambio.getRequestURI().getPath();
    }

    public String cabecera(String nombre) {
        return intercambio.getRequestHeaders().getFirst(nombre);
    }

    /** Parámetros de la ruta, p. ej. {id} en /api/maquinas/{id} */
    public Map<String, String> params() {
        return params;
    }

    /** Parámetros de la URL después de "?" */
    public Map<String, String> query() {
        return query;
    }

    /** Parámetro numérico de la ruta; responde 400 si no es un entero positivo. */
    public long id(String nombre) {
        String v = params.get(nombre);
        try {
            long n = Long.parseLong(v);
            if (n > 0) return n;
        } catch (NumberFormatException | NullPointerException ignorado) {
            // cae al error
        }
        throw new ErrorApp(400, nombre + ": Debe ser un número entero positivo");
    }

    public String ip() {
        String reenviada = cabecera("X-Forwarded-For");
        if (reenviada != null && !reenviada.isBlank()) return reenviada.split(",")[0].trim();
        return intercambio.getRemoteAddress().getAddress().getHostAddress();
    }

    public UsuarioSesion usuario() {
        return usuario;
    }

    void setUsuario(UsuarioSesion usuario) {
        this.usuario = usuario;
    }

    // ------------------------------------------------------------------
    // Cuerpo
    // ------------------------------------------------------------------
    /** Lee el cuerpo completo; responde 413 si supera el límite. */
    public byte[] cuerpo(int limiteBytes) {
        if (cuerpo != null) return cuerpo;
        String largo = cabecera("Content-Length");
        if (largo != null) {
            try {
                if (Long.parseLong(largo.trim()) > limiteBytes) throw ErrorApp.demasiadoGrande();
            } catch (NumberFormatException ignorado) {
                // se valida al leer
            }
        }
        try (InputStream in = intercambio.getRequestBody()) {
            byte[] leido = in.readNBytes(limiteBytes + 1);
            if (leido.length > limiteBytes) throw ErrorApp.demasiadoGrande();
            cuerpo = leido;
            return cuerpo;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Cuerpo JSON como objeto. Si no se envió nada, devuelve un objeto vacío. */
    @SuppressWarnings("unchecked")
    public Map<String, Object> json() {
        String tipo = cabecera("Content-Type");
        byte[] datos = cuerpo(LIMITE_JSON);
        if (datos.length == 0 || tipo == null || !tipo.toLowerCase().contains("json")) {
            return new LinkedHashMap<>();
        }
        Object valor;
        try {
            valor = Json.leer(new String(datos, StandardCharsets.UTF_8));
        } catch (Json.JsonInvalidoException e) {
            throw ErrorApp.solicitudInvalida("El cuerpo de la petición no es un JSON válido");
        }
        if (!(valor instanceof Map)) {
            throw ErrorApp.solicitudInvalida("Se esperaba un objeto JSON");
        }
        return (Map<String, Object>) valor;
    }

    private static Map<String, String> leerQuery(String raw) {
        if (raw == null || raw.isEmpty()) return Collections.emptyMap();
        Map<String, String> mapa = new LinkedHashMap<>();
        for (String par : raw.split("&")) {
            if (par.isEmpty()) continue;
            int igual = par.indexOf('=');
            String clave = igual >= 0 ? par.substring(0, igual) : par;
            String valor = igual >= 0 ? par.substring(igual + 1) : "";
            mapa.putIfAbsent(decodificar(clave), decodificar(valor));
        }
        return mapa;
    }

    private static String decodificar(String s) {
        try {
            return URLDecoder.decode(s, StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            return s;
        }
    }
}
