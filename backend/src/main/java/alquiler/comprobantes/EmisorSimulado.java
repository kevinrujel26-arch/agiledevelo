package alquiler.comprobantes;

import alquiler.config.Config;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;

@Component
public class EmisorSimulado {

    private final boolean simularFalla;

    public EmisorSimulado(Config config) {
        this.simularFalla = Boolean.parseBoolean(config.valor("COMPROBANTE_SIMULAR_FALLA", "false"));
    }

    public Respuesta emitir(String serie, int numero, String documento, BigDecimal total) {
        if (simularFalla) {
            return new Respuesta(false, null, "Servicio de facturación no disponible");
        }
        String base = serie + "-" + numero + "|" + documento + "|" + total.toPlainString() + "|" + Instant.now();
        try {
            byte[] resumen = MessageDigest.getInstance("SHA-256").digest(base.getBytes(StandardCharsets.UTF_8));
            return new Respuesta(true, HexFormat.of().formatHex(resumen), null);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    public record Respuesta(boolean aceptado, String hash, String motivo) {
    }
}
