package pe.upao.alquiler.maquinas;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

/** Guarda y borra las fotos en disco, en la carpeta uploads/maquinas (HU-08). */
public class AlmacenFotos {

    private static final byte[] FIRMA_PNG = {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A};

    private final Path carpeta;

    public AlmacenFotos(Path dirSubidas) {
        this.carpeta = dirSubidas.resolve("maquinas");
        try {
            Files.createDirectories(carpeta);
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo crear la carpeta de fotos " + carpeta, e);
        }
    }

    /**
     * El tipo que declara el navegador se puede falsificar, así que además
     * revisamos los primeros bytes del archivo ("firma mágica").
     */
    public static boolean esImagenReal(byte[] datos, String tipoMime) {
        if ("image/jpeg".equals(tipoMime)) {
            return datos.length >= 3 && (datos[0] & 0xFF) == 0xFF && (datos[1] & 0xFF) == 0xD8 && (datos[2] & 0xFF) == 0xFF;
        }
        if ("image/png".equals(tipoMime)) {
            if (datos.length < FIRMA_PNG.length) return false;
            for (int i = 0; i < FIRMA_PNG.length; i++) {
                if (datos[i] != FIRMA_PNG[i]) return false;
            }
            return true;
        }
        return false;
    }

    /** Guarda el archivo con un nombre aleatorio y devuelve su ruta pública (/uploads/maquinas/...). */
    public String guardar(byte[] datos, String tipoMime) {
        String extension = "image/png".equals(tipoMime) ? ".png" : ".jpg";
        String nombre = UUID.randomUUID() + extension;
        try {
            Files.write(carpeta.resolve(nombre), datos);
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo guardar la foto", e);
        }
        return "/uploads/maquinas/" + nombre;
    }

    /** Borra una foto a partir de su ruta pública. No falla si ya no existe. */
    public void borrar(String rutaPublica) {
        if (rutaPublica == null) return;
        Path archivo = carpeta.resolve(Path.of(rutaPublica).getFileName().toString()).normalize();
        if (!archivo.startsWith(carpeta)) return;
        try {
            Files.deleteIfExists(archivo);
        } catch (IOException ignorado) {
            // no es crítico
        }
    }
}
