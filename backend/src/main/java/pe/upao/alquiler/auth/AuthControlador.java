package pe.upao.alquiler.auth;

import pe.upao.alquiler.http.Enrutador;
import pe.upao.alquiler.http.Respuesta;
import pe.upao.alquiler.http.Solicitud;
import pe.upao.alquiler.json.Json;
import pe.upao.alquiler.util.Validador;

import java.util.Map;

import static pe.upao.alquiler.http.Enrutador.Acceso.AUTENTICADO;
import static pe.upao.alquiler.http.Enrutador.Acceso.PUBLICO;

/** Rutas /api/auth (HU-01, HU-02). */
public class AuthControlador {

    private final AuthServicio servicio;

    public AuthControlador(AuthServicio servicio) {
        this.servicio = servicio;
    }

    public void registrar(Enrutador r) {
        r.post("/api/auth/registro", PUBLICO, this::registro);
        r.post("/api/auth/login", PUBLICO, this::login);
        r.post("/api/auth/logout", AUTENTICADO, this::logout);
        r.get("/api/auth/yo", AUTENTICADO, this::yo);
    }

    /** HU-01 */
    private Respuesta registro(Solicitud s) {
        Validador v = Validador.de(s.json());
        String nombre = v.texto("nombre", 1, 120, true, "El nombre es obligatorio");
        String correo = v.correo("correo");
        String contrasena = v.textoExacto("contrasena", 8, 72,
                "La contraseña debe tener al menos 8 caracteres",
                "La contraseña debe tener como máximo 72 caracteres");
        v.validar();

        Map<String, Object> usuario = servicio.registrarCliente(nombre, correo, contrasena);
        return Respuesta.creado(Json.obj(
                "mensaje", "¡Registro exitoso! Ya puedes iniciar sesión", // HU-01 criterio 6
                "usuario", usuario));
    }

    /** HU-02 */
    private Respuesta login(Solicitud s) {
        Validador v = Validador.de(s.json());
        String correo = v.correo("correo");
        String contrasena = v.texto("contrasena", 1, 200, true, "Ingresa tu contraseña");
        boolean recordarme = Boolean.TRUE.equals(v.booleano("recordarme", false));
        v.validar();

        return Respuesta.ok(servicio.iniciarSesion(correo, contrasena, recordarme, s.ip(), s.cabecera("User-Agent")));
    }

    private Respuesta logout(Solicitud s) {
        servicio.cerrarSesion(s.usuario().sesionId());
        return Respuesta.sinContenido();
    }

    private Respuesta yo(Solicitud s) {
        return Respuesta.ok(Json.obj("usuario", s.usuario()));
    }
}
