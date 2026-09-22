package pe.upao.alquiler.util;

import java.util.List;
import java.util.Map;

/**
 * Error de negocio con código HTTP. El servidor lo convierte en
 * {@code {"error": "...", "detalles": [...]}}.
 */
public class ErrorApp extends RuntimeException {

    private final int estadoHttp;
    private final List<Map<String, Object>> detalles;

    public ErrorApp(int estadoHttp, String mensaje) {
        this(estadoHttp, mensaje, null);
    }

    public ErrorApp(int estadoHttp, String mensaje, List<Map<String, Object>> detalles) {
        super(mensaje);
        this.estadoHttp = estadoHttp;
        this.detalles = detalles;
    }

    public int getEstadoHttp() {
        return estadoHttp;
    }

    public List<Map<String, Object>> getDetalles() {
        return detalles;
    }

    // ---- Atajos para los errores más comunes ----

    public static ErrorApp solicitudInvalida(String mensaje) {
        return new ErrorApp(400, mensaje);
    }

    public static ErrorApp noAutenticado(String mensaje) {
        return new ErrorApp(401, mensaje);
    }

    public static ErrorApp noAutenticado() {
        return new ErrorApp(401, "Debes iniciar sesión");
    }

    public static ErrorApp prohibido(String mensaje) {
        return new ErrorApp(403, mensaje);
    }

    public static ErrorApp prohibido() {
        return new ErrorApp(403, "No tienes permiso para realizar esta acción");
    }

    public static ErrorApp noEncontrado(String mensaje) {
        return new ErrorApp(404, mensaje);
    }

    public static ErrorApp conflicto(String mensaje) {
        return new ErrorApp(409, mensaje);
    }

    public static ErrorApp demasiadoGrande() {
        return new ErrorApp(413, "La petición es demasiado grande");
    }

    public static ErrorApp bloqueado(String mensaje) {
        return new ErrorApp(423, mensaje);
    }
}
