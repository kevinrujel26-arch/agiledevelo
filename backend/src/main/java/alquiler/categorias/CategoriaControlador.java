package alquiler.categorias;

import alquiler.http.Enrutador;
import alquiler.http.Respuesta;
import alquiler.http.Solicitud;
import alquiler.json.Json;
import alquiler.util.ErrorApp;
import alquiler.util.Validador;

import java.util.Map;

import static alquiler.http.Enrutador.Acceso.ADMINISTRADOR;
import static alquiler.http.Enrutador.Acceso.PUBLICO;

/** Rutas /api/categorias (público) y /api/admin/categorias (HU-14). */
public class CategoriaControlador {

    private final CategoriaServicio servicio;

    public CategoriaControlador(CategoriaServicio servicio) {
        this.servicio = servicio;
    }

    public void registrar(Enrutador r) {
        r.get("/api/categorias", PUBLICO, s -> Respuesta.ok(Json.obj("datos", servicio.listarActivas())));

        r.get("/api/admin/categorias", ADMINISTRADOR, s -> Respuesta.ok(Json.obj("datos", servicio.listarTodas())));
        r.post("/api/admin/categorias", ADMINISTRADOR, this::crear);
        r.put("/api/admin/categorias/{id}", ADMINISTRADOR, this::actualizar);
        r.patch("/api/admin/categorias/{id}/estado", ADMINISTRADOR, this::cambiarEstado);
        r.delete("/api/admin/categorias/{id}", ADMINISTRADOR, this::eliminar);
    }

    private Respuesta crear(Solicitud s) {
        Validador v = Validador.de(s.json());
        String nombre = v.texto("nombre", 1, 80, true, "El nombre es obligatorio");
        String descripcion = v.texto("descripcion", 0, 255, false, null);
        v.validar();
        return Respuesta.creado(servicio.crear(nombre, descripcion));
    }

    private Respuesta actualizar(Solicitud s) {
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

    private Respuesta cambiarEstado(Solicitud s) {
        long id = s.id("id");
        Map<String, Object> cuerpo = s.json();
        if (!(cuerpo.get("activa") instanceof Boolean activa)) {
            throw new ErrorApp(400, "activa: Debe ser true o false");
        }
        return Respuesta.ok(servicio.cambiarEstado(id, activa));
    }

    private Respuesta eliminar(Solicitud s) {
        servicio.eliminar(s.id("id"));
        return Respuesta.sinContenido();
    }
}
