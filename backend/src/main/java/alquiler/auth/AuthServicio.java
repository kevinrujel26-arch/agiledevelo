package alquiler.auth;

import alquiler.bd.Fila;
import alquiler.config.Config;
import alquiler.json.Json;
import alquiler.seguridad.Contrasenas;
import alquiler.seguridad.Jwt;
import alquiler.seguridad.Rol;
import alquiler.util.ErrorApp;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;

/** HU-01 Registrar cliente · HU-02 Iniciar y cerrar sesión · EN-03 Autenticación base */
public class AuthServicio {

    /** HU-02 criterio 2: el mismo mensaje si el correo no existe o la contraseña es incorrecta. */
    public static final String MENSAJE_CREDENCIALES = "Correo o contraseña incorrectos";

    private final UsuarioRepositorio usuarios;
    private final SesionRepositorio sesiones;
    private final Contrasenas contrasenas;
    private final Jwt jwt;
    private final Config config;
    /** Hash de relleno: si el correo no existe igual se compara, para que la respuesta tarde lo mismo. */
    private final String hashRelleno;

    public AuthServicio(UsuarioRepositorio usuarios, SesionRepositorio sesiones,
                        Contrasenas contrasenas, Jwt jwt, Config config) {
        this.usuarios = usuarios;
        this.sesiones = sesiones;
        this.contrasenas = contrasenas;
        this.jwt = jwt;
        this.config = config;
        this.hashRelleno = contrasenas.cifrar("contrasena-de-relleno");
    }

    public static Map<String, Object> usuarioJson(Fila u) {
        return Json.obj("id", u.entero("id"), "nombre", u.texto("nombre"), "correo", u.texto("correo"), "rol", u.texto("rol"));
    }

    /** HU-01: el rol siempre es CLIENTE (criterio 5), aunque envíen otro. */
    public Map<String, Object> registrarCliente(String nombre, String correo, String contrasena) {
        if (usuarios.existeCorreo(correo)) {
            throw ErrorApp.conflicto("Ya existe una cuenta registrada con ese correo");
        }
        String hash = contrasenas.cifrar(contrasena);
        return usuarioJson(usuarios.crear(nombre, correo, hash, Rol.CLIENTE.name()));
    }

    public Map<String, Object> iniciarSesion(String correo, String contrasena, boolean recordarme,
                                             String ip, String agenteUsuario) {
        Fila u = usuarios.buscarPorCorreo(correo);
        if (u == null) {
            contrasenas.verificar(contrasena, hashRelleno);
            throw ErrorApp.noAutenticado(MENSAJE_CREDENCIALES);
        }

        // HU-02 criterio 5: cuenta bloqueada temporalmente
        Instant bloqueadoHasta = u.instante("bloqueado_hasta");
        if (bloqueadoHasta != null && bloqueadoHasta.isAfter(Instant.now())) {
            throw ErrorApp.bloqueado("Cuenta bloqueada temporalmente por varios intentos fallidos. Intenta de nuevo en "
                    + minutosRestantes(bloqueadoHasta) + " minuto(s)");
        }

        if (!contrasenas.verificar(contrasena, u.texto("contrasena_hash"))) {
            Fila estado = usuarios.registrarIntentoFallido(u.entero("id"), config.maxIntentosFallidos, config.minutosBloqueo);
            if (estado.instante("bloqueado_hasta") != null) {
                throw ErrorApp.bloqueado("Cuenta bloqueada temporalmente por " + config.maxIntentosFallidos
                        + " intentos fallidos. Intenta de nuevo en " + config.minutosBloqueo + " minutos");
            }
            throw ErrorApp.noAutenticado(MENSAJE_CREDENCIALES);
        }

        // HU-15 criterio 2: un cliente desactivado no puede iniciar sesión
        if (!u.bool("activo")) {
            throw ErrorApp.prohibido("Tu cuenta está desactivada. Comunícate con el administrador");
        }
        usuarios.reiniciarIntentos(u.entero("id"));

        // HU-02 criterio 6: "Recordarme" extiende la sesión
        Duration duracion = recordarme
                ? Duration.ofDays(config.recordarmeDuracionDias)
                : Duration.ofHours(config.duracionHoras);
        Instant expiraEn = Instant.now().plus(duracion);

        String agente = agenteUsuario == null ? null
                : agenteUsuario.substring(0, Math.min(255, agenteUsuario.length()));
        String sesionId = sesiones.crear(u.entero("id"), recordarme, expiraEn, ip, agente);
        String token = jwt.firmar(Json.obj("sid", sesionId, "rol", u.texto("rol")), String.valueOf(u.entero("id")), expiraEn);

        return Json.obj(
                "token", token,
                "expiraEn", expiraEn,
                "recordarme", recordarme,
                "usuario", usuarioJson(u));
    }

    /** HU-02 criterio 4 */
    public void cerrarSesion(String sesionId) {
        sesiones.revocar(sesionId);
    }

    private static long minutosRestantes(Instant hasta) {
        long segundos = Duration.between(Instant.now(), hasta).getSeconds();
        return Math.max(1, (segundos + 59) / 60);
    }
}
