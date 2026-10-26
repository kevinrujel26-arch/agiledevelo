package alquiler.pagos;

import alquiler.config.Config;
import alquiler.reservas.ReservaHoraServicio;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

/** HU-07 criterio 5: cancela las reservas que no se pagaron dentro de las 2 horas y libera el horario. */
@Configuration
@EnableScheduling
public class TareaExpiracion {

    private static final Logger LOG = LoggerFactory.getLogger(TareaExpiracion.class);

    private final ReservaHoraServicio reservas;
    private final Config config;

    public TareaExpiracion(ReservaHoraServicio reservas, Config config) {
        this.reservas = reservas;
        this.config = config;
    }

    @Scheduled(fixedDelay = 60_000, initialDelay = 30_000)
    public void cancelarReservasVencidas() {
        if (config.esPrueba) return; // las pruebas llaman a cancelarVencidas() cuando lo necesitan
        try {
            int n = reservas.cancelarVencidas();
            if (n > 0) LOG.info("{} reserva(s) canceladas por falta de pago", n);
        } catch (RuntimeException e) {
            LOG.warn("No se pudieron cancelar las reservas vencidas: {}", e.getMessage());
        }
    }
}