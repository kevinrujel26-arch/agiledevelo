package alquiler.comprobantes;

import alquiler.config.Config;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class TareaComprobantes {

    private static final Logger LOG = LoggerFactory.getLogger(TareaComprobantes.class);

    private final ComprobanteServicio servicio;
    private final Config config;

    public TareaComprobantes(ComprobanteServicio servicio, Config config) {
        this.servicio = servicio;
        this.config = config;
    }

    @Scheduled(fixedDelay = 60_000, initialDelay = 60_000)
    public void reintentar() {
        if (config.esPrueba) return;
        try {
            int n = servicio.reintentarPendientes();
            if (n > 0) LOG.info("{} comprobante(s) reintentados", n);
        } catch (RuntimeException e) {
            LOG.warn("No se pudieron reintentar los comprobantes: {}", e.getMessage());
        }
    }
}
