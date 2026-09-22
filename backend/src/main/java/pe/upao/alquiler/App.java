package pe.upao.alquiler;

import pe.upao.alquiler.bd.Bd;
import pe.upao.alquiler.bd.Migraciones;
import pe.upao.alquiler.bd.Sembrador;
import pe.upao.alquiler.config.Config;

/**
 * Punto de entrada.
 *
 * <pre>
 *   java -jar alquiler-backend.jar             arranca la API (aplica migraciones pendientes)
 *   java -jar alquiler-backend.jar migrar      solo aplica migraciones
 *   java -jar alquiler-backend.jar sembrar     carga admin, categorías y máquinas de ejemplo
 *   java -jar alquiler-backend.jar reiniciar   BORRA todo, migra y siembra (solo desarrollo)
 * </pre>
 */
public final class App {

    private App() {
    }

    public static void main(String[] args) throws Exception {
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
            case "servidor" -> iniciarServidor(config);
            default -> {
                System.err.println("Comando desconocido: " + comando + " (usa: migrar, sembrar, reiniciar)");
                System.exit(1);
            }
        }
    }

    private static void iniciarServidor(Config config) throws Exception {
        Aplicacion app = new Aplicacion(config);
        if (config.migrarAlIniciar) {
            Migraciones.migrar(app.bd(), config.dirMigraciones, false, false);
        }
        app.iniciar(config.puerto);
        System.out.println("API escuchando en http://localhost:" + config.puerto + " (" + config.entorno + ")");
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("Deteniendo la API...");
            app.detener();
        }));
    }
}
