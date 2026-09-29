package alquiler;

import alquiler.bd.Bd;
import alquiler.bd.Migraciones;
import alquiler.config.Config;
import alquiler.maquinas.AlmacenCloudinary;
import alquiler.maquinas.AlmacenDisco;
import alquiler.maquinas.AlmacenFotos;
import alquiler.seguridad.Contrasenas;
import alquiler.seguridad.Jwt;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.boot.web.servlet.server.ConfigurableServletWebServerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

/**
 * Los objetos "base" de la aplicación (configuración, base de datos, JWT,
 * contraseñas, fotos). Los servicios, repositorios y controladores se
 * registran solos con @Service, @Repository y @RestController.
 */
@Configuration
public class ConfiguracionApp {

    /** Variables de entorno / propiedades de Spring + archivo backend/.env */
    @Bean
    public Config config(Environment entorno) {
        return Config.desde(entorno::getProperty);
    }

    /** Pool de conexiones propio (JDBC). Al arrancar aplica las migraciones pendientes. */
    @Bean(destroyMethod = "close")
    public Bd bd(Config config) {
        Bd bd = new Bd(config);
        if (config.migrarAlIniciar) {
            Migraciones.migrar(bd, config.dirMigraciones, false, false);
        }
        return bd;
    }

    @Bean
    public Jwt jwt(Config config) {
        return new Jwt(config.jwtSecreto);
    }

    @Bean
    public Contrasenas contrasenas(Config config) {
        return new Contrasenas(config.iteracionesContrasena);
    }

    /**
     * Producción usa Cloudinary. Las pruebas y el desarrollo sin credenciales
     * usan una carpeta local (así las pruebas no dependen de internet).
     */
    @Bean
    public AlmacenFotos almacenFotos(Config config) {
        boolean cloudinaryConfigurado = config.cloudinaryCloudName != null
                && config.cloudinaryApiKey != null && config.cloudinaryApiSecret != null;
        if (config.esPrueba) return new AlmacenDisco(config);
        if (cloudinaryConfigurado || "production".equals(config.entorno)) return new AlmacenCloudinary(config);
        return new AlmacenDisco(config);
    }

    /** El puerto sale de PORT / backend/.env (Render define PORT). En pruebas lo elige Spring. */
    @Bean
    public WebServerFactoryCustomizer<ConfigurableServletWebServerFactory> puerto(Config config) {
        return fabrica -> {
            if (!config.esPrueba) fabrica.setPort(config.puerto);
        };
    }
}
