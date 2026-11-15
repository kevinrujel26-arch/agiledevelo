package alquiler.pagos;

import alquiler.config.Config;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class PasarelaConfig {

    @Bean
    public PasarelaPago pasarela(Config config) {
        return "culqi".equals(config.pasarela) ? new PasarelaCulqi(config) : new PasarelaMercadoPago(config);
    }
}
