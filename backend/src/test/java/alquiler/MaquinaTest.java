package alquiler;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import alquiler.json.Json;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Pruebas de aceptación: HU-08 Registrar y publicar máquina · HU-03 Ver catálogo. */
class MaquinaTest extends PruebaBase {

    private String token;
    private long categoriaId;

    @BeforeEach
    void preparar() {
        token = tokenAdmin();
        categoriaId = crearCategoria("Excavadoras", true);
    }

    private long crearMaquina(Map<String, Object> datos) {
        Resp r = post("/api/admin/maquinas", datos, token);
        assertEquals(201, r.estado(), "crear máquina: " + r.cuerpo());
        return numero(r.json().get("id"));
    }

    private long crearPublicada(String nombre) {
        Map<String, Object> datos = datosMaquina(categoriaId);
        datos.put("nombre", nombre);
        long id = crearMaquina(datos);
        assertEquals(201, subir("/api/admin/maquinas/" + id + "/fotos", token, new Archivo("foto.png", "image/png", PNG)).estado());
        assertEquals(200, post("/api/admin/maquinas/" + id + "/publicar", null, token).estado());
        return id;
    }

    private static List<Long> ids(Resp catalogo) {
        return catalogo.lista("datos").stream().map(m -> numero(m.get("id"))).toList();
    }

    // ------------------------------------------------------------ HU-08
    @Test
    @DisplayName("HU-08: pide categoría, marca, modelo, tarifa y ubicación")
    void camposObligatorios() {
        Resp r = post("/api/admin/maquinas", Json.obj("nombre", "Sin datos"), token);
        assertEquals(400, r.estado());
        List<Object> campos = r.lista("detalles").stream().map(d -> d.get("campo")).map(Object.class::cast).toList();
        for (String campo : new String[]{"categoriaId", "marca", "modelo", "tarifaDiaria", "ubicacion"}) {
            assertTrue(campos.contains(campo), "falta el error de " + campo);
        }
    }

    @Test
    @DisplayName("HU-08: se guarda como borrador y no aparece en el catálogo")
    void borrador() {
        long id = crearMaquina(datosMaquina(categoriaId));
        assertEquals("BORRADOR", get("/api/admin/maquinas/" + id, token).json().get("estado"));
        assertEquals(0, get("/api/maquinas", null).lista("datos").size());
        assertEquals(404, get("/api/maquinas/" + id, null).estado());
    }

    @Test
    @DisplayName("HU-08: sube fotos JPG/PNG y la primera queda como principal")
    void subeFotos() {
        long id = crearMaquina(datosMaquina(categoriaId));
        Resp r = subir("/api/admin/maquinas/" + id + "/fotos", token,
                new Archivo("a.jpg", "image/jpeg", JPG), new Archivo("b.png", "image/png", PNG));
        assertEquals(201, r.estado());
        List<Map<String, Object>> fotos = r.lista("fotos");
        assertEquals(2, fotos.size());
        assertEquals(1, fotos.stream().filter(f -> Boolean.TRUE.equals(f.get("esPrincipal"))).count());
    }

    @Test
    @DisplayName("HU-08: rechaza formatos que no son JPG o PNG")
    void rechazaFormatos() {
        long id = crearMaquina(datosMaquina(categoriaId));
        Resp gif = subir("/api/admin/maquinas/" + id + "/fotos", token,
                new Archivo("a.gif", "image/gif", "GIF89a".getBytes(StandardCharsets.US_ASCII)));
        assertEquals(400, gif.estado());
        // Archivo que dice ser PNG pero no lo es
        Resp falso = subir("/api/admin/maquinas/" + id + "/fotos", token,
                new Archivo("falso.png", "image/png", "no soy imagen".getBytes(StandardCharsets.US_ASCII)));
        assertEquals(400, falso.estado());
    }

    @Test
    @DisplayName("HU-08: permite como máximo 5 fotos")
    void maximoCincoFotos() {
        long id = crearMaquina(datosMaquina(categoriaId));
        Archivo[] cinco = new Archivo[5];
        for (int i = 0; i < 5; i++) cinco[i] = new Archivo("f" + i + ".png", "image/png", PNG);
        assertEquals(201, subir("/api/admin/maquinas/" + id + "/fotos", token, cinco).estado());
        Resp sexta = subir("/api/admin/maquinas/" + id + "/fotos", token, new Archivo("f6.png", "image/png", PNG));
        assertEquals(400, sexta.estado());
        assertTrue(sexta.error().contains("máximo de 5"));
    }

    @Test
    @DisplayName("HU-08: se puede cambiar la foto principal")
    void cambiaPrincipal() {
        long id = crearMaquina(datosMaquina(categoriaId));
        Resp r = subir("/api/admin/maquinas/" + id + "/fotos", token,
                new Archivo("a.png", "image/png", PNG), new Archivo("b.png", "image/png", PNG));
        long otra = r.lista("fotos").stream().filter(f -> !Boolean.TRUE.equals(f.get("esPrincipal")))
                .map(f -> numero(f.get("id"))).findFirst().orElseThrow();
        Resp cambio = patch("/api/admin/maquinas/" + id + "/fotos/" + otra + "/principal", null, token);
        long principal = cambio.lista("fotos").stream().filter(f -> Boolean.TRUE.equals(f.get("esPrincipal")))
                .map(f -> numero(f.get("id"))).findFirst().orElseThrow();
        assertEquals(otra, principal);
    }

    @Test
    @DisplayName("HU-08: no se publica sin foto principal")
    void noPublicaSinFoto() {
        long id = crearMaquina(datosMaquina(categoriaId));
        assertEquals(409, post("/api/admin/maquinas/" + id + "/publicar", null, token).estado());
    }

    @Test
    @DisplayName("HU-08: al publicarla aparece de inmediato en el catálogo")
    void publicaEnCatalogo() {
        long id = crearPublicada("Excavadora 320");
        assertTrue(ids(get("/api/maquinas", null)).contains(id));
    }

    @Test
    @DisplayName("HU-08: se puede editar después de publicada")
    void editaPublicada() {
        long id = crearPublicada("Excavadora 320");
        Resp r = put("/api/admin/maquinas/" + id, Json.obj("tarifaDiaria", 1600.5), token);
        assertEquals(200, r.estado());
        assertEquals(1600.5, ((Number) r.json().get("tarifaDiaria")).doubleValue(), 0.001);
        assertEquals("PUBLICADA", r.json().get("estado"));
    }

    @Test
    @DisplayName("HU-08: se retira del catálogo sin borrar su historial de reservas")
    void retiraSinBorrarHistorial() {
        long id = crearPublicada("Excavadora 320");
        long adminId = bd().uno("SELECT id FROM usuarios WHERE rol = 'ADMINISTRADOR'").entero("id");
        bd().ejecutar("""
                INSERT INTO reservas (maquina_id, cliente_id, fecha_inicio, fecha_fin, tarifa_diaria, monto_total, estado)
                VALUES (?, ?, '2025-01-10', '2025-01-12', 1450, 4350, 'FINALIZADA')""", id, adminId);

        assertEquals(200, post("/api/admin/maquinas/" + id + "/retirar", null, token).estado());
        assertEquals(0, get("/api/maquinas", null).lista("datos").size());
        assertEquals(1L, bd().uno("SELECT count(*) AS total FROM reservas WHERE maquina_id = ?", id).enteroOCero("total"));
    }

    // ------------------------------------------------------------ HU-03
    @Test
    @DisplayName("HU-03: visible sin sesión, con foto, nombre, categoría y tarifa")
    void catalogoPublico() {
        crearPublicada("Excavadora 320");
        Resp r = get("/api/maquinas", null);
        assertEquals(200, r.estado());
        Map<String, Object> tarjeta = r.lista("datos").get(0);
        assertEquals("Excavadora 320", tarjeta.get("nombre"));
        assertEquals("Excavadoras", ((Map<?, ?>) tarjeta.get("categoria")).get("nombre"));
        assertEquals(1450L, numero(tarjeta.get("tarifaDiaria")));
        assertTrue(((String) tarjeta.get("fotoPrincipal")).matches("^/uploads/maquinas/.+\\.png$"));
    }

    @Test
    @DisplayName("HU-03: el catálogo está paginado")
    void paginado() {
        for (int i = 0; i < 5; i++) crearPublicada("Máquina " + i);
        Resp r = get("/api/maquinas?pagina=2&tamanio=2", null);
        assertEquals(2, r.lista("datos").size());
        Map<String, Object> p = r.objeto("paginacion");
        assertEquals(2L, numero(p.get("pagina")));
        assertEquals(5L, numero(p.get("total")));
        assertEquals(3L, numero(p.get("totalPaginas")));
    }

    @Test
    @DisplayName("HU-03/HU-04: el detalle de una máquina publicada se abre por su URL")
    void detallePorUrl() {
        long id = crearPublicada("Excavadora 320");
        Resp r = get("/api/maquinas/" + id, null);
        assertEquals(200, r.estado());
        assertEquals(Map.of("Potencia", "146 HP"), r.json().get("especificaciones"));
        assertEquals(1, r.lista("fotos").size());
    }

    @Test
    @DisplayName("HU-08: las fotos subidas se pueden descargar desde /uploads")
    void sirveFotos() {
        long id = crearPublicada("Excavadora 320");
        String ruta = (String) get("/api/maquinas/" + id, null).lista("fotos").get(0).get("url");
        Descarga d = descargar(ruta);
        assertEquals(200, d.estado());
        assertEquals("image/png", d.tipo());
        assertEquals(PNG.length, d.datos().length);
        assertEquals(404, descargar("/uploads/../pom.xml").estado());
    }
}
