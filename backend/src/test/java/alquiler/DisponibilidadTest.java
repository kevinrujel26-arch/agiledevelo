package alquiler;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import alquiler.json.Json;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Pruebas de aceptación: HU-09 Gestionar disponibilidad. */
class DisponibilidadTest extends PruebaBase {

    private String token;
    private long maquinaId;

    @BeforeEach
    void preparar() {
        token = tokenAdmin();
        long categoriaId = crearCategoria("Excavadoras", true);
        maquinaId = bd().uno("""
                INSERT INTO maquinas (categoria_id, nombre, marca, modelo, tarifa_horaria, ubicacion, estado, publicada_en)
                VALUES (?, 'Excavadora', 'CAT', '320', 1000, 'Trujillo', 'PUBLICADA', now()) RETURNING id""",
                categoriaId).entero("id");
    }

    /** Fecha 'AAAA-MM-DD' dentro de N días (en Lima). */
    private static String dentroDe(int dias) {
        return LocalDate.now(ZoneId.of("America/Lima")).plusDays(dias).toString();
    }

    private static Map<String, Object> rango(int desde, int hasta) {
        return Json.obj("fechaInicio", dentroDe(desde), "fechaFin", dentroDe(hasta));
    }

    private Resp bloquear(List<Map<String, Object>> rangos) {
        return post("/api/admin/maquinas/" + maquinaId + "/bloqueos", Json.obj("rangos", rangos, "motivo", "Mantenimiento"), token);
    }

    // ------------------------------------------------------------ Validaciones
    private Resp bloquearConMotivo(Object motivo, List<Map<String, Object>> rangos) {
        return post("/api/admin/maquinas/" + maquinaId + "/bloqueos", Json.obj("rangos", rangos, "motivo", motivo), token);
    }

    @Test
    @DisplayName("Validación: no se bloquean fechas pasadas ni a más de 5 años de hoy (el límite sí se admite)")
    void fechasFueraDeRango() {
        Resp pasada = bloquear(List.of(rango(-1, 0)));
        assertEquals(400, pasada.estado());
        assertEquals("No se pueden bloquear fechas pasadas", pasada.errorDe("rangos.0.fechaInicio"));

        LocalDate hoy = LocalDate.now(ZoneId.of("America/Lima"));
        String limite = hoy.plusYears(5).toString();
        Resp lejana = bloquear(List.of(Json.obj("fechaInicio", hoy.plusYears(5).plusDays(1).toString(), "fechaFin", "9999-12-31")));
        assertEquals(400, lejana.estado());
        assertEquals("No se pueden bloquear fechas a más de 5 años de hoy", lejana.errorDe("rangos.0.fechaInicio"));
        assertEquals("No se pueden bloquear fechas a más de 5 años de hoy", lejana.errorDe("rangos.0.fechaFin"));

        Resp finLejano = bloquear(List.of(Json.obj("fechaInicio", dentroDe(1), "fechaFin", hoy.plusYears(6).toString())));
        assertEquals("No se pueden bloquear fechas a más de 5 años de hoy", finLejano.errorDe("rangos.0.fechaFin"));

        assertEquals(201, bloquear(List.of(Json.obj("fechaInicio", limite, "fechaFin", limite))).estado());
    }

    @Test
    @DisplayName("Validación: la fecha fin no es anterior al inicio y ambas son obligatorias, con el error en su campo")
    void ordenYObligatorias() {
        Resp r = bloquear(List.of(rango(5, 2)));
        assertEquals("La fecha de fin no puede ser anterior a la de inicio", r.errorDe("rangos.0.fechaFin"));
        Resp sinFin = bloquear(List.of(Json.obj("fechaInicio", dentroDe(1))));
        assertEquals("Elige la fecha de fin", sinFin.errorDe("rangos.0.fechaFin"));
        Resp formato = bloquear(List.of(Json.obj("fechaInicio", "20000-01-01", "fechaFin", dentroDe(1))));
        assertEquals("Fecha inválida, usa el formato AAAA-MM-DD", formato.errorDe("rangos.0.fechaInicio"));
        assertEquals(201, bloquear(List.of(rango(2, 2))).estado());
    }

    @Test
    @DisplayName("Validación: como máximo 20 rangos por vez")
    void maximoVeinteRangos() {
        List<Map<String, Object>> veintiuno = new java.util.ArrayList<>();
        for (int i = 0; i < 21; i++) veintiuno.add(rango(1 + i * 2, 1 + i * 2));
        Resp r = bloquear(veintiuno);
        assertEquals(400, r.estado());
        assertEquals("Puedes bloquear como máximo 20 rangos a la vez", r.errorDe("rangos"));
        assertEquals(201, bloquear(veintiuno.subList(0, 20)).estado());
    }

    @Test
    @DisplayName("Validación: el motivo tiene hasta 160 caracteres, sin HTML, y se recorta")
    void motivoReglas() {
        Resp largo = bloquearConMotivo("m".repeat(161), List.of(rango(1, 1)));
        assertEquals("El motivo debe tener como máximo 160 caracteres", largo.errorDe("motivo"));
        Resp html = bloquearConMotivo("<script>x</script>", List.of(rango(1, 1)));
        assertEquals("El motivo no puede contener los signos < ni >", html.errorDe("motivo"));
        Resp ok = bloquearConMotivo("  Mantenimiento   preventivo ", List.of(rango(1, 1)));
        assertEquals(201, ok.estado(), String.valueOf(ok.cuerpo()));
        assertEquals("Mantenimiento preventivo", ok.lista("datos").get(0).get("motivo"));
    }

    @Test
    @DisplayName("HU-09: bloquea uno o varios rangos de fechas a la vez")
    void variosRangos() {
        Resp r = bloquear(List.of(rango(1, 3), rango(10, 10)));
        assertEquals(201, r.estado());
        assertEquals(2, r.lista("datos").size());
    }

    @Test
    @DisplayName("HU-09: las fechas bloqueadas aparecen ocupadas en el calendario público de inmediato")
    void apareceEnCalendario() {
        assertEquals(201, bloquear(List.of(rango(5, 6))).estado());
        Resp r = get("/api/maquinas/" + maquinaId + "/disponibilidad", null);
        assertEquals(200, r.estado());
        List<Map<String, Object>> ocupados = r.lista("ocupados");
        assertEquals(1, ocupados.size());
        assertEquals(dentroDe(5), ocupados.get(0).get("fechaInicio"));
        assertEquals(dentroDe(6), ocupados.get(0).get("fechaFin"));
        assertEquals("BLOQUEO", ocupados.get(0).get("tipo"));
    }

    @Test
    @DisplayName("HU-09: no se puede bloquear una fecha con una reserva ya pagada")
    void noSobreReservaPagada() {
        Usuario cliente = crearCliente();
        bd().ejecutar("""
                INSERT INTO reservas (maquina_id, cliente_id, fecha_inicio, fecha_fin, tarifa_horaria, monto_total, estado)
                VALUES (?, ?, ?::date, (?::date + interval '1 day'), 1000, 3000, 'PAGADA')""", maquinaId, cliente.id(), dentroDe(7), dentroDe(9));

        Resp r = bloquear(List.of(rango(9, 12)));
        assertEquals(409, r.estado());
        assertTrue(r.error().contains("reserva pagada"));
        // Todo o nada: no se guardó ningún bloqueo
        assertEquals(0L, bd().uno("SELECT count(*) AS total FROM bloqueos_disponibilidad").enteroOCero("total"));
    }

    @Test
    @DisplayName("HU-09: no permite rangos superpuestos con un bloqueo existente")
    void noSuperpuestos() {
        assertEquals(201, bloquear(List.of(rango(1, 5))).estado());
        assertEquals(409, bloquear(List.of(rango(5, 8))).estado());
    }

    @Test
    @DisplayName("HU-09: la fecha fin no puede ser anterior al inicio ni se pueden bloquear fechas pasadas")
    void validaFechas() {
        assertEquals(400, bloquear(List.of(rango(5, 2))).estado());
        assertEquals(400, bloquear(List.of(rango(-3, 2))).estado());
    }

    @Test
    @DisplayName("HU-09: se pueden desbloquear fechas bloqueadas antes")
    void desbloquear() {
        Resp r = bloquear(List.of(rango(1, 2)));
        long bloqueoId = numero(r.lista("datos").get(0).get("id"));
        assertEquals(204, delete("/api/admin/maquinas/" + maquinaId + "/bloqueos/" + bloqueoId, token).estado());
        assertEquals(0, get("/api/maquinas/" + maquinaId + "/disponibilidad", null).lista("ocupados").size());
    }
}
