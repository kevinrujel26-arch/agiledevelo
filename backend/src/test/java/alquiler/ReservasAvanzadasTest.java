package alquiler;

import alquiler.json.Json;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("HU-06: reservas — concurrencia, margen y cancelaciones")
class ReservasAvanzadasTest extends PruebaBase {

    private long maquinaId;
    private LocalDate manana;
    private String tokenCliente;

    @BeforeEach
    void prepararMaquina() {
        tokenCliente = token(crearCliente(), false);
        long categoriaId = crearCategoria("Excavadoras", true);
        maquinaId = bd().uno("""
                INSERT INTO maquinas (categoria_id, nombre, marca, modelo, tarifa_horaria, ubicacion, estado, publicada_en)
                VALUES (?, 'Excavadora', 'CAT', '320', 100, 'Trujillo', 'PUBLICADA', now()) RETURNING id""",
                categoriaId).entero("id");
        manana = LocalDate.now(ZoneId.of("America/Lima")).plusDays(1);
    }

    private static Map<String, Object> cuerpo(long maquina, String fecha, int horaInicio, int horas) {
        return Json.obj(
                "maquinaId", maquina,
                "fecha", fecha,
                "horaInicio", horaInicio,
                "horas", horas,
                "nombreSolicitante", "Juan Pérez García",
                "ruc", "20000000109",
                "telefono", "987654321",
                "email", "juan@example.com",
                "ubicacionObra", "Av. Test 123",
                "aceptaPolitica", true);
    }

    @Test
    @DisplayName("Dos clientes reservan el mismo horario a la vez: un 201 y un 409")
    void concurrenciaDosPeticionesSimultaneas() throws Exception {
        for (int ronda = 0; ronda < 3; ronda++) {
            final String fecha = manana.plusDays(ronda).toString();
            final String tokenA = token(crearCliente(), false);
            final String tokenB = token(crearCliente(), false);
            CountDownLatch listos = new CountDownLatch(2);
            CountDownLatch salida = new CountDownLatch(1);
            ExecutorService pool = Executors.newFixedThreadPool(2);
            try {
                List<Future<Integer>> futuros = new ArrayList<>();
                for (String t : List.of(tokenA, tokenB)) {
                    futuros.add(pool.submit(() -> {
                        listos.countDown();
                        salida.await();
                        return post("/api/reservas", cuerpo(maquinaId, fecha, 9, 3), t).estado();
                    }));
                }
                listos.await();
                salida.countDown();
                List<Integer> estados = new ArrayList<>();
                for (Future<Integer> f : futuros) estados.add(f.get(30, TimeUnit.SECONDS));
                Collections.sort(estados);
                assertEquals(List.of(201, 409), estados, "Ronda " + ronda + ": " + estados);
            } finally {
                pool.shutdownNow();
            }
        }
        long total = bd().uno("SELECT count(*) AS n FROM reservas WHERE maquina_id = ? AND estado <> 'CANCELADA'",
                maquinaId).entero("n");
        assertEquals(3L, total);
    }

    @Test
    @DisplayName("Una reserva cancelada libera sus horas y su margen")
    void reservaCanceladaLiberaHorasYMargen() {
        String fecha = manana.toString();
        assertEquals(201, post("/api/reservas", cuerpo(maquinaId, fecha, 9, 3), tokenCliente).estado());

        String tokenOtro = token(crearCliente(), false);
        assertEquals(409, post("/api/reservas", cuerpo(maquinaId, fecha, 9, 3), tokenOtro).estado());
        assertEquals(409, post("/api/reservas", cuerpo(maquinaId, fecha, 12, 1), tokenOtro).estado()); // hora de margen

        bd().ejecutar("UPDATE reservas SET estado = 'CANCELADA', cancelada_en = now() WHERE maquina_id = ?", maquinaId);

        assertEquals(201, post("/api/reservas", cuerpo(maquinaId, fecha, 12, 1), tokenOtro).estado());
        assertEquals(201, post("/api/reservas", cuerpo(maquinaId, fecha, 9, 2), tokenOtro).estado());
    }

    @Test
    @DisplayName("Mismo cliente, mismo día, dos reservas respetando el margen: 201 y 201")
    void dosReservasMismoClienteMismoDiaRespetandoMargen() {
        String fecha = manana.toString();
        assertEquals(201, post("/api/reservas", cuerpo(maquinaId, fecha, 7, 3), tokenCliente).estado()); // 07-10, margen hasta 11
        assertEquals(201, post("/api/reservas", cuerpo(maquinaId, fecha, 11, 3), tokenCliente).estado()); // 11-14
    }

    @Test
    @DisplayName("El margen también aplica ANTES de una reserva existente")
    void margenTambienAntesDeUnaReserva() {
        String fecha = manana.toString();
        assertEquals(201, post("/api/reservas", cuerpo(maquinaId, fecha, 13, 2), tokenCliente).estado()); // 13-15
        String tokenOtro = token(crearCliente(), false);
        assertEquals(409, post("/api/reservas", cuerpo(maquinaId, fecha, 12, 1), tokenOtro).estado()); // 12-13 sin margen
        assertEquals(201, post("/api/reservas", cuerpo(maquinaId, fecha, 11, 1), tokenOtro).estado()); // 11-12, margen 12-13
    }

    @Test
    @DisplayName("Cliente sin celular registrado: 403")
    void sinCelular403() {
        Usuario sinCelular = crearCliente();
        bd().ejecutar("UPDATE usuarios SET telefono = NULL WHERE id = ?", sinCelular.id());
        Resp r = post("/api/reservas", cuerpo(maquinaId, manana.toString(), 9, 1), token(sinCelular, false));
        assertEquals(403, r.estado());
    }

    @Test
    @DisplayName("La BD acepta el estado EN_CURSO")
    void estadoEnCursoSeAceptaEnBD() {
        Usuario c = crearCliente();
        bd().ejecutar("""
                INSERT INTO reservas (maquina_id, cliente_id, fecha_inicio, fecha_fin, fin_bloqueo, tarifa_horaria, monto_total, estado)
                VALUES (?, ?, now() + interval '3 days', now() + interval '3 days 2 hours',
                        now() + interval '3 days 3 hours', 100, 200, 'EN_CURSO')""", maquinaId, c.id());
        long n = bd().uno("SELECT count(*) AS n FROM reservas WHERE estado = 'EN_CURSO'").entero("n");
        assertEquals(1L, n);
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> horasDelDia(String fecha) {
        Resp r = get("/api/maquinas/" + maquinaId + "/disponibilidad/horas?fecha=" + fecha, tokenCliente);
        assertEquals(200, r.estado());
        return (List<Map<String, Object>>) r.objeto("datos").get("horas");
    }

    private static Map<String, Object> deLaHora(List<Map<String, Object>> horas, int hora) {
        return horas.stream().filter(h -> ((Number) h.get("hora")).intValue() == hora)
                .findFirst().orElseThrow();
    }

    @Test
    @DisplayName("La hora de margen se marca PREPARACION")
    void horasMargenSeMarcaComoPreparacion() {
        String fecha = manana.toString();
        assertEquals(201, post("/api/reservas", cuerpo(maquinaId, fecha, 7, 3), tokenCliente).estado());
        List<Map<String, Object>> horas = horasDelDia(fecha);
        for (int h : new int[]{7, 8, 9}) assertEquals("RESERVADA", deLaHora(horas, h).get("estado"), "hora " + h);
        assertEquals("PREPARACION", deLaHora(horas, 10).get("estado"));
        assertTrue(horasDelDia(fecha).stream().anyMatch(h -> "LIBRE".equals(h.get("estado"))), "Debe haber horas LIBRE después del margen");
    }

    @Test
    @DisplayName("Reserva parcial: el día sigue disponible; sin hora válida: no disponible")
    void disponibleSoloSiQuedaUnaHoraValida() {
        String fecha = manana.toString();
        assertEquals(201, post("/api/reservas", cuerpo(maquinaId, fecha, 7, 3), tokenCliente).estado());
        Resp parcial = get("/api/maquinas/" + maquinaId + "/disponibilidad/horas?fecha=" + fecha, tokenCliente);
        assertEquals(Boolean.TRUE, parcial.objeto("datos").get("disponible"));

        String otro = manana.plusDays(1).toString();
        assertEquals(201, post("/api/reservas", cuerpo(maquinaId, otro, 7, 11), tokenCliente).estado()); // 07-18
        Resp lleno = get("/api/maquinas/" + maquinaId + "/disponibilidad/horas?fecha=" + otro, tokenCliente);
        assertEquals(Boolean.FALSE, lleno.objeto("datos").get("disponible"));
        assertEquals("PREPARACION", deLaHora(horasDelDia(otro), 18).get("estado"));
    }

    @Test
    @DisplayName("Calendario por día: responde 200 con una reserva (regresión del 500)")
    void calendarioPorDiaResponde200ConReserva() {
        LocalDate d = manana.plusDays(2);
        assertEquals(201, post("/api/reservas", cuerpo(maquinaId, d.toString(), 7, 3), tokenCliente).estado());
        Resp r = get("/api/maquinas/" + maquinaId + "/disponibilidad?desde=" + d + "&hasta=" + d, null);
        assertEquals(200, r.estado());
    }

    @Test
    @DisplayName("Calendario: día con reserva parcial (07-10) sigue disponible")
    void calendarioDiaParcialSigueDisponible() {
        LocalDate d = manana.plusDays(2);
        assertEquals(201, post("/api/reservas", cuerpo(maquinaId, d.toString(), 7, 3), tokenCliente).estado());
        Resp r = get("/api/maquinas/" + maquinaId + "/disponibilidad?desde=" + d + "&hasta=" + d, null);
        List<Map<String, Object>> ocupados = (List<Map<String, Object>>) ((Map<String, Object>) r.cuerpo()).get("ocupados");
        assertEquals(false, ocupados.stream().anyMatch(o -> d.toString().equals(o.get("fechaInicio"))));
    }

    @Test
    @DisplayName("Calendario: día lleno (07-18, sin horas válidas) no disponible")
    void calendarioDiaLlenoNoDisponible() {
        LocalDate d = manana.plusDays(2);
        assertEquals(201, post("/api/reservas", cuerpo(maquinaId, d.toString(), 7, 11), tokenCliente).estado());
        Resp r = get("/api/maquinas/" + maquinaId + "/disponibilidad?desde=" + d + "&hasta=" + d, null);
        List<Map<String, Object>> ocupados = (List<Map<String, Object>>) ((Map<String, Object>) r.cuerpo()).get("ocupados");
        assertEquals(true, ocupados.stream().anyMatch(o -> d.toString().equals(o.get("fechaInicio")) && "RESERVAS".equals(o.get("tipo"))));
    }

    @Test
    @DisplayName("Calendario: reserva cancelada libera el día")
    void calendarioReservaCanceladaLiberaElDia() {
        LocalDate d = manana.plusDays(2);
        post("/api/reservas", cuerpo(maquinaId, d.toString(), 7, 11), tokenCliente);
        String codigo = bd().uno("SELECT codigo FROM reservas WHERE maquina_id = ? ORDER BY id DESC LIMIT 1", maquinaId).texto("codigo");
        bd().ejecutar("UPDATE reservas SET estado = 'CANCELADA' WHERE codigo = ?", codigo);
        Resp r = get("/api/maquinas/" + maquinaId + "/disponibilidad?desde=" + d + "&hasta=" + d, null);
        List<Map<String, Object>> ocupados = (List<Map<String, Object>>) ((Map<String, Object>) r.cuerpo()).get("ocupados");
        assertEquals(false, ocupados.stream().anyMatch(o -> d.toString().equals(o.get("fechaInicio"))));
    }

    @Test
    @DisplayName("Calendario: mantenimiento bloquea el día")
    void calendarioMantenimientoBloqueaElDia() {
        LocalDate d = manana.plusDays(2);
        LocalDate dPlus1 = d.plusDays(1);
        bd().ejecutar("""
                INSERT INTO bloqueos_disponibilidad (maquina_id, fecha_inicio, fecha_fin, motivo)
                VALUES (?, ?::date, ?::date, 'Mantenimiento')""", maquinaId, d.toString(), dPlus1.toString());
        Resp r = get("/api/maquinas/" + maquinaId + "/disponibilidad?desde=" + d + "&hasta=" + d, null);
        List<Map<String, Object>> ocupados = (List<Map<String, Object>>) ((Map<String, Object>) r.cuerpo()).get("ocupados");
        assertEquals(true, ocupados.stream().anyMatch(o -> d.toString().equals(o.get("fechaInicio")) && "BLOQUEO".equals(o.get("tipo"))));
    }

    @Test
    @DisplayName("Calendario sin parámetros usa rango por defecto y responde 200")
    void calendarioSinParametrosUsaRangoPorDefectoYResponde200() {
        LocalDate d = manana.plusDays(2);
        assertEquals(201, post("/api/reservas", cuerpo(maquinaId, d.toString(), 7, 11), tokenCliente).estado()); // 07-18, llena todo el día
        Resp r = get("/api/maquinas/" + maquinaId + "/disponibilidad", null);
        assertEquals(200, r.estado());
        List<Map<String, Object>> ocupados = (List<Map<String, Object>>) ((Map<String, Object>) r.cuerpo()).get("ocupados");
        assertEquals(true, ocupados.stream().anyMatch(o -> d.toString().equals(o.get("fechaInicio")) && "RESERVAS".equals(o.get("tipo"))));
    }
}
