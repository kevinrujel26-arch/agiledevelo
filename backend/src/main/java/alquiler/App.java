package alquiler;

import alquiler.bd.Bd;
import alquiler.bd.Migraciones;
import alquiler.bd.Sembrador;
import alquiler.config.Config;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;

/**
 * Punto de entrada (Spring Boot).
 *
 * <pre>
 *   java -jar alquiler-backend.jar             arranca la API (aplica migraciones pendientes)
 *   java -jar alquiler-backend.jar migrar      solo aplica migraciones
 *   java -jar alquiler-backend.jar sembrar     carga admin, categorías y máquinas de ejemplo
 *   java -jar alquiler-backend.jar reiniciar   BORRA todo, migra y siembra (solo desarrollo)
 * </pre>
 *
 * Los comandos migrar / sembrar / reiniciar no levantan Spring: solo usan la BD.
 */
// Se excluye el usuario en memoria que Spring Security crea por defecto: aquí los
// usuarios viven en la tabla "usuarios" y el login lo hace AuthServicio.
@SpringBootApplication(exclude = UserDetailsServiceAutoConfiguration.class)
public class App {

    public static void main(String[] args) {
        Config config;
        try {
            config = Config.cargar();
        } catch (IllegalStateException e) {
            System.err.println("Error de configuración: " + e.getMessage());
            System.exit(1);
            return;
        }
        String comando = args.length > 0 ? args[0] : "servidor";

        switch (comando) {
            case "migrar" -> {
                try (Bd bd = new Bd(config)) {
                    Migraciones.migrar(bd, config.dirMigraciones, false, false);
                }
            }
            case "sembrar" -> {
                try (Bd bd = new Bd(config)) {
                    Sembrador.sembrar(bd, config);
                }
            }
            case "reiniciar" -> {
                if ("production".equals(config.entorno)) {
                    System.err.println("No se permite reiniciar la base de datos en producción");
                    System.exit(1);
                }
                try (Bd bd = new Bd(config)) {
                    Migraciones.migrar(bd, config.dirMigraciones, true, false);
                    Sembrador.sembrar(bd, config);
                }
            }
            case "servidor" -> SpringApplication.run(App.class, args);
            default -> {
                System.err.println("Comando desconocido: " + comando + ". Usa: servidor | migrar | sembrar | reiniciar");
                System.exit(1);
            }
        }
    }
}
