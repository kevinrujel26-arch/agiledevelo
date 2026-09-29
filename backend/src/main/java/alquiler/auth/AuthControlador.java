package alquiler.auth;

import alquiler.http.Respuesta;
import alquiler.http.Solicitud;
import alquiler.json.Json;
import alquiler.util.Validador;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/** Rutas /api/auth (HU-01, HU-02). */
@RestController
public class AuthControlador {

    private final AuthServicio servicio;

    public AuthControlador(AuthServicio servicio) {
        this.servicio = servicio;
    }

    /** HU-01 */
    @PostMapping("/api/auth/registro")
    public ResponseEntity<Object> registro(HttpServletRequest peticion) {
        Solicitud s = Solicitud.de(peticion);
        Validador v = Validador.de(s.json());
        String nombre = v.nombrePersona("nombre");
        String correo = v.correo("correo");
        String telefono = v.celular("telefono", true);
        String contrasena = v.contrasenaNueva("contrasena");
        v.validar();

        Map<String, Object> usuario = servicio.registrarCliente(nombre, correo, telefono, contrasena);
        return Respuesta.creado(Json.obj(
                "mensaje", "¡Registro exitoso! Ya puedes iniciar sesión", // HU-01 criterio 6
                "usuario", usuario));
    }

    /** HU-02 */
    @PostMapping("/api/auth/login")
    public ResponseEntity<Object> login(HttpServletRequest peticion) {
        Solicitud s = Solicitud.de(peticion);
        Validador v = Validador.de(s.json());
        String correo = v.correo("correo");
        String contrasena = v.texto("contrasena", 1, 200, true, "Ingresa tu contraseña");
        boolean recordarme = Boolean.TRUE.equals(v.booleano("recordarme", false));
        v.validar();

        return Respuesta.ok(servicio.iniciarSesion(correo, contrasena, recordarme, s.ip(), s.cabecera("User-Agent")));
    }

    @PostMapping("/api/auth/logout")
    public ResponseEntity<Object> logout(HttpServletRequest peticion) {
        Solicitud s = Solicitud.de(peticion);
        servicio.cerrarSesion(s.usuario().sesionId());
        return Respuesta.sinContenido();
    }

    @GetMapping("/api/auth/yo")
    public ResponseEntity<Object> yo(HttpServletRequest peticion) {
        Solicitud s = Solicitud.de(peticion);
        return Respuesta.ok(Json.obj("usuario", s.usuario()));
    }
}
