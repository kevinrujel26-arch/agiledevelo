package alquiler;

import alquiler.json.Json;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("HU-06: Reservar máquina por horas")
class ReservasTest extends PruebaBase {

    private String tokenCliente;
    private String tokenAdmin;
    private long maquinaId;
    private String mañana;
    private String pasadoMañana;

    @BeforeEach
    void preparar() {
        // Crear usuario cliente con celular
        Usuario clienteU = crearCliente();
        bd().ejecutar("UPDATE usuarios SET telefono = ? WHERE id = ?", "987654321", clienteU.id());
        tokenCliente = token(clienteU, false);

        // Crear admin
        tokenAdmin = tokenAdmin();

        // Crear máquina publicada
        long categoriaId = crearCategoria("Excavadoras", true);
        maquinaId = bd().uno("""
                INSERT INTO maquinas (categoria_id, nombre, marca, modelo, tarifa_horaria, ubicacion, estado, publicada_en)
                VALUES (?, 'Excavadora', 'CAT', '320', 100, 'Trujillo', 'PUBLICADA', now()) RETURNING id""",
                categoriaId).entero("id");

        // Fechas
        ZoneId zonaLima = ZoneId.of("America/Lima");
        LocalDate hoy = LocalDate.now(zonaLima);
        mañana = hoy.plusDays(1).toString();
        pasadoMañana = hoy.plusDays(2).toString();
    }

    // ========================================================
    // Validaciones básicas y restricciones
    // ========================================================

    @Test
    @DisplayName("Crear reserva válida: 201")
    void crearReservaValida() {
        Resp resp = post("/api/reservas", Json.obj(
                "maquinaId", maquinaId,
                "fecha", mañana,
                "horaInicio", 9,
                "horas", 3,
                "nombreSolicitante", "Juan Pérez García",
                "ruc", "20000000109",
                "telefono", "987654321",
                "email", "juan@example.com",
                "ubicacionObra", "Av. Siempre Viva 123",
                "detalle", "Obra en Lima",
                "aceptaPolitica", true), tokenCliente);

        assertEquals(201, resp.estado());
        Map<String, Object> datos = (Map<String, Object>) ((Map<String, Object>) resp.cuerpo()).get("datos");
        assertNotNull(datos.get("codigo"));
        assertEquals("PENDIENTE_PAGO", datos.get("estado"));
        assertEquals(maquinaId, ((Number) datos.get("maquinaId")).longValue());
        assertEquals(3L, ((Number) datos.get("horas")).longValue());
        // total puede venir como BigDecimal o como Number
        Object totalObj = datos.get("total");
        if (totalObj instanceof BigDecimal bd) {
            assertEquals(new BigDecimal("300"), bd);
        } else {
            assertEquals(300.0, ((Number) totalObj).doubleValue(), 0.01);
        }
    }

    @Test
    @DisplayName("Reserva solapa con otra existente: 409")
    void reservaSolapa() {
        // Primera reserva: 9:00 a 12:00
        post("/api/reservas", Json.obj(
                "maquinaId", maquinaId,
                "fecha", mañana,
                "horaInicio", 9,
                "horas", 3,
                "nombreSolicitante", "Juan Pérez",
                "ruc", "20000000109",
                "telefono", "987654321",
                "email", "juan@example.com",
                "ubicacionObra", "Av. Test",
                "aceptaPolitica", true), tokenCliente);

        // Segunda reserva: 11:00 a 13:00 (se solapa)
        Resp resp = post("/api/reservas", Json.obj(
                "maquinaId", maquinaId,
                "fecha", mañana,
                "horaInicio", 11,
                "horas", 2,
                "nombreSolicitante", "Pedro González",
                "ruc", "10000000103",
                "telefono", "987654322",
                "email", "pedro@example.com",
                "ubicacionObra", "Av. Test",
                "aceptaPolitica", true), tokenCliente);

        assertEquals(409, resp.estado());
    }

    @Test
    @DisplayName("Reserva adyacente sin margen: 409")
    void reservaAdyacenteSinMargen() {
        // Primera reserva: 9:00 a 10:00
        post("/api/reservas", Json.obj(
                "maquinaId", maquinaId,
                "fecha", mañana,
                "horaInicio", 9,
                "horas", 1,
                "nombreSolicitante", "Juan Pérez",
                "ruc", "20000000109",
                "telefono", "987654321",
                "email", "juan@example.com",
                "ubicacionObra", "Av. Test",
                "aceptaPolitica", true), tokenCliente);

        // Segunda reserva: 10:00 a 11:00 (exactamente adyacente, sin margen)
        Resp resp = post("/api/reservas", Json.obj(
                "maquinaId", maquinaId,
                "fecha", mañana,
                "horaInicio", 10,
                "horas", 1,
                "nombreSolicitante", "Pedro González",
                "ruc", "10000000103",
                "telefono", "987654322",
                "email", "pedro@example.com",
                "ubicacionObra", "Av. Test",
                "aceptaPolitica", true), tokenCliente);

        assertEquals(409, resp.estado());
    }

    @Test
    @DisplayName("Reserva después del margen: 201")
    void reservaDespuesDelMargen() {
        // Primera reserva: 9:00 a 10:00
        post("/api/reservas", Json.obj(
                "maquinaId", maquinaId,
                "fecha", mañana,
                "horaInicio", 9,
                "horas", 1,
                "nombreSolicitante", "Juan Pérez",
                "ruc", "20000000109",
                "telefono", "987654321",
                "email", "juan@example.com",
                "ubicacionObra", "Av. Test",
                "aceptaPolitica", true), tokenCliente);

        // Segunda reserva: 11:00 a 12:00 (hay margen de 1 hora: 10:00-11:00)
        Resp resp = post("/api/reservas", Json.obj(
                "maquinaId", maquinaId,
                "fecha", mañana,
                "horaInicio", 11,
                "horas", 1,
                "nombreSolicitante", "Pedro González",
                "ruc", "10000000103",
                "telefono", "987654322",
                "email", "pedro@example.com",
                "ubicacionObra", "Av. Test",
                "aceptaPolitica", true), tokenCliente);

        assertEquals(201, resp.estado());
    }

    // ========================================================
    // Validaciones de datos del solicitante
    // ========================================================

    @Test
    @DisplayName("RUC inválido: 400")
    void rucInvalido() {
        Resp resp = post("/api/reservas", Json.obj(
                "maquinaId", maquinaId,
                "fecha", mañana,
                "horaInicio", 9,
                "horas", 1,
                "nombreSolicitante", "Juan Pérez",
                "ruc", "12345678901",  // Inválido
                "telefono", "987654321",
                "email", "juan@example.com",
                "ubicacionObra", "Av. Test",
                "aceptaPolitica", true), tokenCliente);

        assertEquals(400, resp.estado());
    }

    @Test
    @DisplayName("Email inválido: 400")
    void emailInvalido() {
        Resp resp = post("/api/reservas", Json.obj(
                "maquinaId", maquinaId,
                "fecha", mañana,
                "horaInicio", 9,
                "horas", 1,
                "nombreSolicitante", "Juan Pérez",
                "ruc", "20000000109",
                "telefono", "987654321",
                "email", "invalid-email",
                "ubicacionObra", "Av. Test",
                "aceptaPolitica", true), tokenCliente);

        assertEquals(400, resp.estado());
    }

    @Test
    @DisplayName("Falta aceptar política: 400")
    void faltaAceptarPolitica() {
        Resp resp = post("/api/reservas", Json.obj(
                "maquinaId", maquinaId,
                "fecha", mañana,
                "horaInicio", 9,
                "horas", 1,
                "nombreSolicitante", "Juan Pérez",
                "ruc", "20000000109",
                "telefono", "987654321",
                "email", "juan@example.com",
                "ubicacionObra", "Av. Test",
                "aceptaPolitica", false), tokenCliente);

        assertEquals(400, resp.estado());
    }

    // ========================================================
    // Validaciones de horario
    // ========================================================

    @Test
    @DisplayName("Hora fuera de operación: 400")
    void horaFueraDeOperacion() {
        Resp resp = post("/api/reservas", Json.obj(
                "maquinaId", maquinaId,
                "fecha", mañana,
                "horaInicio", 6,  // Antes de las 7:00
                "horas", 1,
                "nombreSolicitante", "Juan Pérez",
                "ruc", "20000000109",
                "telefono", "987654321",
                "email", "juan@example.com",
                "ubicacionObra", "Av. Test",
                "aceptaPolitica", true), tokenCliente);

        assertEquals(400, resp.estado());
    }

    @Test
    @DisplayName("Reserva termina fuera de operación: 400")
    void reservaTerminaFueraDeOperacion() {
        Resp resp = post("/api/reservas", Json.obj(
                "maquinaId", maquinaId,
                "fecha", mañana,
                "horaInicio", 18,
                "horas", 2,  // Terminaría a las 20:00, pero cierre es 19:00
                "nombreSolicitante", "Juan Pérez",
                "ruc", "20000000109",
                "telefono", "987654321",
                "email", "juan@example.com",
                "ubicacionObra", "Av. Test",
                "aceptaPolitica", true), tokenCliente);

        assertEquals(400, resp.estado());
    }

    @Test
    @DisplayName("Fecha en el pasado: 400")
    void fechaPasadaOHoy() {
        ZoneId zonaLima = ZoneId.of("America/Lima");
        String ayer = LocalDate.now(zonaLima).minusDays(1).toString();

        Resp resp = post("/api/reservas", Json.obj(
                "maquinaId", maquinaId,
                "fecha", ayer,
                "horaInicio", 9,
                "horas", 1,
                "nombreSolicitante", "Juan Pérez",
                "ruc", "20000000109",
                "telefono", "987654321",
                "email", "juan@example.com",
                "ubicacionObra", "Av. Test",
                "aceptaPolitica", true), tokenCliente);

        assertEquals(400, resp.estado());
    }

    // ========================================================
    // Máquina
    // ========================================================

    @Test
    @DisplayName("Máquina en mantenimiento: 409")
    void maquinaEnMantenimiento() {
        bd().ejecutar("UPDATE maquinas SET en_mantenimiento = true WHERE id = ?", maquinaId);

        Resp resp = post("/api/reservas", Json.obj(
                "maquinaId", maquinaId,
                "fecha", mañana,
                "horaInicio", 9,
                "horas", 1,
                "nombreSolicitante", "Juan Pérez",
                "ruc", "20000000109",
                "telefono", "987654321",
                "email", "juan@example.com",
                "ubicacionObra", "Av. Test",
                "aceptaPolitica", true), tokenCliente);

        assertEquals(409, resp.estado());
    }

    // ========================================================
    // Límite de pendientes
    // ========================================================

    @Test
    @DisplayName("Máximo 2 reservas PENDIENTE_PAGO: tercera es 409")
    void maximoPendientes() {
        // Primera reserva
        post("/api/reservas", Json.obj(
                "maquinaId", maquinaId,
                "fecha", mañana,
                "horaInicio", 9,
                "horas", 1,
                "nombreSolicitante", "Juan Pérez",
                "ruc", "20000000109",
                "telefono", "987654321",
                "email", "juan@example.com",
                "ubicacionObra", "Av. Test",
                "aceptaPolitica", true), tokenCliente);

        // Segunda reserva
        post("/api/reservas", Json.obj(
                "maquinaId", maquinaId,
                "fecha", pasadoMañana,
                "horaInicio", 9,
                "horas", 1,
                "nombreSolicitante", "Juan Pérez",
                "ruc", "20000000109",
                "telefono", "987654321",
                "email", "juan@example.com",
                "ubicacionObra", "Av. Test",
                "aceptaPolitica", true), tokenCliente);

        // Tercera reserva (debe fallar)
        long categoria2 = crearCategoria("Topadoras", true);
        long maquina2 = bd().uno("""
                INSERT INTO maquinas (categoria_id, nombre, marca, modelo, tarifa_horaria, ubicacion, estado, publicada_en)
                VALUES (?, 'Topadora', 'CAT', 'D6', 150, 'Lima', 'PUBLICADA', now()) RETURNING id""",
                categoria2).entero("id");

        Resp resp = post("/api/reservas", Json.obj(
                "maquinaId", maquina2,
                "fecha", mañana,
                "horaInicio", 14,
                "horas", 1,
                "nombreSolicitante", "Juan Pérez",
                "ruc", "20000000109",
                "telefono", "987654321",
                "email", "juan@example.com",
                "ubicacionObra", "Av. Test",
                "aceptaPolitica", true), tokenCliente);

        assertEquals(409, resp.estado());
    }

    // ========================================================
    // Seguridad y autorización
    // ========================================================

    @Test
    @DisplayName("Sin token: 401")
    void sinToken() {
        Resp resp = post("/api/reservas", Json.obj(
                "maquinaId", maquinaId,
                "fecha", mañana,
                "horaInicio", 9,
                "horas", 1,
                "nombreSolicitante", "Juan Pérez",
                "ruc", "20000000109",
                "telefono", "987654321",
                "email", "juan@example.com",
                "ubicacionObra", "Av. Test",
                "aceptaPolitica", true), null);

        assertEquals(401, resp.estado());
    }

    @Test
    @DisplayName("GET /api/admin/reservas solo para admin: 403 sin admin")
    void listarReservasNoAdmin() {
        Resp resp = get("/api/admin/reservas", tokenCliente);
        assertEquals(403, resp.estado());
    }

    @Test
    @DisplayName("GET /api/admin/reservas funciona para admin: 200")
    void listarReservasAdmin() {
        Resp resp = get("/api/admin/reservas", tokenAdmin);
        assertEquals(200, resp.estado());
    }

    @Test
    @DisplayName("GET /api/admin/reservas incluye maquinaNombre, clienteNombre, clienteCorreo; ordena por creado_en DESC")
    void listarReservasAdminIncluyeNombresYOrden() {
        LocalDate manana = LocalDate.now(ZoneId.of("America/Lima")).plusDays(1);

        // Crear 2 reservas con tokens diferentes (clientes diferentes)
        String tokenCliente1 = token(crearCliente(), false);
        String tokenCliente2 = token(crearCliente(), false);

        // Primera reserva
        Resp resp1 = post("/api/reservas", Json.obj(
                "maquinaId", maquinaId,
                "fecha", manana.toString(),
                "horaInicio", 8,
                "horas", 1,
                "nombreSolicitante", "Cliente 1",
                "ruc", "20000000109",
                "telefono", "987654321",
                "email", "cliente1@example.com",
                "ubicacionObra", "Av. Test 1",
                "aceptaPolitica", true), tokenCliente1);
        assertEquals(201, resp1.estado());

        // Segunda reserva (día siguiente para que no choque)
        LocalDate pasadomañana = manana.plusDays(1);
        Resp resp2 = post("/api/reservas", Json.obj(
                "maquinaId", maquinaId,
                "fecha", pasadomañana.toString(),
                "horaInicio", 9,
                "horas", 2,
                "nombreSolicitante", "Cliente 2",
                "ruc", "20000000109",
                "telefono", "987654321",
                "email", "cliente2@example.com",
                "ubicacionObra", "Av. Test 2",
                "aceptaPolitica", true), tokenCliente2);
        assertEquals(201, resp2.estado());

        // Obtener listado de admin
        Resp adminResp = get("/api/admin/reservas", tokenAdmin);
        assertEquals(200, adminResp.estado());

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> reservas = (List<Map<String, Object>>) adminResp.lista("datos");
        assertTrue(reservas.size() >= 2, "Debe haber al menos 2 reservas");

        // Verificar que los primeros dos elementos (más recientes) tienen los campos nuevos
        Map<String, Object> primera = reservas.get(0);
        Map<String, Object> segunda = reservas.get(1);

        // Primera reserva debe tener los campos nuevos
        assertNotNull(primera.get("maquinaNombre"), "maquinaNombre no puede ser nulo");
        assertNotNull(primera.get("clienteNombre"), "clienteNombre no puede ser nulo");
        assertNotNull(primera.get("clienteCorreo"), "clienteCorreo no puede ser nulo");
        assertTrue(((String) primera.get("maquinaNombre")).length() > 0, "maquinaNombre no puede estar vacío");
        assertTrue(((String) primera.get("clienteNombre")).length() > 0, "clienteNombre no puede estar vacío");
        assertTrue(((String) primera.get("clienteCorreo")).length() > 0, "clienteCorreo no puede estar vacío");

        // Segunda reserva también debe tener los campos
        assertNotNull(segunda.get("maquinaNombre"), "maquinaNombre no puede ser nulo");
        assertNotNull(segunda.get("clienteNombre"), "clienteNombre no puede ser nulo");
        assertNotNull(segunda.get("clienteCorreo"), "clienteCorreo no puede ser nulo");

        // Verificar que están ordenadas por creado_en DESC (la primera es más reciente que la segunda)
        Instant creadoEn1 = Instant.parse((String) primera.get("creadoEn"));
        Instant creadoEn2 = Instant.parse((String) segunda.get("creadoEn"));
        assertTrue(creadoEn1.isAfter(creadoEn2) || creadoEn1.equals(creadoEn2), "La primera debe ser más reciente o igual (DESC order)");
    }

    // ========================================================
    // Reservas para hoy con anticipación mínima
    // ========================================================

    @Test
    @DisplayName("Reserva para hoy sin anticipación suficiente: 400")
    void reservaHoyConAntelacionInsuficiente400() {
        ZoneId zonaLima = ZoneId.of("America/Lima");
        LocalDate hoy = LocalDate.now(zonaLima);
        Instant ahora = Instant.now();
        Instant proximaHoraValida = ahora.plus(2, ChronoUnit.HOURS);

        // Calcular la primera hora de inicio que NO cumple con la anticipación (dentro de la ventana)
        int horaIntentarAhora = proximaHoraValida.atZone(zonaLima).getHour();
        if (horaIntentarAhora >= 19) {
            org.junit.jupiter.api.Assumptions.assumeTrue(false, "Es demasiado tarde para este test");
        }

        Resp resp = post("/api/reservas", Json.obj(
                "maquinaId", maquinaId,
                "fecha", hoy.toString(),
                "horaInicio", horaIntentarAhora,
                "horas", 1,
                "nombreSolicitante", "Juan Pérez",
                "ruc", "20000000109",
                "telefono", "987654321",
                "email", "juan@example.com",
                "ubicacionObra", "Av. Test",
                "aceptaPolitica", true), tokenCliente);

        assertEquals(400, resp.estado());
    }

    @Test
    @DisplayName("Reserva para hoy con anticipación suficiente: 201")
    void reservaHoyConAntelacionSuficienteOk() {
        ZoneId zonaLima = ZoneId.of("America/Lima");
        LocalDate hoy = LocalDate.now(zonaLima);
        Instant ahora = Instant.now();
        // Calcular la primera hora de inicio que SÍ cumple con la anticipación (al menos 3 horas)
        Instant proximaHoraValida = ahora.plus(3, ChronoUnit.HOURS);
        int horaValida = proximaHoraValida.atZone(zonaLima).getHour();
        if (horaValida >= 18) {  // 18 porque 18+1=19 que es cierre
            org.junit.jupiter.api.Assumptions.assumeTrue(false, "Es demasiado tarde para este test");
        }

        Resp resp = post("/api/reservas", Json.obj(
                "maquinaId", maquinaId,
                "fecha", hoy.toString(),
                "horaInicio", horaValida,
                "horas", 1,
                "nombreSolicitante", "Juan Pérez",
                "ruc", "20000000109",
                "telefono", "987654321",
                "email", "juan@example.com",
                "ubicacionObra", "Av. Test",
                "aceptaPolitica", true), tokenCliente);

        assertEquals(201, resp.estado());
    }

    @Test
    @DisplayName("Horas de hoy anteriores a la anticipación se marcan PASADA")
    void horasDeHoyPasadasSeMarcanPASADA() {
        ZoneId zonaLima = ZoneId.of("America/Lima");
        LocalDate hoy = LocalDate.now(zonaLima);
        Instant ahora = Instant.now();
        int horaActual = ahora.atZone(zonaLima).getHour();

        if (horaActual < 7) {
            org.junit.jupiter.api.Assumptions.assumeTrue(false, "Es muy temprano, no hay horas pasadas que comprobar");
        }

        Resp resp = get("/api/maquinas/" + maquinaId + "/disponibilidad/horas?fecha=" + hoy.toString(), tokenCliente);
        assertEquals(200, resp.estado());

        @SuppressWarnings("unchecked")
        Map<String, Object> datos = (Map<String, Object>) ((Map<String, Object>) resp.cuerpo()).get("datos");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> listHoras = (List<Map<String, Object>>) datos.get("horas");

        // Verificar que hay al menos una hora con estado PASADA
        boolean tieneHoraPasada = listHoras.stream()
                .anyMatch(h -> "PASADA".equals(h.get("estado")));
        assertTrue(tieneHoraPasada, "Debe haber al menos una hora marcada como PASADA");
    }

}
