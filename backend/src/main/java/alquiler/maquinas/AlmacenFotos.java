package alquiler.maquinas;

/**
 * Dónde se guardan las fotos de las máquinas (HU-08). Hay dos formas:
 *
 *  - {@link AlmacenCloudinary}: la nube (permanente). Es la que usa producción.
 *  - {@link AlmacenDisco}: una carpeta local. Solo para las pruebas automáticas
 *    y para desarrollar sin cuenta de Cloudinary.
 *
 * La elección se hace en {@code alquiler.ConfiguracionApp}.
 */
public interface AlmacenFotos {

    byte[] FIRMA_PNG = {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A};

    /** Guarda la foto y devuelve la URL con la que se mostrará (completa o que empieza con /uploads/). */
    String guardar(byte[] datos, String tipoMime);

    /** Borra una foto a partir de su URL. No falla si ya no existe. */
    void borrar(String url);

    /**
     * El tipo que declara el navegador se puede falsificar, así que además
     * revisamos los primeros bytes del archivo ("firma mágica").
     */
    static boolean esImagenReal(byte[] datos, String tipoMime) {
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
}
