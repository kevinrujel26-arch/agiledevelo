package pe.upao.alquiler.bd;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

/**
 * Aplica en orden los archivos .sql de database/migrations que aún no se
 * aplicaron (EN-02). Guarda cuáles se aplicaron en la tabla schema_migraciones.
 */
public final class Migraciones {

    private Migraciones() {
    }

    /**
     * @param reiniciar si es true BORRA todas las tablas antes (solo desarrollo y pruebas)
     * @return cuántas migraciones nuevas se aplicaron
     */
    public static int migrar(Bd bd, Path carpeta, boolean reiniciar, boolean silencioso) {
        if (!Files.isDirectory(carpeta)) {
            throw new IllegalStateException("No existe la carpeta de migraciones: " + carpeta
                    + ". Ejecuta la aplicación desde la carpeta backend o define MIGRATIONS_DIR");
        }
        List<Path> archivos;
        try (Stream<Path> lista = Files.list(carpeta)) {
            archivos = lista.filter(p -> p.getFileName().toString().endsWith(".sql")).sorted().toList();
        } catch (IOException e) {
            throw new IllegalStateException("No se pudo leer " + carpeta, e);
        }

        return bd.transaccion(tx -> {
            if (reiniciar) {
                if (!silencioso) System.out.println("[!] Borrando el esquema public...");
                bd.ejecutarScript(tx.conexion(), "DROP SCHEMA public CASCADE; CREATE SCHEMA public;");
            }
            tx.ejecutar("""
                    CREATE TABLE IF NOT EXISTS schema_migraciones (
                      nombre      VARCHAR(200) PRIMARY KEY,
                      aplicada_en TIMESTAMPTZ NOT NULL DEFAULT now()
                    )""");
            int nuevas = 0;
            for (Path archivo : archivos) {
                String nombre = archivo.getFileName().toString();
                if (tx.uno("SELECT 1 FROM schema_migraciones WHERE nombre = ?", nombre) != null) continue;
                if (!silencioso) System.out.println("-> Aplicando " + nombre);
                try {
                    bd.ejecutarScript(tx.conexion(), Files.readString(archivo, StandardCharsets.UTF_8));
                } catch (Exception e) {
                    throw new IllegalStateException("Falló la migración " + nombre + ": " + e.getMessage(), e);
                }
                tx.ejecutar("INSERT INTO schema_migraciones (nombre) VALUES (?)", nombre);
                nuevas++;
            }
            if (!silencioso) {
                System.out.println(nuevas > 0 ? "[OK] " + nuevas + " migración(es) aplicada(s)" : "[OK] La base de datos ya estaba al día");
            }
            return nuevas;
        });
    }
}
