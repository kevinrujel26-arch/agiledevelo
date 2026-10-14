package alquiler;

import alquiler.json.Json;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Combustible por hora: se suma al total de la reserva")
class CombustibleTest extends PruebaBase {

    private String tokenCliente;
    private String tokenAdmin;
    private long categoriaId;
    private String manana;

    @BeforeEach
    void preparar() {
        Usuario clienteU = crearCliente();
        bd().ejecutar("UPDATE usuarios SET telefono = ? WHERE id = ?", "987654321", clienteU.id());
        tokenCliente = token(clienteU, false);
        tokenAdmin = tokenAdmin();
        categoriaId = crearCategoria("Excavadoras", true);
        manana = LocalDate.now(ZoneId.of("America/Lima")).plusDays(1).toString();
    }

    private long maquinaPublicada(int tarifa, int combustible) {
        return bd().uno("""
                INSERT INTO maquinas (categoria_id, nombre, marca, modelo, tarifa_horaria, combustible_por_hora,
                                      ubicacion, estado, publicada_en)
                VALUES (?, 'Excavadora', 'CAT', '320', ?, ?, 'Trujillo', 'PUBLICADA', now()) RETURNING id""",
                categoriaId, tarifa, combustible).entero("id");
    }

    private Resp reservar(long maquinaId, int horas) {
        return post("/api/reservas", Json.obj(
                "maquinaId", maquinaId,
                "fecha", manana,
                "horaInicio", 9,
                "horas", horas,
                "nombreSolicitante", "Juan Perez Garcia",
                "ruc", "20000000109",
                "telefono", "987654321",
                "email", "juan@example.com",
                "ubicacionObra", "Av. Siempre Viva 123",
                "detalle", "Obra de prueba",
                "aceptaPolitica", true), tokenCliente);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> datos(Resp resp) {
        return (Map<String, Object>) ((Map<String, Object>) resp.cuerpo()).get("datos");
    }

    private static BigDecimal num(Object o) {
        return new BigDecimal(String.valueOf(o));
    }

    private Map<String, Object> cuerpoMaquina() {
        return Json.obj(
                "categoriaId", categoriaId,
                "nombre", "Excavadora Prueba",
                "marca", "CAT",
                "modelo", "320",
                "tarifaHoraria", 250,
                "ubicacion", "Trujillo");
    }

    @Test
    @DisplayName("Con combustible: total = (tarifa + combustible) x horas")
    void reservaConCombustibleSumaAlTotal() {
        long id = maquinaPublicada(250, 40);
        Resp resp = reservar(id, 3);
        assertEquals(201, resp.estado());
        Map<String, Object> d = datos(resp);
        assertEquals(0, new BigDecimal("870").compareTo(num(d.get("total"))));
        assertEquals(0, new BigDecimal("120").compareTo(num(d.get("combustibleTotal"))));
        var fila = bd().uno("SELECT tarifa_horaria, monto_total, combustible_total FROM reservas WHERE codigo = ?",
                d.get("codigo"));
        assertEquals(0, new BigDecimal("250").compareTo(fila.decimal("tarifa_horaria")));
        assertEquals(0, new BigDecimal("870").compareTo(fila.decimal("monto_total")));
        assertEquals(0, new BigDecimal("120").compareTo(fila.decimal("combustible_total")));
    }

    @Test
    @DisplayName("Sin combustible: el total es tarifa x horas, como antes")
    void reservaSinCombustibleMantieneElTotal() {
        long id = maquinaPublicada(100, 0);
        Resp resp = reservar(id, 3);
        assertEquals(201, resp.estado());
        Map<String, Object> d = datos(resp);
        assertEquals(0, new BigDecimal("300").compareTo(num(d.get("total"))));
        assertEquals(0, BigDecimal.ZERO.compareTo(num(d.get("combustibleTotal"))));
    }

    @Test
    @DisplayName("Alta de maquina con combustible negativo: 400")
    void altaConCombustibleNegativoDa400() {
        Map<String, Object> cuerpo = cuerpoMaquina();
        cuerpo.put("combustiblePorHora", -5);
        Resp resp = post("/api/admin/maquinas", cuerpo, tokenAdmin);
        assertEquals(400, resp.estado());
    }

    @Test
    @DisplayName("Alta de maquina guarda el combustible por hora")
    void altaGuardaElCombustible() {
        Map<String, Object> cuerpo = cuerpoMaquina();
        cuerpo.put("combustiblePorHora", new BigDecimal("35.50"));
        Resp resp = post("/api/admin/maquinas", cuerpo, tokenAdmin);
        assertEquals(201, resp.estado());
        var fila = bd().uno("SELECT combustible_por_hora FROM maquinas WHERE nombre = ?", "Excavadora Prueba");
        assertEquals(0, new BigDecimal("35.50").compareTo(fila.decimal("combustible_por_hora")));
    }

    @Test
    @DisplayName("Alta de maquina sin combustible: queda en 0")
    void altaSinCombustibleQuedaEnCero() {
        Resp resp = post("/api/admin/maquinas", cuerpoMaquina(), tokenAdmin);
        assertEquals(201, resp.estado());
        var fila = bd().uno("SELECT combustible_por_hora FROM maquinas WHERE nombre = ?", "Excavadora Prueba");
        assertEquals(0, BigDecimal.ZERO.compareTo(fila.decimal("combustible_por_hora")));
    }

    @Test
    @DisplayName("Editar maquina: se puede cambiar el combustible por hora")
    void editarCambiaElCombustible() {
        long id = maquinaPublicada(100, 0);
        Resp resp = put("/api/admin/maquinas/" + id, Json.obj("combustiblePorHora", 55), tokenAdmin);
        assertEquals(200, resp.estado());
        var fila = bd().uno("SELECT combustible_por_hora FROM maquinas WHERE id = ?", id);
        assertEquals(0, new BigDecimal("55").compareTo(fila.decimal("combustible_por_hora")));
    }
}