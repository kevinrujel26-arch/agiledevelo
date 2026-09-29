package alquiler.maquinas;

import alquiler.config.Config;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

/**
 * Guarda las fotos en una carpeta del servidor (UPLOAD_DIR) y las sirve en /uploads/.
 * Se usa en las pruebas automáticas y en desarrollo sin Cloudinary. En Render el
 * disco se borra en cada despliegue, por eso allí se usa {@link AlmacenCloudinary}.
 */
public class AlmacenDisco implements AlmacenFotos {

    private static final String PREFIJO = "/uploads/maquinas/";

    private final Path carpeta;

    public AlmacenDisco(Config config) {
        this.carpeta = config.dirSubidas.resolve("maquinas");
    }

    @Override
    public String guardar(byte[] datos, String tipoMime) {
        String extension = "image/png".equals(tipoMime) ? "png" : "jpg";
        String nombre = UUID.randomUUID() + "." + extension;
        try {
            Files.createDirectories(carpeta);
            Files.write(carpeta.resolve(nombre), datos);
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo guardar la foto en el disco", e);
        }
        return PREFIJO + nombre;
    }

    @Override
    public void borrar(String url) {
        if (url == null || !url.startsWith(PREFIJO)) return;
        Path archivo = carpeta.resolve(url.substring(PREFIJO.length())).normalize();
        if (!archivo.startsWith(carpeta)) return; // nunca borrar fuera de la carpeta
        try {
            Files.deleteIfExists(archivo);
        } catch (IOException ignorado) {
            // no es crítico
        }
    }
}
