package alquiler.seguridad;

import alquiler.bd.Bd;
import alquiler.bd.Fila;
import alquiler.config.Config;
import alquiler.util.ErrorApp;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;

/**
 * EN-03 / EN-05: valida el token "Authorization: Bearer ..." de cada petición.
 *
 * No basta con que la firma del JWT sea correcta: además la sesión debe
 * seguir vigente en la tabla "sesiones" (no cerrada, no vencida, sin
 * exceso de inactividad) y el usuario debe estar activo.
 */
public class Autenticador {

    /** Solo se actualiza ultima_actividad si pasó más de 1 minuto (evita escribir en cada petición). */
    private static final Duration INTERVALO_ACTIVIDAD = Duration.ofMinutes(1);
    private static final String SESION_EXPIRADA = "Tu sesión expiró. Inicia sesión nuevamente";

    private final Bd bd;
    private final Jwt jwt;
    private final Config config;

    public Autenticador(Bd bd, Jwt jwt, Config config) {
        this.bd = bd;
        this.jwt = jwt;
        this.config = config;
    }

    public UsuarioSesion autenticar(String cabeceraAuthorization) {
        if (cabeceraAuthorization == null || !cabeceraAuthorization.startsWith("Bearer ")) {
            throw ErrorApp.noAutenticado();
        }
        String token = cabeceraAuthorization.substring("Bearer ".length()).trim();
        Map<String, Object> datos = jwt.verificar(token);
        if (datos == null || !(datos.get("sid") instanceof String sid)) {
            throw ErrorApp.noAutenticado(SESION_EXPIRADA);
        }

        Fila s = bd.uno("""
                SELECT s.id, s.recordarme, s.ultima_actividad, s.expira_en, s.revocada_en,
                       u.id AS usuario_id, u.nombre, u.correo, u.rol, u.activo
                  FROM sesiones s
                  JOIN usuarios u ON u.id = s.usuario_id
                 WHERE s.id = ?::uuid""", sid);

        Instant ahora = Instant.now();
        if (s == null || s.instante("revocada_en") != null || !s.instante("expira_en").isAfter(ahora)) {
            throw ErrorApp.noAutenticado(SESION_EXPIRADA);
        }
        if (!s.bool("activo")) {
            throw ErrorApp.noAutenticado("Tu cuenta está desactivada");
        }

        Duration inactiva = Duration.between(s.instante("ultima_actividad"), ahora);
        Duration limite = s.bool("recordarme")
                ? Duration.ofDays(config.recordarmeInactividadDias)
                : Duration.ofMinutes(config.inactividadMinutos);
        if (inactiva.compareTo(limite) > 0) {
            bd.ejecutar("UPDATE sesiones SET revocada_en = now() WHERE id = ?::uuid", sid);
            throw ErrorApp.noAutenticado("Tu sesión expiró por inactividad. Inicia sesión nuevamente");
        }
        if (inactiva.compareTo(INTERVALO_ACTIVIDAD) > 0) {
            bd.ejecutar("UPDATE sesiones SET ultima_actividad = now() WHERE id = ?::uuid", sid);
        }

        return new UsuarioSesion(
                s.entero("usuario_id"), s.texto("nombre"), s.texto("correo"),
                Rol.valueOf(s.texto("rol")), sid);
    }

    /** EN-05: exige uno de los roles indicados. */
    public static void exigirRol(UsuarioSesion usuario, Rol... permitidos) {
        if (usuario == null) throw ErrorApp.noAutenticado();
        for (Rol r : permitidos) {
            if (usuario.rol() == r) return;
        }
        throw ErrorApp.prohibido();
    }
}
