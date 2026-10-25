package alquiler.pagos;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Pasarela de pago (Mercado Pago). Es una interfaz para que las pruebas la
 * puedan simular sin llamar a internet, igual que VerificadorGoogle.
 */
public interface PasarelaPago {

    /** Pago de Checkout Pro: a dónde mandar al cliente para que pague. */
    record Preferencia(String id, String urlPago) {
    }

    /**
     * Pago tal como lo informa Mercado Pago. {@code estado}: approved, rejected, pending, in_process,
     * cancelled, refunded... {@code referencia}: el id de la reserva que enviamos al crear la preferencia.
     */
    record Pago(String id, String estado, String detalle, String referencia, BigDecimal monto) {
    }

    /** false si falta MP_ACCESS_TOKEN: el pago en línea queda desactivado. */
    boolean configurada();

    /** Crea la preferencia de pago. {@code referencia} es el id de la reserva; {@code venceEn} puede ser null. */
    Preferencia crearPreferencia(String referencia, String titulo, BigDecimal monto, Instant venceEn);

    /** Consulta un pago a Mercado Pago. Devuelve null si no existe. */
    Pago consultarPago(String idPago);
}