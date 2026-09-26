package alquiler.maquinas;

import alquiler.config.Config;
import alquiler.json.Json;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Sube y borra las fotos de las máquinas en Cloudinary (HU-08), en vez de
 * guardarlas en el disco del servidor: en Render (y en cualquier hosting
 * gratuito similar) el disco se reinicia en cada despliegue, así que un
 * archivo guardado localmente se pierde tarde o temprano. Cloudinary las
 * guarda aparte, con una URL pública permanente.
 *
 * No usa el SDK oficial de Cloudinary (para no agregar dependencias):
 * llama directamente a su API HTTP con las clases del propio JDK.
 */
public class AlmacenFotos {

    private static final byte[] FIRMA_PNG = {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A};
    private static final String CARPETA = "maquinas";

    private final String cloudName;
    private final String apiKey;
    private final String apiSecret;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15)).build();

    public AlmacenFotos(Config config) {
        this.cloudName = config.cloudinaryCloudName;
        this.apiKey = config.cloudinaryApiKey;
        this.apiSecret = config.cloudinaryApiSecret;
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

    /** Sube la foto a Cloudinary y devuelve su URL pública y permanente (https://...). */
    public String guardar(byte[] datos, String tipoMime) {
        exigirConfigurado();
        long timestamp = Instant.now().getEpochSecond();
        // Cloudinary exige firmar los parámetros en orden alfabético (sin "file" ni "api_key").
        String firma = sha1Hex("folder=" + CARPETA + "&timestamp=" + timestamp + apiSecret);
        String extension = "image/png".equals(tipoMime) ? "png" : "jpg";
        String nombreArchivo = UUID.randomUUID() + "." + extension;

        String boundary = "----maquiRenta" + UUID.randomUUID();
        byte[] cuerpo = multipart(boundary, Map.of(
                "api_key", apiKey,
                "timestamp", String.valueOf(timestamp),
                "folder", CARPETA,
                "signature", firma
        ), nombreArchivo, tipoMime, datos);

        HttpRequest peticion = HttpRequest.newBuilder()
                .uri(URI.create("https://api.cloudinary.com/v1_1/" + cloudName + "/image/upload"))
                .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .POST(BodyPublishers.ofByteArray(cuerpo))
                .build();

        Map<String, Object> respuesta = enviar(peticion);
        Object url = respuesta.get("secure_url");
        if (url == null) {
            Object error = respuesta.get("error");
            String mensaje = error instanceof Map<?, ?> m ? String.valueOf(m.get("message")) : "respuesta inesperada de Cloudinary";
            throw new UncheckedIOException(new IOException("No se pudo subir la foto a Cloudinary: " + mensaje));
        }
        return url.toString();
    }

    /** Borra una foto a partir de su URL pública de Cloudinary. No falla si ya no existe. */
    public void borrar(String urlPublica) {
        if (urlPublica == null || cloudName == null) return;
        String publicId = idPublico(urlPublica);
        if (publicId == null) return; // no es una URL de Cloudinary reconocible; no hay nada que borrar

        long timestamp = Instant.now().getEpochSecond();
        String firma = sha1Hex("public_id=" + publicId + "&timestamp=" + timestamp + apiSecret);
        String cuerpo = "public_id=" + urlEncode(publicId)
                + "&api_key=" + urlEncode(apiKey)
                + "&timestamp=" + timestamp
                + "&signature=" + firma;

        HttpRequest peticion = HttpRequest.newBuilder()
                .uri(URI.create("https://api.cloudinary.com/v1_1/" + cloudName + "/image/destroy"))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(BodyPublishers.ofString(cuerpo, StandardCharsets.UTF_8))
                .build();
        try {
            http.send(peticion, HttpResponse.BodyHandlers.discarding());
        } catch (IOException ignorado) {
            // no es crítico: en el peor caso queda una imagen huérfana en Cloudinary
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    // ------------------------------------------------------------------
    private void exigirConfigurado() {
        if (cloudName == null || apiKey == null || apiSecret == null) {
            throw new IllegalStateException(
                    "Cloudinary no está configurado. Define CLOUDINARY_CLOUD_NAME, CLOUDINARY_API_KEY y "
                            + "CLOUDINARY_API_SECRET (backend/.env en desarrollo, variables de entorno en Render).");
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> enviar(HttpRequest peticion) {
        try {
            HttpResponse<String> respuesta = http.send(peticion, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            Object cuerpo = Json.leer(respuesta.body());
            return cuerpo instanceof Map<?, ?> m ? (Map<String, Object>) m : Map.of();
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo conectar con Cloudinary", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new UncheckedIOException("Subida a Cloudinary interrumpida", new IOException(e));
        }
    }

    /** Extrae el "public_id" (carpeta/nombre-sin-extensión) de una URL de Cloudinary. */
    private static String idPublico(String url) {
        int marca = url.indexOf("/upload/");
        if (marca < 0) return null;
        String resto = url.substring(marca + "/upload/".length());
        // Cloudinary agrega un prefijo de versión "v1234567890/"; lo quitamos si está.
        if (resto.matches("^v\\d+/.*")) resto = resto.substring(resto.indexOf('/') + 1);
        int punto = resto.lastIndexOf('.');
        return punto > 0 ? resto.substring(0, punto) : resto;
    }

    private static String sha1Hex(String texto) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-1").digest(texto.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(hash.length * 2);
            for (byte b : hash) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e); // SHA-1 siempre está disponible en el JDK
        }
    }

    private static String urlEncode(String texto) {
        return URLEncoder.encode(texto, StandardCharsets.UTF_8);
    }

    /** Construye un cuerpo multipart/form-data con campos de texto + un archivo. */
    private static byte[] multipart(String boundary, Map<String, String> campos, String nombreArchivo, String tipoMime, byte[] datosArchivo) {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            for (Map.Entry<String, String> e : campos.entrySet()) {
                out.write(("--" + boundary + "\r\n").getBytes(StandardCharsets.UTF_8));
                out.write(("Content-Disposition: form-data; name=\"" + e.getKey() + "\"\r\n\r\n").getBytes(StandardCharsets.UTF_8));
                out.write((e.getValue() + "\r\n").getBytes(StandardCharsets.UTF_8));
            }
            out.write(("--" + boundary + "\r\n").getBytes(StandardCharsets.UTF_8));
            out.write(("Content-Disposition: form-data; name=\"file\"; filename=\"" + nombreArchivo + "\"\r\n").getBytes(StandardCharsets.UTF_8));
            out.write(("Content-Type: " + tipoMime + "\r\n\r\n").getBytes(StandardCharsets.UTF_8));
            out.write(datosArchivo);
            out.write(("\r\n--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e); // ByteArrayOutputStream nunca falla al escribir
        }
    }
}
