package alquiler;

import alquiler.json.Json;
import alquiler.pagos.PasarelaPago;
import alquiler.reservas.ReservaHoraServicio;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
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

    private void mercadoPagoCrea(String preferenciaId) {
        when(pasarela.crearPreferencia(eq(String.valueOf(reservaId)), anyString(), any(BigDecimal.class), any()))
                .thenReturn(new PasarelaPago.Preferencia(preferenciaId, "https://mercadopago.test/pagar/" + preferenciaId));
    }

    private void mercadoPagoInforma(String idPago, String estado, String detalle, long referencia, String monto) {
        when(pasarela.consultarPago(idPago)).thenReturn(
                new PasarelaPago.Pago(idPago, estado, detalle, String.valueOf(referencia), new BigDecimal(monto)));
    }

    private Resp avisoDeMercadoPago(String idPago) {
        return post("/api/pagos/webhook?type=payment&data.id=" + idPago, Json.obj("type", "payment"), null);
    }

    @Test
    @DisplayName("C2: al aprobarse el pago, no queda el registro provisional: solo el pago real")
    void pagoAprobadoDejaUnSoloRegistro() {
        mercadoPagoCrea("pref-1");
        assertEquals(200, post("/api/reservas/" + reservaId + "/pagar", Json.obj(), token).estado());
        assertEquals(1L, bd().uno("SELECT count(*) AS n FROM pagos WHERE reserva_id = ?", reservaId).entero("n"));
        mercadoPagoInforma("555", "approved", "accredited", reservaId, "200.00");

        assertEquals(200, avisoDeMercadoPago("555").estado());

        assertEquals(1L, bd().uno("SELECT count(*) AS n FROM pagos WHERE reserva_id = ?", reservaId).entero("n"));
        assertEquals("APROBADO", bd().uno("SELECT estado FROM pagos WHERE reserva_id = ?", reservaId).texto("estado"));
    }

    private Map<String, Object> datosTarjeta() {
        return Json.obj("token", "tok_1234567890abcdef", "metodoPagoId", "master", "emisorId", "24",
                "correo", "comprador@test.com", "tipoDocumento", "DNI", "numeroDocumento", "12345678");
    }

    /** Mercado Pago cobra con la tarjeta y responde con este estado; luego se consulta igual que con el aviso. */
    private void mercadoPagoCobra(String idPago, String estado, String detalle) {
        PasarelaPago.Pago pago = new PasarelaPago.Pago(idPago, estado, detalle, String.valueOf(reservaId), new BigDecimal("200.00"));
        when(pasarela.pagarConTarjeta(eq(String.valueOf(reservaId)), anyString(), any(BigDecimal.class), any(PasarelaPago.DatosTarjeta.class)))
                .thenReturn(pago);
        when(pasarela.consultarPago(idPago)).thenReturn(pago);
    }

    @Test
    @DisplayName("Tarjeta: el pago aprobado confirma la reserva sin salir de la página")
    void pagarConTarjetaAprobado() {
        mercadoPagoCobra("777", "approved", "accredited");

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
        mercadoPagoCobra("778", "rejected", "cc_rejected_insufficient_amount");
        String ruta = "/api/reservas/" + reservaId + "/pagar-con-tarjeta";

        Resp r = post(ruta, datosTarjeta(), token);

        assertEquals(200, r.estado(), String.valueOf(r.cuerpo()));
        assertEquals("RECHAZADO", r.objeto("datos").get("estadoPago"));
        assertEquals("La tarjeta no tiene fondos suficientes", r.objeto("datos").get("motivo"));
        assertEquals("PENDIENTE_PAGO", estadoReserva(reservaId));

        mercadoPagoCobra("779", "approved", "accredited");
        Resp reintento = post(ruta, datosTarjeta(), token);
        assertEquals("PAGADA", reintento.objeto("datos").get("estadoReserva"));
    }

    @Test
    @DisplayName("Tarjeta: valida sesión, dueño, datos, estado de la reserva y configuración")
    void pagarConTarjetaValidaDatos() {
        mercadoPagoCobra("777", "approved", "accredited");
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
        when(pasarela.clavePublica()).thenReturn("APP_USR-clave-publica-de-prueba");

        Resp r = get("/api/pagos/config", null);

        assertEquals(200, r.estado(), String.valueOf(r.cuerpo()));
        assertEquals("APP_USR-clave-publica-de-prueba", r.objeto("datos").get("publicKey"));
        assertEquals(false, r.objeto("datos").get("pagoEnPagina"));
        when(pasarela.clavePublica()).thenReturn(null);
        assertEquals(503, get("/api/pagos/config", null).estado());
    }

    private String estadoReserva(long id) {
        return bd().uno("SELECT estado FROM reservas WHERE id = ?", id).texto("estado");
    }

    private long cantidadPagos(String idExterno) {
        return bd().uno("SELECT count(*) AS n FROM pagos WHERE id_externo = ?", idExterno).entero("n");
    }

    // ------------------------------------------------------------ Criterio 1: ir a pagar
    @Test
    @DisplayName("C1: pagar crea el pago en Mercado Pago y devuelve la dirección de pago")
    void pagarDevuelveLaUrl() {
        mercadoPagoCrea("pref-1");

        Resp r = post("/api/reservas/" + reservaId + "/pagar", Json.obj(), token);

        assertEquals(200, r.estado(), String.valueOf(r.cuerpo()));
        assertEquals("https://mercadopago.test/pagar/pref-1", r.objeto("datos").get("urlPago"));
        verify(pasarela).crearPreferencia(eq(String.valueOf(reservaId)), anyString(), any(BigDecimal.class), any());
        Map<String, Object> pago = bd().uno("SELECT * FROM pagos WHERE reserva_id = ?", reservaId);
        assertEquals("PENDIENTE", pago.get("estado"));
        assertEquals("pref-1", pago.get("preferencia_id"));
        assertEquals("PENDIENTE_PAGO", estadoReserva(reservaId));
    }

    @Test
    @DisplayName("C1: solo el dueño de la reserva puede pagarla; sin sesión no se puede")
    void pagarReservaAjenaOSinSesion() {
        mercadoPagoCrea("pref-1");
        String otro = token(crearCliente(), false);

        assertEquals(404, post("/api/reservas/" + reservaId + "/pagar", Json.obj(), otro).estado());
        assertEquals(401, post("/api/reservas/" + reservaId + "/pagar", Json.obj(), null).estado());
        assertEquals(0L, bd().uno("SELECT count(*) AS n FROM pagos").entero("n"));
    }

    @Test
    @DisplayName("C1: una reserva que ya no está pendiente no se puede pagar")
    void pagarReservaYaPagada() {
        mercadoPagoCrea("pref-1");
        bd().ejecutar("UPDATE reservas SET estado = 'PAGADA' WHERE id = ?", reservaId);

        assertEquals(409, post("/api/reservas/" + reservaId + "/pagar", Json.obj(), token).estado());
    }

    @Test
    @DisplayName("C1: si el pago no está configurado, responde 503")
    void pagoNoConfigurado() {
        when(pasarela.configurada()).thenReturn(false);

        assertEquals(503, post("/api/reservas/" + reservaId + "/pagar", Json.obj(), token).estado());
    }

    // ------------------------------------------------------------ Criterio 2: pago aprobado
    @Test
    @DisplayName("C2: cuando Mercado Pago aprueba el pago, la reserva queda PAGADA")
    void pagoAprobadoConfirmaLaReserva() {
        mercadoPagoInforma("555", "approved", "accredited", reservaId, "200.00");

        assertEquals(200, avisoDeMercadoPago("555").estado());

        assertEquals("PAGADA", estadoReserva(reservaId));
        Map<String, Object> pago = bd().uno("SELECT * FROM pagos WHERE id_externo = '555'");
        assertEquals("APROBADO", pago.get("estado"));
        assertNotNull(pago.get("pagado_en"));
        Map<String, Object> estado = get("/api/reservas/" + reservaId + "/pago", token).objeto("datos");
        assertEquals("PAGADA", estado.get("estadoReserva"));
        assertEquals("APROBADO", estado.get("estadoPago"));
    }

    @Test
    @DisplayName("C2: al volver de Mercado Pago, la verificación confirma el pago sin esperar el aviso")
    void verificarAlVolver() {
        mercadoPagoInforma("555", "approved", "accredited", reservaId, "200.00");

        Resp r = post("/api/reservas/" + reservaId + "/pago/verificar", Json.obj("paymentId", "555"), token);

        assertEquals(200, r.estado(), String.valueOf(r.cuerpo()));
        assertEquals("PAGADA", r.objeto("datos").get("estadoReserva"));
        assertEquals("PAGADA", estadoReserva(reservaId));
    }

    @Test
    @DisplayName("C2: la verificación valida dueño, formato y que el pago sea de esa reserva")
    void verificarValidaDatos() {
        long otraReserva = insertarReserva("PENDIENTE_PAGO", Instant.now().plus(2, ChronoUnit.HOURS), 10);
        mercadoPagoInforma("555", "approved", "accredited", otraReserva, "200.00");
        String otroCliente = token(crearCliente(), false);

        assertEquals(404, post("/api/reservas/" + reservaId + "/pago/verificar", Json.obj("paymentId", "555"), otroCliente).estado());
        assertEquals(400, post("/api/reservas/" + reservaId + "/pago/verificar", Json.obj("paymentId", "abc"), token).estado());
        assertEquals(409, post("/api/reservas/" + reservaId + "/pago/verificar", Json.obj("paymentId", "555"), token).estado());
        assertEquals("PENDIENTE_PAGO", estadoReserva(reservaId));
        assertEquals("PENDIENTE_PAGO", estadoReserva(otraReserva));
    }

    // ------------------------------------------------------------ Criterio 3: pago rechazado
    @Test
    @DisplayName("C3: si el pago es rechazado, la reserva sigue pendiente, se informa el motivo y se puede reintentar")
    void pagoRechazado() {
        mercadoPagoInforma("556", "rejected", "cc_rejected_insufficient_amount", reservaId, "200.00");

        assertEquals(200, avisoDeMercadoPago("556").estado());

        assertEquals("PENDIENTE_PAGO", estadoReserva(reservaId));
        assertEquals("RECHAZADO", bd().uno("SELECT estado FROM pagos WHERE id_externo = '556'").texto("estado"));
        Map<String, Object> estado = get("/api/reservas/" + reservaId + "/pago", token).objeto("datos");
        assertEquals("RECHAZADO", estado.get("estadoPago"));
        assertEquals("La tarjeta no tiene fondos suficientes", estado.get("motivo"));

        mercadoPagoCrea("pref-2");
        assertEquals(200, post("/api/reservas/" + reservaId + "/pagar", Json.obj(), token).estado());
    }

    // ------------------------------------------------------------ Criterio 4: aviso repetido
    @Test
    @DisplayName("C4: si Mercado Pago avisa dos veces del mismo pago, se registra uno solo")
    void avisoRepetido() {
        mercadoPagoInforma("555", "approved", "accredited", reservaId, "200.00");

        assertEquals(200, avisoDeMercadoPago("555").estado());
        assertEquals(200, avisoDeMercadoPago("555").estado());

        assertEquals(1L, cantidadPagos("555"));
        assertEquals("PAGADA", estadoReserva(reservaId));
    }

    @Test
    @DisplayName("C4: se ignoran los avisos de pagos desconocidos, de otra reserva inexistente o con otro monto")
    void avisosQueSeIgnoran() {
        mercadoPagoInforma("557", "approved", "accredited", 999999, "200.00");
        mercadoPagoInforma("558", "approved", "accredited", reservaId, "1.00");

        assertEquals(200, avisoDeMercadoPago("556").estado()); // Mercado Pago no lo conoce
        assertEquals(200, avisoDeMercadoPago("557").estado());
        assertEquals(200, avisoDeMercadoPago("558").estado());
        assertEquals(200, post("/api/pagos/webhook", Json.obj("type", "otra-cosa"), null).estado());

        assertEquals("PENDIENTE_PAGO", estadoReserva(reservaId));
        assertEquals(0L, bd().uno("SELECT count(*) AS n FROM pagos").entero("n"));
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
        mercadoPagoCrea("pref-1");
        bd().ejecutar("UPDATE reservas SET expira_pago_en = now() - interval '1 minute' WHERE id = ?", reservaId);

        assertEquals(409, post("/api/reservas/" + reservaId + "/pagar", Json.obj(), token).estado());
        assertEquals("CANCELADA", estadoReserva(reservaId));
    }
}