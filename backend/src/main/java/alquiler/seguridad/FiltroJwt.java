package alquiler.seguridad;

import alquiler.config.Config;
import alquiler.http.EscritorJson;
import alquiler.http.ManejadorErrores;
import alquiler.util.ErrorApp;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * Lee "Authorization: Bearer ..." y, si la sesión es válida (firma del JWT +
 * fila vigente en la tabla "sesiones", ver {@link Autenticador}), deja al
 * usuario autenticado en Spring Security. Si no lo es, no lo autentica:
 * Spring decide después si la ruta lo exigía y responde 401 con el motivo.
 *
 * No es un @Component a propósito: se registra solo dentro de la cadena de
 * seguridad (SeguridadConfig). Si fuera @Component, Spring Boot lo ejecutaría dos veces.
 */
public class FiltroJwt extends OncePerRequestFilter {

    /** Atributo de la petición donde se guarda por qué falló la autenticación (para el mensaje 401). */
    public static final String ATRIBUTO_ERROR = "alquiler.errorAutenticacion";

    private final Autenticador autenticador;
    private final Config config;

    public FiltroJwt(Autenticador autenticador, Config config) {
        this.autenticador = autenticador;
        this.config = config;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest peticion, HttpServletResponse respuesta, FilterChain cadena)
            throws ServletException, IOException {
        String cabecera = peticion.getHeader("Authorization");
        if (cabecera != null && !cabecera.isBlank() && esRutaProtegida(peticion)) {
            try {
                UsuarioSesion usuario = autenticador.autenticar(cabecera);
                var autenticacion = new UsernamePasswordAuthenticationToken(
                        usuario, null, List.of(new SimpleGrantedAuthority("ROLE_" + usuario.rol().name())));
                SecurityContextHolder.getContext().setAuthentication(autenticacion);
            } catch (ErrorApp e) {
                peticion.setAttribute(ATRIBUTO_ERROR, e);
            } catch (RuntimeException e) { // por ejemplo, la BD no responde
                ManejadorErrores.Traduccion t = ManejadorErrores.traducir(e, config);
                EscritorJson.escribir(respuesta, t.estado(), t.cuerpo());
                return;
            }
        }
        cadena.doFilter(peticion, respuesta);
    }

    /**
     * Las rutas públicas ignoran el token (como antes): así un token vencido no
     * estorba al ver el catálogo y no se cuenta como actividad de la sesión.
     */
    private static boolean esRutaProtegida(HttpServletRequest peticion) {
        String ruta = peticion.getRequestURI();
        return ruta.startsWith("/api/admin/") || ruta.equals("/api/auth/logout") || ruta.equals("/api/auth/yo") || ruta.equals("/api/auth/cambiar-contrasena");
    }
}
