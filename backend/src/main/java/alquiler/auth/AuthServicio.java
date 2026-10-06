package alquiler.auth;

import org.springframework.stereotype.Service;
import alquiler.bd.Fila;
import alquiler.config.Config;
import alquiler.json.Json;
import alquiler.seguridad.Contrasenas;
import alquiler.seguridad.Jwt;
import alquiler.seguridad.Rol;
import alquiler.util.ErrorApp;

import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Map;

/** HU-01 Registrar cliente · HU-02 Iniciar y cerrar sesión · EN-03 Autenticación base */
@Service
public class AuthServicio {

    /** HU-02 criterio 2: el mismo mensaje si el correo no existe o la contraseña es incorrecta. */
    public static final String MENSAJE_CREDENCIALES = "Correo o contraseña incorrectos";
    /** Cuenta creada con Google: no tiene contraseña, así que el login normal no la acepta. */
    public static final String MENSAJE_CUENTA_GOOGLE = "Esta cuenta usa Google. Ingresa con el botón \"Continuar con Google\"";

    private static final int MAX_NOMBRE = 120;
    private static final int MAX_CORREO = 160;

    private final UsuarioRepositorio usuarios;
    private final SesionRepositorio sesiones;
    private final Contrasenas contrasenas;
    private final Jwt jwt;
    private final Config config;
    private final VerificadorGoogle google;
    /** Hash de relleno: si el correo no existe igual se compara, para que la respuesta tarde lo mismo. */
    private final String hashRelleno;

    public AuthServicio(UsuarioRepositorio usuarios, SesionRepositorio sesiones,
                        Contrasenas contrasenas, Jwt jwt, Config config, VerificadorGoogle google) {
        this.usuarios = usuarios;
        this.sesiones = sesiones;
        this.contrasenas = contrasenas;
        this.jwt = jwt;
        this.config = config;
        this.google = google;
        this.hashRelleno = contrasenas.cifrar("contrasena-de-relleno");
    }

    public static Map<String, Object> usuarioJson(Fila u) {
        return Json.obj("id", u.entero("id"), "nombre", u.texto("nombre"), "correo", u.texto("correo"),
                "telefono", u.texto("telefono"), "rol", u.texto("rol"));
    }

    /** HU-01: el rol siempre es CLIENTE (criterio 5), aunque envíen otro. */
    public Map<String, Object> registrarCliente(String nombre, String correo, String telefono, String contrasena) {
        if (usuarios.existeCorreo(correo)) {
            throw ErrorApp.conflicto("Ya existe una cuenta registrada con ese correo");
        }
        String hash = contrasenas.cifrar(contrasena);
        return usuarioJson(usuarios.crear(nombre, correo, telefono, hash, Rol.CLIENTE.name()));
    }

    public Map<String, Object> iniciarSesion(String correo, String contrasena, boolean recordarme,
                                             String ip, String agenteUsuario) {
        Fila u = usuarios.buscarPorCorreo(correo);
        if (u == null) {
            contrasenas.verificar(contrasena, hashRelleno);
            throw ErrorApp.noAutenticado(MENSAJE_CREDENCIALES);
        }
        // Sin contraseña guardada ninguna contraseña es válida (ni vacía ni otra)
        if (u.texto("contrasena_hash") == null) {
            throw ErrorApp.noAutenticado(MENSAJE_CUENTA_GOOGLE);
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

        exigirActivo(u);
        usuarios.reiniciarIntentos(u.entero("id"));
        return abrirSesion(u, recordarme, ip, agenteUsuario);
    }

    /**
     * "Continuar con Google": verifica el ID token y abre la misma sesión que el login normal.
     *  - Si ya hay una cuenta con ese correo, la vincula (guarda google_id) y entra con ella.
     *  - Si no, crea un CLIENTE sin contraseña y sin celular (se le pide luego en "Mi cuenta").
     * Un ADMINISTRADOR nunca se crea por aquí: ese rol solo se asigna por SQL.
     */
    public Map<String, Object> iniciarSesionGoogle(String credencial, boolean recordarme,
                                                   String ip, String agenteUsuario) {
        if (!google.configurado()) {
            throw new ErrorApp(503, "El inicio de sesión con Google no está disponible");
        }
        VerificadorGoogle.CuentaGoogle cuenta = google.verificar(credencial);
        if (cuenta == null || cuenta.id() == null || cuenta.id().isBlank()) {
            throw ErrorApp.noAutenticado("No se pudo verificar tu cuenta de Google. Intenta nuevamente");
        }
        if (!cuenta.correoVerificado() || cuenta.correo() == null || cuenta.correo().isBlank()) {
            throw ErrorApp.noAutenticado("Tu correo de Google no está verificado. Verifícalo en Google o regístrate con correo y contraseña");
        }
        String correo = cuenta.correo().trim().toLowerCase(Locale.ROOT);
        if (correo.length() > MAX_CORREO) {
            throw ErrorApp.solicitudInvalida("El correo de tu cuenta de Google es demasiado largo");
        }

        boolean nuevo = false;
        Fila u = usuarios.buscarPorGoogleId(cuenta.id());
        if (u == null) {
            u = usuarios.buscarPorCorreo(correo);
            if (u == null) {
                u = usuarios.crearConGoogle(nombreDesdeGoogle(cuenta.nombre(), correo), correo, cuenta.id(), Rol.CLIENTE.name());
                nuevo = true;
            } else {
                if (u.texto("google_id") != null) {
                    throw ErrorApp.conflicto("Este correo ya está vinculado a otra cuenta de Google");
                }
                exigirActivo(u);
                u = usuarios.vincularGoogle(u.entero("id"), cuenta.id());
                if (u == null) throw ErrorApp.conflicto("Este correo ya está vinculado a otra cuenta de Google");
            }
        }
        exigirActivo(u);

        Map<String, Object> respuesta = abrirSesion(u, recordarme, ip, agenteUsuario);
        respuesta.put("nuevo", nuevo);
        return respuesta;
    }

    /** Completa el celular que falta (cuentas creadas con Google o antiguas). */
    public Map<String, Object> actualizarTelefono(long usuarioId, String telefono) {
        return usuarioJson(usuarios.actualizarTelefono(usuarioId, telefono));
    }

    /** HU-15 criterio 2: un cliente desactivado no puede iniciar sesión */
    private static void exigirActivo(Fila u) {
        if (!u.bool("activo")) {
            throw ErrorApp.prohibido("Tu cuenta está desactivada. Comunícate con el administrador");
        }
    }

    /** El nombre de Google se limpia (sin < >, sin caracteres de control) y se recorta; si falta, se usa el correo. */
    static String nombreDesdeGoogle(String nombre, String correo) {
        String limpio = nombre == null ? "" : nombre.replaceAll("[\\p{Cc}<>]", " ").replaceAll("\\s+", " ").trim();
        if (limpio.isEmpty()) limpio = correo.substring(0, Math.max(1, correo.indexOf('@')));
        return limpio.length() > MAX_NOMBRE ? limpio.substring(0, MAX_NOMBRE).trim() : limpio;
    }

    /** Crea la sesión y el JWT; es la misma para el login normal y el de Google. */
    private Map<String, Object> abrirSesion(Fila u, boolean recordarme, String ip, String agenteUsuario) {
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
