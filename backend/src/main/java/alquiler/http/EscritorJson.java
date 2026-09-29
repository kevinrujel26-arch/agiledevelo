package alquiler.http;

import alquiler.json.Json;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/** Escribe una respuesta JSON a mano (lo necesitan los filtros de seguridad, que están antes de Spring MVC). */
public final class EscritorJson {

    private EscritorJson() {
    }

    public static void escribir(HttpServletResponse respuesta, int estado, Object cuerpo) throws IOException {
        byte[] bytes = Json.escribir(cuerpo).getBytes(StandardCharsets.UTF_8);
        respuesta.setStatus(estado);
        respuesta.setContentType("application/json;charset=UTF-8");
        respuesta.setContentLength(bytes.length);
        respuesta.getOutputStream().write(bytes);
    }
}
