package alquiler.pagos;

import java.math.BigDecimal;
import java.util.Map;

public interface PasarelaPago {

    String DESAFIO_3DS = "3ds";

    record Pago(String id, String estado, String detalle, String referencia, BigDecimal monto, String urlDesafio) {

        public Pago(String id, String estado, String detalle, String referencia, BigDecimal monto) {
            this(id, estado, detalle, referencia, monto, null);
        }
    }

    boolean configurada();

    Pago consultarPago(String idPago);

    record DatosTarjeta(String token, String metodoPagoId, String emisorId, String correo,
                        String tipoDocumento, String numeroDocumento, String dispositivoId,
                        String nombre, String apellido, String telefono, Map<String, Object> autenticacion3ds) {

        public DatosTarjeta conComprador(String nombre, String apellido, String telefono) {
            return new DatosTarjeta(token, metodoPagoId, emisorId, correo, tipoDocumento, numeroDocumento,
                    dispositivoId, nombre, apellido, telefono, autenticacion3ds);
        }
    }

    Pago pagarConTarjeta(String referencia, String descripcion, BigDecimal monto, DatosTarjeta tarjeta);

    String clavePublica();
}
