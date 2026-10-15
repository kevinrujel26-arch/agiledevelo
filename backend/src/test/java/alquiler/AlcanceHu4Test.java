package alquiler;

import alquiler.seguridad.FiltroAlcance;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Milestone 1: alcance congelado hasta la HU4 (ALCANCE_HU=4).
 * Comprueba que HU1-HU4 siguen funcionando y que HU5 (admin) y HU6 (reservas) responden 404.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"APP_ENV=test", "MIGRAR_AL_INICIAR=false", "ALCANCE_HU=4"})
class AlcanceHu4Test extends PruebaBase {

    private long maquinaId;

    @BeforeEach
    void preparar() {
        long categoriaId = crearCategoria("Excavadoras", true);
        maquinaId = bd().uno("""
                INSERT INTO maquinas (categoria_id, nombre, marca, modelo, tarifa_horaria, ubicacion, estado, publicada_en)
                VALUES (?, 'Excavadora', 'CAT', '320', 1000, 'Trujillo', 'PUBLICADA', now()) RETURNING id""",
                categoriaId).entero("id");
    }

    @Test
    @DisplayName("HU1-HU4 siguen activas: catálogo, categorías, detalle, calendario y login (cliente y admin)")
    void hastaHu4Funciona() {
        assertEquals(200, get("/api/categorias", null).estado());
        Resp catalogo = get("/api/maquinas", null);
        assertEquals(200, catalogo.estado());
        assertEquals(1, catalogo.lista("datos").size());
        assertEquals(200, get("/api/maquinas/" + maquinaId, null).estado());
        assertEquals(200, get("/api/maquinas/" + maquinaId + "/disponibilidad", null).estado());

        assertNotNull(token(crearCliente(), false));
        assertNotNull(tokenAdmin());
    }

    @Test
    @DisplayName("HU5 apagada: toda la API de administración responde 404, incluso con token de administrador")
    void hu5Apagada() {
        String admin = tokenAdmin();
        assertEquals(404, get("/api/admin/maquinas", admin).estado());
        assertEquals(404, get("/api/admin/maquinas/" + maquinaId, admin).estado());
        assertEquals(404, post("/api/admin/maquinas", datosMaquina(1), admin).estado());
        assertEquals(404, post("/api/admin/maquinas/" + maquinaId + "/publicar", null, admin).estado());
        assertEquals(404, get("/api/admin/categorias", admin).estado());
        assertEquals(404, get("/api/admin/maquinas/" + maquinaId + "/bloqueos", admin).estado());
        // y sin token tampoco revela que la ruta existe
        assertEquals(404, get("/api/admin/maquinas", null).estado());
    }

    @Test
    @DisplayName("HU6 apagada: reservar y consultar horas libres responden 404")
    void hu6Apagada() {
        String cliente = token(crearCliente(), false);
        assertEquals(404, post("/api/reservas", java.util.Map.of("maquinaId", maquinaId), cliente).estado());
        assertEquals(404, get("/api/maquinas/" + maquinaId + "/disponibilidad/horas?fecha=2030-01-01", null).estado());
        assertEquals(404, get("/api/admin/reservas", tokenAdmin()).estado());
    }

    @Test
    @DisplayName("Reglas del filtro: 4 apaga HU5 y HU6, 5 solo apaga HU6, 6 no apaga nada")
    void reglasPorAlcance() {
        String[] hu5 = {"/api/admin/maquinas", "/api/admin/categorias", "/api/admin/maquinas/3/fotos"};
        String[] hu6 = {"/api/reservas", "/api/maquinas/3/disponibilidad/horas", "/api/admin/reservas", "/api/admin/reservas/3/cancelar"};
        String[] hu1a4 = {"/api/maquinas", "/api/maquinas/3", "/api/maquinas/3/disponibilidad", "/api/categorias",
                "/api/auth/login", "/api/auth/registro", "/api/auth/yo", "/api/salud"};

        for (String r : hu1a4) {
            for (int alcance = 4; alcance <= 6; alcance++) assertFalse(FiltroAlcance.estaApagada(alcance, r), r + " @" + alcance);
        }
        for (String r : hu5) {
            assertTrue(FiltroAlcance.estaApagada(4, r), r);
            assertFalse(FiltroAlcance.estaApagada(5, r), r);
            assertFalse(FiltroAlcance.estaApagada(6, r), r);
        }
        for (String r : hu6) {
            assertTrue(FiltroAlcance.estaApagada(4, r), r);
            assertTrue(FiltroAlcance.estaApagada(5, r), r);
            assertFalse(FiltroAlcance.estaApagada(6, r), r);
        }
    }
}
