package alquiler.http;

import alquiler.bd.ErrorBd;
import alquiler.categorias.CategoriaServicio;
import alquiler.config.Config;
import alquiler.json.Json;
import alquiler.util.ErrorApp;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.ErrorResponse;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Convierte cualquier excepción en {"error": "...", "detalles": [...]} con el
 * código HTTP adecuado. El frontend depende de este formato.
 */
@RestControllerAdvice
public class ManejadorErrores {

    /** Código HTTP + cuerpo JSON de un error. */
    public record Traduccion(int estado, Map<String, Object> cuerpo) {
    }

    // Restricciones de la BD -> mensaje entendible para el usuario
    private static final Map<String, Object[]> MENSAJES_RESTRICCION = Map.of(
            "ux_usuarios_correo", new Object[]{409, "Ya existe una cuenta registrada con ese correo"},
            "ux_categorias_nombre", new Object[]{409, CategoriaServicio.NOMBRE_REPETIDO},
            "ux_fotos_una_principal", new Object[]{409, "La máquina ya tiene una foto principal"},
            "ck_max_5_fotos", new Object[]{400, "Una máquina puede tener como máximo 5 fotos"},
            "ex_bloqueos_sin_superposicion", new Object[]{409, "El rango se superpone con otro bloqueo existente de esta máquina"},
            "ex_reservas_sin_superposicion", new Object[]{409, "Las fechas elegidas ya están ocupadas"});

    private final Config config;

    public ManejadorErrores(Config config) {
        this.config = config;
    }

    /** Ruta que no existe (o método no permitido): 404 con el mismo mensaje de siempre. */
    @ExceptionHandler({NoResourceFoundException.class, NoHandlerFoundException.class, HttpRequestMethodNotSupportedException.class})
    public ResponseEntity<Object> rutaInexistente(Exception e, HttpServletRequest peticion) {
        return ResponseEntity.status(404).body(Json.obj("error", mensajeRutaInexistente(peticion)));
    }

    /** También lo usa SeguridadConfig para las rutas que rechaza el firewall (p. ej. con "../"). */
    public static String mensajeRutaInexistente(HttpServletRequest peticion) {
        String ruta = peticion.getRequestURI();
        return ruta.startsWith("/uploads/")
                ? "Archivo no encontrado"
                : "No existe la ruta " + peticion.getMethod() + " " + ruta;
    }

    @ExceptionHandler(Throwable.class)
    public ResponseEntity<Object> general(Throwable error) {
        Traduccion t = traducir(error, config);
        return ResponseEntity.status(t.estado()).body(t.cuerpo());
    }

    /** También la usa el filtro de seguridad, que corre antes que los controladores. */
    public static Traduccion traducir(Throwable error, Config config) {
        if (error instanceof ErrorApp e) {
            Map<String, Object> cuerpo = new LinkedHashMap<>();
            cuerpo.put("error", e.getMessage());
            if (e.getDetalles() != null) cuerpo.put("detalles", e.getDetalles());
            return new Traduccion(e.getEstadoHttp(), cuerpo);
        }
        if (error instanceof ErrorBd e) {
            Object[] conocido = e.getRestriccion() == null ? null : MENSAJES_RESTRICCION.get(e.getRestriccion());
            if (conocido != null) return new Traduccion((Integer) conocido[0], Json.obj("error", conocido[1]));
            if ("23503".equals(e.getEstadoSql())) {
                return new Traduccion(409, Json.obj("error", "El registro está relacionado con otros datos y no se puede modificar así"));
            }
        }
        // Errores propios de Spring MVC (por ejemplo un tipo de contenido no soportado)
        if (error instanceof ErrorResponse e && e.getStatusCode().is4xxClientError()) {
            return new Traduccion(e.getStatusCode().value(), Json.obj("error", "La solicitud no es válida"));
        }
        if (!config.esPrueba) error.printStackTrace();
        return new Traduccion(500, Json.obj("error", "Ocurrió un error inesperado. Intenta nuevamente"));
    }
}
