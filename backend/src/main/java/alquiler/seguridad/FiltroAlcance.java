package alquiler.seguridad;

import alquiler.http.EscritorJson;
import alquiler.http.ManejadorErrores;
import alquiler.json.Json;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.regex.Pattern;

/**
 * Congela el alcance funcional de la entrega (variable ALCANCE_HU, ver Config.alcanceHu).
 *
 *  - Alcance menor que 5: HU5 (registrar y publicar máquina) está apagada. Toda la API de
 *    administración (/api/admin/**: máquinas, fotos, categorías, bloqueos de fechas) responde 404.
 *  - Alcance menor que 6: HU6 (reservar máquina por horas) está apagada:
 *    POST /api/reservas, GET /api/maquinas/{id}/disponibilidad/horas y /api/admin/reservas responden 404.
 *
 * El código de HU5 y HU6 no se borra: para activarlo basta con subir ALCANCE_HU (5 o 6).
 * Responde igual que una ruta inexistente, así el comportamiento es el mismo que si nunca se hubiera programado.
 * Lo que sí queda activo en HU1-HU4 (incluida GET /api/maquinas/{id}/disponibilidad del calendario) no se toca.
 */
public class FiltroAlcance extends OncePerRequestFilter {

    private static final Pattern HORAS_LIBRES = Pattern.compile("^/api/maquinas/[^/]+/disponibilidad/horas/?$");

    private final int alcanceHu;

    public FiltroAlcance(int alcanceHu) {
        this.alcanceHu = alcanceHu;
    }

    /** true si la ruta pertenece a una HU que todavía no está habilitada. */
    public static boolean estaApagada(int alcanceHu, String ruta) {
        if (alcanceHu < 5 && (ruta.equals("/api/admin") || ruta.startsWith("/api/admin/"))) return true; // HU5
        if (alcanceHu < 6 && (ruta.equals("/api/reservas") || ruta.equals("/api/reservas/")
                || HORAS_LIBRES.matcher(ruta).matches()
                || ruta.startsWith("/api/admin/reservas"))) return true; // HU6
        return false;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest peticion, HttpServletResponse respuesta, FilterChain cadena)
            throws ServletException, IOException {
        if (estaApagada(alcanceHu, peticion.getRequestURI())) {
            EscritorJson.escribir(respuesta, 404, Json.obj("error", ManejadorErrores.mensajeRutaInexistente(peticion)));
            return;
        }
        cadena.doFilter(peticion, respuesta);
    }
}
