package alquiler.categorias;

import alquiler.http.Respuesta;
import alquiler.http.Solicitud;
import alquiler.json.Json;
import alquiler.util.ErrorApp;
import alquiler.util.Validador;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.Map;


/** Rutas /api/categorias (público) y /api/admin/categorias (HU-14). */
@RestController
public class CategoriaControlador {

    private final CategoriaServicio servicio;

    public CategoriaControlador(CategoriaServicio servicio) {
        this.servicio = servicio;
    }

    @GetMapping("/api/categorias")
    public ResponseEntity<Object> listarActivas() {
        return Respuesta.ok(Json.obj("datos", servicio.listarActivas()));
    }

    @GetMapping("/api/admin/categorias")
    public ResponseEntity<Object> listarTodas() {
        return Respuesta.ok(Json.obj("datos", servicio.listarTodas()));
    }

    @PostMapping("/api/admin/categorias")
    public ResponseEntity<Object> crear(HttpServletRequest peticion) {
        Solicitud s = Solicitud.de(peticion);
        Validador v = Validador.de(s.json());
        String nombre = v.texto("nombre", 1, 80, true, "El nombre es obligatorio");
        String descripcion = v.texto("descripcion", 0, 255, false, null);
        v.validar();
        return Respuesta.creado(servicio.crear(nombre, descripcion));
    }

    @PutMapping("/api/admin/categorias/{id}")
    public ResponseEntity<Object> actualizar(HttpServletRequest peticion) {
        Solicitud s = Solicitud.de(peticion);
        long id = s.id("id");
        Map<String, Object> cuerpo = s.json();
        if (!cuerpo.containsKey("nombre") && !cuerpo.containsKey("descripcion")) {
            throw ErrorApp.solicitudInvalida("Envía al menos un campo para actualizar");
        }
        Validador v = Validador.de(cuerpo);
        String nombre = v.tiene("nombre") ? v.texto("nombre", 1, 80, true, "El nombre es obligatorio") : null;
        String descripcion = v.texto("descripcion", 0, 255, false, null);
        v.validar();
        return Respuesta.ok(servicio.actualizar(id, nombre, v.tiene("descripcion"), descripcion));
    }

    @PatchMapping("/api/admin/categorias/{id}/estado")
    public ResponseEntity<Object> cambiarEstado(HttpServletRequest peticion) {
        Solicitud s = Solicitud.de(peticion);
        long id = s.id("id");
        Map<String, Object> cuerpo = s.json();
        if (!(cuerpo.get("activa") instanceof Boolean activa)) {
            throw new ErrorApp(400, "activa: Debe ser true o false");
        }
        return Respuesta.ok(servicio.cambiarEstado(id, activa));
    }

    @DeleteMapping("/api/admin/categorias/{id}")
    public ResponseEntity<Object> eliminar(HttpServletRequest peticion) {
        Solicitud s = Solicitud.de(peticion);
        servicio.eliminar(s.id("id"));
        return Respuesta.sinContenido();
    }
}
