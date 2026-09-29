package alquiler;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import alquiler.json.Json;

import java.util.List;
import java.util.Map;

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

    // ------------------------------------------------------------ Validaciones
    @Test
    @DisplayName("Validación: el nombre de categoría admite letras, números y guiones; recorta y colapsa espacios")
    void nombreCategoriaValido() {
        Resp r = post("/api/admin/categorias", Json.obj("nombre", "  Grúas   torre-2 ", "descripcion", "  Para obras altas "), token);
        assertEquals(201, r.estado(), String.valueOf(r.cuerpo()));
        assertEquals("Grúas torre-2", r.json().get("nombre"));
        assertEquals("Para obras altas", r.json().get("descripcion"));
    }

    @Test
    @DisplayName("Validación: el nombre de categoría rechaza símbolos, HTML, sin letras y largos fuera de 2–80")
    void nombreCategoriaInvalido() {
        Map<String, String> casos = Map.of(
                "A", "El nombre debe tener al menos 2 caracteres",
                "a".repeat(81), "El nombre debe tener como máximo 80 caracteres",
                "123", "El nombre debe contener al menos una letra",
                "Grúas & más", "El nombre solo puede contener letras, números, espacios y guiones",
                "   ", "El nombre es obligatorio",
                "<b>Grúas</b>", "El nombre no puede contener los signos < ni >");
        casos.forEach((malo, mensaje) -> {
            Resp r = post("/api/admin/categorias", Json.obj("nombre", malo), token);
            assertEquals(400, r.estado(), malo);
            assertEquals(mensaje, r.errorDe("nombre"), malo);
        });
        Resp larga = post("/api/admin/categorias", Json.obj("nombre", "Grúas", "descripcion", "x".repeat(256)), token);
        assertEquals("La descripción debe tener como máximo 255 caracteres", larga.errorDe("descripcion"));
    }

    @Test
    @DisplayName("Validación: el nombre no se repite aunque cambien mayúsculas o tildes, con un mensaje claro")
    void nombreRepetidoSinTildes() {
        long gruas = crearCategoria("Grúas", true);
        long otra = crearCategoria("Montacargas", true);
        Resp r = post("/api/admin/categorias", Json.obj("nombre", "GRUAS"), token);
        assertEquals(409, r.estado());
        assertEquals("Ya existe una categoría con ese nombre (sin distinguir mayúsculas ni tildes)", r.errorDe("nombre"));
        assertEquals(409, put("/api/admin/categorias/" + otra, Json.obj("nombre", "gruas"), token).estado());
        // Cambiarle solo las mayúsculas o tildes a la misma categoría sí se permite
        assertEquals(200, put("/api/admin/categorias/" + gruas, Json.obj("nombre", "GRÚAS"), token).estado());
    }

    @Test
    @DisplayName("Validación: una categoría antigua con nombre fuera de las reglas se puede seguir editando sin renombrarla")
    void categoriaAntiguaSeEdita() {
        long id = crearCategoria("Cat. #1 & Co", true);
        Resp r = put("/api/admin/categorias/" + id, Json.obj("descripcion", "Nueva descripción"), token);
        assertEquals(200, r.estado(), String.valueOf(r.cuerpo()));
        assertEquals("Cat. #1 & Co", r.json().get("nombre"));
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
                INSERT INTO maquinas (categoria_id, nombre, marca, modelo, tarifa_horaria, ubicacion)
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
