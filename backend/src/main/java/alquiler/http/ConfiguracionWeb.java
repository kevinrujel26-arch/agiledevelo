package alquiler.http;

import alquiler.config.Config;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.CacheControl;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.List;
import java.util.concurrent.TimeUnit;

/** Ajustes de Spring MVC: cómo se escribe el JSON y de dónde salen las fotos de /uploads. */
@Configuration
public class ConfiguracionWeb implements WebMvcConfigurer {

    private final Config config;

    public ConfiguracionWeb(Config config) {
        this.config = config;
    }

    /** Nuestro convertidor va primero: todas las respuestas usan el mismo formato JSON que antes. */
    @Override
    public void extendMessageConverters(List<HttpMessageConverter<?>> convertidores) {
        convertidores.add(0, new ConvertidorJson());
    }

    /** HU-08: sirve las fotos guardadas en disco. Spring evita salir de la carpeta (../). */
    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registro) {
        String carpeta = config.dirSubidas.toUri().toString();
        if (!carpeta.endsWith("/")) carpeta += "/";
        registro.addResourceHandler("/uploads/**")
                .addResourceLocations(carpeta)
                .setCacheControl(CacheControl.maxAge(7, TimeUnit.DAYS).cachePublic());
    }
}
