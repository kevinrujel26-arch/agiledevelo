package alquiler.pagos;

import alquiler.bd.Bd;
import alquiler.bd.Fila;
import alquiler.json.Json;
import alquiler.reservas.ReservaHoraRepositorio;
import alquiler.util.ErrorApp;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Map;

/** HU-07: pagar una reserva pendiente con Mercado Pago. */
@Service
public class PagoServicio {

    private static final Logger LOG = LoggerFactory.getLogger(PagoServicio.class);
    private static final String MOTIVO_GENERICO =
            "El pago fue rechazado. Revisa los datos de tu tarjeta o prueba con otro medio de pago";

    private final Bd bd;
    private final PasarelaPago pasarela;
    private final ReservaHoraRepositorio reservas;

    public PagoServicio(Bd bd, PasarelaPago pasarela, ReservaHoraRepositorio reservas) {
        this.bd = bd;
        this.pasarela = pasarela;
        this.reservas = reservas;
    }

    /** Criterio 1: crea el pago en Mercado Pago y devuelve la dirección a la que hay que mandar al cliente. */
    public Map<String, Object> iniciarPago(long usuarioId, long reservaId) {
        if (!pasarela.configurada()) {
            throw new ErrorApp(503, "El pago en línea no está disponible por ahora");
        }
        Fila r = reservaDelCliente(usuarioId, reservaId);
        if (!"PENDIENTE_PAGO".equals(r.texto("estado"))) {
            throw ErrorApp.conflicto("La reserva ya no está pendiente de pago");
        }
        Instant vence = r.instante("expira_pago_en");
        if (vence != null && !vence.isAfter(Instant.now())) {
            reservas.cancelarVencidas();
            throw ErrorApp.conflicto("Pasaron más de 2 horas sin pagar: la reserva fue cancelada y el horario liberado");
        }

        PasarelaPago.Preferencia p = pasarela.crearPreferencia(
                String.valueOf(reservaId),
                "Reserva " + r.texto("codigo") + " - " + r.texto("maquina_nombre"),
                r.decimal("monto_total"),
                vence);
        bd.ejecutar("""
                INSERT INTO pagos (reserva_id, proveedor, preferencia_id, estado, monto)
                VALUES (?, 'MERCADO_PAGO', ?, 'PENDIENTE', ?)""",
                reservaId, p.id(), r.decimal("monto_total"));
        return Json.obj("urlPago", p.urlPago());
    }

    /**
     * Criterios 2, 3 y 4: Mercado Pago avisa (webhook) o el cliente vuelve de pagar. Se consulta el pago a
     * Mercado Pago (nunca se confía en lo que llega en la petición) y se actualiza la reserva.
     * Un aviso repetido actualiza el mismo pago, no crea otro. Devuelve false si el pago no es de una reserva nuestra.
     */
    public boolean registrarPago(String idPago, Long reservaEsperada) {
        PasarelaPago.Pago info = pasarela.consultarPago(idPago);
        if (info == null) return false;
        Long idReserva = idDeReserva(info.referencia());
        if (idReserva == null) return false;
        final long reservaId = idReserva;
        if (reservaEsperada != null && reservaEsperada != reservaId) {
            throw ErrorApp.conflicto("El pago no corresponde a esta reserva");
        }

        String estadoPago = switch (info.estado() == null ? "" : info.estado()) {
            case "approved" -> "APROBADO";
            case "rejected", "cancelled" -> "RECHAZADO";
            case "refunded", "charged_back" -> "REEMBOLSADO";
            default -> "PENDIENTE";
        };

        return bd.transaccion(tx -> {
            Fila r = tx.uno("SELECT * FROM reservas WHERE id = ? FOR UPDATE", reservaId);
            if (r == null) return false;
            if (info.monto() == null || info.monto().compareTo(r.decimal("monto_total")) != 0) {
                LOG.warn("Pago {} ignorado: el monto no coincide con la reserva {}", idPago, reservaId);
                return false;
            }

            tx.ejecutar("""
                    INSERT INTO pagos (reserva_id, proveedor, id_externo, estado, monto, respuesta_proveedor, pagado_en)
                    VALUES (?, 'MERCADO_PAGO', ?, ?, ?, ?, ?)
                    ON CONFLICT (proveedor, id_externo) WHERE id_externo IS NOT NULL
                    DO UPDATE SET estado = EXCLUDED.estado,
                                  respuesta_proveedor = EXCLUDED.respuesta_proveedor,
                                  pagado_en = COALESCE(pagos.pagado_en, EXCLUDED.pagado_en)""",
                    reservaId, info.id(), estadoPago, r.decimal("monto_total"),
                    Json.escribir(Json.obj("estado", info.estado(), "detalle", info.detalle())),
                    "APROBADO".equals(estadoPago) ? Instant.now() : null);

            // El registro provisional que dejó "Pagar ahora" ya no hace falta: queda el pago real
            tx.ejecutar("DELETE FROM pagos WHERE reserva_id = ? AND id_externo IS NULL AND estado = 'PENDIENTE'", reservaId);

            if ("APROBADO".equals(estadoPago)) {
                if ("PENDIENTE_PAGO".equals(r.texto("estado"))) {
                    tx.ejecutar("UPDATE reservas SET estado = 'PAGADA' WHERE id = ?", reservaId);
                } else if ("CANCELADA".equals(r.texto("estado"))) {
                    LOG.warn("Pago {} aprobado para la reserva {} que ya estaba cancelada: hay que reembolsarlo", idPago, reservaId);
                }
            }
            return true;
        });
    }

    /** Estado actual de la reserva y de su último pago (lo usa la pantalla a la que vuelve el cliente). */
    public Map<String, Object> estado(long usuarioId, long reservaId) {
        Fila r = reservaDelCliente(usuarioId, reservaId);
        Fila p = bd.uno("""
                SELECT estado, respuesta_proveedor FROM pagos
                 WHERE reserva_id = ? AND id_externo IS NOT NULL
                 ORDER BY actualizado_en DESC, id DESC LIMIT 1""", reservaId);
        String estadoPago = p == null ? null : p.texto("estado");
        String motivo = null;
        if ("RECHAZADO".equals(estadoPago)) {
            Object respuesta = p.json("respuesta_proveedor");
            String detalle = respuesta instanceof Map<?, ?> m && m.get("detalle") != null ? m.get("detalle").toString() : null;
            motivo = motivoRechazo(detalle);
        }
        return Json.obj(
                "reservaId", reservaId,
                "codigo", r.texto("codigo"),
                "total", r.decimal("monto_total"),
                "estadoReserva", r.texto("estado"),
                "estadoPago", estadoPago,
                "motivo", motivo);
    }

    /** Pago dentro de la página: el formulario de tarjeta de Mercado Pago entrega un token y aquí se crea el pago. */
    public Map<String, Object> pagarConTarjeta(long usuarioId, long reservaId, PasarelaPago.DatosTarjeta tarjeta) {
        if (!pasarela.configurada()) {
            throw new ErrorApp(503, "El pago en línea no está disponible por ahora");
        }
        Fila r = reservaDelCliente(usuarioId, reservaId);
        if (!"PENDIENTE_PAGO".equals(r.texto("estado"))) {
            throw ErrorApp.conflicto("La reserva ya no está pendiente de pago");
        }
        Instant vence = r.instante("expira_pago_en");
        if (vence != null && !vence.isAfter(Instant.now())) {
            reservas.cancelarVencidas();
            throw ErrorApp.conflicto("Pasaron más de 2 horas sin pagar: la reserva fue cancelada y el horario liberado");
        }
        // El monto sale de la reserva, nunca de lo que mande el navegador
        PasarelaPago.Pago creado = pasarela.pagarConTarjeta(
                String.valueOf(reservaId),
                "Reserva " + r.texto("codigo") + " - " + r.texto("maquina_nombre"),
                r.decimal("monto_total"),
                tarjeta);
        if (creado == null || creado.id() == null) {
            throw new ErrorApp(502, "No se pudo procesar el pago. Intenta nuevamente");
        }
        registrarPago(creado.id(), reservaId);
        return estado(usuarioId, reservaId);
    }

    /** Clave pública para el formulario de tarjeta; 503 si no está configurada. */
    public String clavePublica() {
        String clave = pasarela.clavePublica();
        if (clave == null || clave.isBlank()) throw new ErrorApp(503, "El pago en línea no está disponible por ahora");
        return clave;
    }

    private static Long idDeReserva(String referencia) {
        try {
            return referencia == null ? null : Long.valueOf(referencia);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** Confirma que la reserva existe y es de este cliente; si no, 404. */
    private Fila reservaDelCliente(long usuarioId, long reservaId) {
        Fila r = bd.uno("""
                SELECT r.*, m.nombre AS maquina_nombre
                  FROM reservas r JOIN maquinas m ON m.id = r.maquina_id
                 WHERE r.id = ? AND r.cliente_id = ?""", reservaId, usuarioId);
        if (r == null) throw ErrorApp.noEncontrado("La reserva no existe");
        return r;
    }

    /** Traduce el motivo de rechazo de Mercado Pago a un texto para el cliente. */
    static String motivoRechazo(String detalle) {
        if (detalle == null) return MOTIVO_GENERICO;
        return switch (detalle) {
            case "cc_rejected_insufficient_amount" -> "La tarjeta no tiene fondos suficientes";
            case "cc_rejected_bad_filled_security_code" -> "El código de seguridad de la tarjeta es incorrecto";
            case "cc_rejected_bad_filled_date" -> "La fecha de vencimiento de la tarjeta es incorrecta";
            case "cc_rejected_bad_filled_other" -> "Revisa los datos de la tarjeta";
            case "cc_rejected_call_for_authorize" -> "Debes autorizar el pago con tu banco";
            case "cc_rejected_card_disabled" -> "La tarjeta está inactiva. Actívala o usa otra";
            case "cc_rejected_max_attempts" -> "Llegaste al límite de intentos. Prueba con otra tarjeta";
            default -> MOTIVO_GENERICO;
        };
    }
}