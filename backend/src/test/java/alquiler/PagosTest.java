package alquiler;

import alquiler.json.Json;
import alquiler.pagos.PasarelaPago;
import alquiler.reservas.ReservaHoraServicio;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@DisplayName("HU-07: Pagar reserva en línea")
class PagosTest extends PruebaBase {

    private static int secuencia = 0;

    @Autowired
    private ReservaHoraServicio reservasServicio;

    private Usuario cliente;
    private String token;
    private long maquinaId;
    private long reservaId;

    @BeforeEach
    void preparar() {
        cliente = crearCliente();
        token = token(cliente, false);
        long categoriaId = crearCategoria("Excavadoras", true);
        maquinaId = bd().uno("""
                INSERT INTO maquinas (categoria_id, nombre, marca, modelo, tarifa_horaria, ubicacion, estado, publicada_en)
                VALUES (?, 'Excavadora', 'CAT', '320', 100, 'Trujillo', 'PUBLICADA', now()) RETURNING id""",
                categoriaId).entero("id");
        reservaId = insertarReserva("PENDIENTE_PAGO", Instant.now().plus(2, ChronoUnit.HOURS), 0);
        when(pasarela.configurada()).thenReturn(true);
    }

    /** Reserva de 2 horas por S/ 200 para mañana; "desdeHoras" separa las reservas para que no se solapen. */
    private long insertarReserva(String estado, Instant expiraPago, int desdeHoras) {
        Instant inicio = Instant.now().plus(1, ChronoUnit.DAYS).plus(desdeHoras, ChronoUnit.HOURS);
        Instant fin = inicio.plus(2, ChronoUnit.HOURS);
        return bd().uno("""
                INSERT INTO reservas (codigo, cliente_id, maquina_id, fecha_inicio, fecha_fin, fin_bloqueo,
                                      tarifa_horaria, monto_total, estado, expira_pago_en)
                VALUES (?, ?, ?, ?, ?, ?, 100, 200, ?, ?) RETURNING id""",
                "PRUEBA-" + (++secuencia), cliente.id(), maquinaId, inicio, fin, fin.plus(1, ChronoUnit.HOURS),
                estado, expiraPago).entero("id");
    }

    private Map<String, Object> datosTarjeta() {
        return Json.obj("token", "tok_1234567890abcdef", "metodoPagoId", "master", "emisorId", "24",
                "correo", "comprador@test.com", "tipoDocumento", "DNI", "numeroDocumento", "12345678");
    }

    private void culqiCobra(String idPago, String estado, String detalle) {
        PasarelaPago.Pago pago = new PasarelaPago.Pago(idPago, estado, detalle, String.valueOf(reservaId), new BigDecimal("200.00"));
        when(pasarela.pagarConTarjeta(eq(String.valueOf(reservaId)), anyString(), any(BigDecimal.class), any(PasarelaPago.DatosTarjeta.class)))
                .thenReturn(pago);
        when(pasarela.consultarPago(idPago)).thenReturn(pago);
    }

    @Test
    @DisplayName("Tarjeta: el pago aprobado confirma la reserva sin salir de la página")
    void pagarConTarjetaAprobado() {
        culqiCobra("777", "approved", "accredited");

        Resp r = post("/api/reservas/" + reservaId + "/pagar-con-tarjeta", datosTarjeta(), token);

        assertEquals(200, r.estado(), String.valueOf(r.cuerpo()));
        assertEquals("PAGADA", r.objeto("datos").get("estadoReserva"));
        assertEquals("APROBADO", r.objeto("datos").get("estadoPago"));
        assertEquals("PAGADA", estadoReserva(reservaId));
        assertEquals(1L, cantidadPagos("777"));
    }

    @Test
    @DisplayName("Tarjeta: si el pago es rechazado, se informa el motivo, la reserva sigue pendiente y se puede reintentar")
    void pagarConTarjetaRechazado() {
        culqiCobra("778", "rejected", "insufficient_funds");
        String ruta = "/api/reservas/" + reservaId + "/pagar-con-tarjeta";

        Resp r = post(ruta, datosTarjeta(), token);

        assertEquals(200, r.estado(), String.valueOf(r.cuerpo()));
        assertEquals("RECHAZADO", r.objeto("datos").get("estadoPago"));
        assertEquals("El pago fue rechazado. Revisa los datos de tu tarjeta o prueba con otro medio de pago", r.objeto("datos").get("motivo"));
        assertEquals("PENDIENTE_PAGO", estadoReserva(reservaId));

        culqiCobra("779", "approved", "accredited");
        Resp reintento = post(ruta, datosTarjeta(), token);
        assertEquals("PAGADA", reintento.objeto("datos").get("estadoReserva"));
    }

    @Test
    @DisplayName("Tarjeta: valida sesión, dueño, datos, estado de la reserva y configuración")
    void pagarConTarjetaValidaDatos() {
        culqiCobra("777", "approved", "accredited");
        String ruta = "/api/reservas/" + reservaId + "/pagar-con-tarjeta";
        String otro = token(crearCliente(), false);

        assertEquals(401, post(ruta, datosTarjeta(), null).estado());
        assertEquals(404, post(ruta, datosTarjeta(), otro).estado());
        assertEquals(400, post(ruta, Json.obj("token", "tok_1234567890abcdef", "metodoPagoId", "master", "correo", "no-es-correo"), token).estado());
        assertEquals(400, post(ruta, Json.obj("correo", "comprador@test.com"), token).estado());
        bd().ejecutar("UPDATE reservas SET estado = 'PAGADA' WHERE id = ?", reservaId);
        assertEquals(409, post(ruta, datosTarjeta(), token).estado());
        when(pasarela.configurada()).thenReturn(false);
        assertEquals(503, post(ruta, datosTarjeta(), token).estado());
        assertEquals(0L, bd().uno("SELECT count(*) AS n FROM pagos").entero("n"));
    }

    @Test
    @DisplayName("Tarjeta: la clave pública para el formulario es pública y responde 503 si falta")
    void clavePublicaParaElFormulario() {
        when(pasarela.clavePublica()).thenReturn("pk_test_clave_publica_de_prueba");

        Resp r = get("/api/pagos/config", null);

        assertEquals(200, r.estado(), String.valueOf(r.cuerpo()));
        assertEquals("pk_test_clave_publica_de_prueba", r.objeto("datos").get("publicKey"));
        when(pasarela.clavePublica()).thenReturn(null);
        assertEquals(503, get("/api/pagos/config", null).estado());
    }

    private void cobrarConDocumento(String idPago, String documento) {
        bd().ejecutar("UPDATE reservas SET ruc = ?, nombre_solicitante = 'Constructora Prueba' WHERE id = ?", documento, reservaId);
        culqiCobra(idPago, "approved", "accredited");
        assertEquals(200, post("/api/reservas/" + reservaId + "/pagar-con-tarjeta", datosTarjeta(), token).estado());
    }

    @Test
    @DisplayName("HU-08: al aprobarse el pago con DNI se emite una boleta simulada aceptada")
    void pagoAprobadoEmiteBoleta() {
        cobrarConDocumento("801", "12345678");

        var c = bd().uno("SELECT * FROM comprobantes WHERE reserva_id = ?", reservaId);

        assertEquals("BOLETA", c.texto("tipo"));
        assertEquals("B001", c.texto("serie"));
        assertEquals("12345678", c.texto("documento_cliente"));
        assertEquals("ACEPTADO", c.texto("estado"));
        assertNotNull(c.texto("codigo_hash"));
        assertEquals(0, new BigDecimal("200.00").compareTo(c.decimal("total")));
        assertEquals(0, new BigDecimal("169.49").compareTo(c.decimal("valor_venta")));
        assertEquals(0, new BigDecimal("30.51").compareTo(c.decimal("igv")));
    }

    @Test
    @DisplayName("HU-08: al aprobarse el pago con RUC se emite una factura simulada")
    void pagoAprobadoEmiteFactura() {
        cobrarConDocumento("802", "20123456789");

        var c = bd().uno("SELECT * FROM comprobantes WHERE reserva_id = ?", reservaId);

        assertEquals("FACTURA", c.texto("tipo"));
        assertEquals("F001", c.texto("serie"));
        assertEquals("20123456789", c.texto("documento_cliente"));
        assertEquals("ACEPTADO", c.texto("estado"));
    }

    @Test
    @DisplayName("HU-08: el cliente descarga su comprobante en PDF y XML; otros no pueden")
    void descargaDelComprobante() {
        String ruta = "/api/reservas/" + reservaId + "/comprobante";
        assertEquals(404, descargar(ruta, token).estado());

        cobrarConDocumento("804", "12345678");

        Descarga pdf = descargar(ruta + "?formato=pdf", token);
        assertEquals(200, pdf.estado());
        assertTrue(pdf.tipo().contains("application/pdf"));
        assertEquals("%PDF", new String(pdf.datos(), 0, 4, StandardCharsets.US_ASCII));

        Descarga xml = descargar(ruta + "?formato=xml", token);
        assertEquals(200, xml.estado());
        assertTrue(xml.tipo().contains("xml"));
        String contenido = new String(xml.datos(), StandardCharsets.UTF_8);
        assertTrue(contenido.contains("B001-"));
        assertTrue(contenido.contains("Comprobante simulado, sin validez tributaria"));

        assertEquals(404, descargar(ruta, token(crearCliente(), false)).estado());
        assertEquals(401, descargar(ruta, null).estado());
        assertEquals(400, descargar(ruta + "?formato=doc", token).estado());
    }
    private String estadoReserva(long id) {
        return bd().uno("SELECT estado FROM reservas WHERE id = ?", id).texto("estado");
    }

    private long cantidadPagos(String idExterno) {
        return bd().uno("SELECT count(*) AS n FROM pagos WHERE id_externo = ?", idExterno).entero("n");
    }

    // ------------------------------------------------------------ Criterio 5: 2 horas
    @Test
    @DisplayName("C5: pasadas las 2 horas sin pagar, la reserva se cancela y el horario queda libre")
    void cancelaLasVencidas() {
        long vencida = insertarReserva("PENDIENTE_PAGO", Instant.now().minus(1, ChronoUnit.MINUTES), 10);
        long pagadaVieja = insertarReserva("PAGADA", Instant.now().minus(1, ChronoUnit.HOURS), 20);

        int canceladas = reservasServicio.cancelarVencidas();

        assertEquals(1, canceladas);
        assertEquals("CANCELADA", estadoReserva(vencida));
        assertNotNull(bd().uno("SELECT cancelada_en FROM reservas WHERE id = ?", vencida).instante("cancelada_en"));
        assertEquals("PAGADA", estadoReserva(pagadaVieja));
        assertEquals("PENDIENTE_PAGO", estadoReserva(reservaId));
        // El horario quedó libre: se puede reservar otra vez el mismo
        assertDoesNotThrow(() -> insertarReserva("PENDIENTE_PAGO", Instant.now().plus(2, ChronoUnit.HOURS), 10));
    }

    @Test
    @DisplayName("C5: intentar pagar una reserva vencida la cancela y responde 409")
    void pagarVencida() {
        bd().ejecutar("UPDATE reservas SET expira_pago_en = now() - interval '1 minute' WHERE id = ?", reservaId);

        assertEquals(409, post("/api/reservas/" + reservaId + "/pagar-con-tarjeta", datosTarjeta(), token).estado());
        assertEquals("CANCELADA", estadoReserva(reservaId));
    }
}