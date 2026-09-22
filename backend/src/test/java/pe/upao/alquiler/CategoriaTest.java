package pe.upao.alquiler;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import pe.upao.alquiler.json.Json;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Pruebas de aceptación: HU-14 Gestionar categorías. */
class CategoriaTest extends PruebaBase {

    private String token;

    @BeforeEach
    void iniciarSesion() {
        token = tokenAdmin();
    }

    @Test
    @DisplayName("HU-14: crea, renombra y desactiva una categoría")
    void crearRenombrarDesactivar() {
        Resp creada = post("/api/admin/categorias", Json.obj("nombre", "Grúas"), token);
        assertEquals(201, creada.estado());
        long id = numero(creada.json().get("id"));

        Resp renombrada = put("/api/admin/categorias/" + id, Json.obj("nombre", "Grúas torre"), token);
        assertEquals("Grúas torre", renombrada.json().get("nombre"));

        Resp desactivada = patch("/api/admin/categorias/" + id + "/estado", Json.obj("activa", false), token);
        assertEquals(false, desactivada.json().get("activa"));
    }

    @Test
    @DisplayName("HU-14: el nombre no puede repetirse (sin distinguir mayúsculas)")
    void nombreUnico() {
        crearCategoria("Excavadoras", true);
        assertEquals(409, post("/api/admin/categorias", Json.obj("nombre", "  EXCAVADORAS "), token).estado());
    }

    @Test
    @DisplayName("HU-14: una categoría desactivada no aparece en el catálogo público")
    void desactivadaNoEsFiltro() {
        crearCategoria("Activa", true);
        crearCategoria("Inactiva", false);
        List<String> nombres = get("/api/categorias", null).lista("datos").stream()
                .map(c -> (String) c.get("nombre")).toList();
        assertEquals(List.of("Activa"), nombres);
    }

    @Test
    @DisplayName("HU-14: con máquinas no se elimina, solo se desactiva")
    void conMaquinasNoSeElimina() {
        long id = crearCategoria("Montacargas", true);
        bd().ejecutar("""
                INSERT INTO maquinas (categoria_id, nombre, marca, modelo, tarifa_diaria, ubicacion)
                VALUES (?, 'M1', 'Toyota', '8FD', 200, 'Lima')""", id);
        Resp r = delete("/api/admin/categorias/" + id, token);
        assertEquals(409, r.estado());
        assertTrue(r.error().toLowerCase().contains("desactívala"));
    }

    @Test
    @DisplayName("HU-14: una categoría sin máquinas sí se puede eliminar")
    void sinMaquinasSeElimina() {
        long id = crearCategoria("Temporal", true);
        assertEquals(204, delete("/api/admin/categorias/" + id, token).estado());
    }
}
