package pe.upao.alquiler;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import pe.upao.alquiler.bd.Bd;
import pe.upao.alquiler.bd.Migraciones;
import pe.upao.alquiler.config.Config;
import pe.upao.alquiler.json.Json;
import pe.upao.alquiler.seguridad.Contrasenas;

import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Base de las pruebas de integración: levanta la API real en un puerto libre
 * contra la BD de pruebas (DATABASE_URL_TEST), que se recrea al empezar y se
 * vacía antes de cada prueba.
 */
public abstract class PruebaBase {

    private static Aplicacion app;
    private static String base;
    private static final HttpClient CLIENTE = HttpClient.newHttpClient();
    private static int contador = 0;

    /** PNG y JPG mínimos con la firma correcta de cada formato. */
    protected static final byte[] PNG = {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0};
    protected static final byte[] JPG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0, 0x10, 0x4A, 0x46};

    @BeforeAll
    static synchronized void iniciarApi() throws Exception {
        if (app != null) return;
        Path subidas = Files.createTempDirectory("alquiler-pruebas");
        Config config = Config.paraPruebas(Map.of("UPLOAD_DIR", subidas.toString()));
        app = new Aplicacion(config);
        Migraciones.migrar(app.bd(), config.dirMigraciones, true, true);
        app.iniciar(0);
        base = "http://localhost:" + app.puerto();
    }

    @BeforeEach
    void limpiarBd() {
        bd().ejecutar("""
                TRUNCATE auditoria, solicitudes_reembolso, pagos, reservas, bloqueos_disponibilidad,
                         fotos_maquina, maquinas, categorias, sesiones, usuarios
                RESTART IDENTITY CASCADE""");
    }

    protected static Bd bd() {
        return app.bd();
    }

    // ------------------------------------------------------------------
    // Respuesta HTTP
    // ------------------------------------------------------------------
    protected record Resp(int estado, Object cuerpo) {
        @SuppressWarnings("unchecked")
        public Map<String, Object> json() {
            return (Map<String, Object>) cuerpo;
        }

        public String error() {
            return cuerpo instanceof Map<?, ?> m ? (String) m.get("error") : null;
        }

        @SuppressWarnings("unchecked")
        public List<Map<String, Object>> lista(String campo) {
            return (List<Map<String, Object>>) json().get(campo);
        }

        @SuppressWarnings("unchecked")
        public Map<String, Object> objeto(String campo) {
            return (Map<String, Object>) json().get(campo);
        }
    }

    protected static Resp get(String ruta, String token) {
        return enviar(peticion(ruta, token).GET());
    }

    protected static Resp post(String ruta, Object cuerpo, String token) {
        return enviar(conCuerpo(ruta, token, "POST", cuerpo));
    }

    protected static Resp put(String ruta, Object cuerpo, String token) {
        return enviar(conCuerpo(ruta, token, "PUT", cuerpo));
    }

    protected static Resp patch(String ruta, Object cuerpo, String token) {
        return enviar(conCuerpo(ruta, token, "PATCH", cuerpo));
    }

    protected static Resp delete(String ruta, String token) {
        return enviar(peticion(ruta, token).DELETE());
    }

    /** Un archivo para subir: nombre, tipo MIME y contenido. */
    protected record Archivo(String nombre, String tipo, byte[] datos) {
    }

    /** POST multipart/form-data con los archivos en el campo "fotos". */
    protected static Resp subir(String ruta, String token, Archivo... archivos) {
        String boundary = "----prueba" + UUID.randomUUID();
        ByteArrayOutputStream cuerpo = new ByteArrayOutputStream();
        for (Archivo a : archivos) {
            String cabecera = "--" + boundary + "\r\n"
                    + "Content-Disposition: form-data; name=\"fotos\"; filename=\"" + a.nombre() + "\"\r\n"
                    + "Content-Type: " + a.tipo() + "\r\n\r\n";
            cuerpo.writeBytes(cabecera.getBytes(StandardCharsets.UTF_8));
            cuerpo.writeBytes(a.datos());
            cuerpo.writeBytes("\r\n".getBytes(StandardCharsets.UTF_8));
        }
        cuerpo.writeBytes(("--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));
        HttpRequest.Builder b = peticion(ruta, token)
                .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .POST(HttpRequest.BodyPublishers.ofByteArray(cuerpo.toByteArray()));
        return enviar(b);
    }

    private static HttpRequest.Builder peticion(String ruta, String token) {
        HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(base + ruta));
        if (token != null) b.header("Authorization", "Bearer " + token);
        return b;
    }

    private static HttpRequest.Builder conCuerpo(String ruta, String token, String metodo, Object cuerpo) {
        HttpRequest.BodyPublisher publicador = cuerpo == null
                ? HttpRequest.BodyPublishers.noBody()
                : HttpRequest.BodyPublishers.ofString(Json.escribir(cuerpo), StandardCharsets.UTF_8);
        return peticion(ruta, token).header("Content-Type", "application/json").method(metodo, publicador);
    }

    private static Resp enviar(HttpRequest.Builder b) {
        try {
            HttpResponse<String> r = CLIENTE.send(b.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            Object cuerpo = r.body() == null || r.body().isEmpty() ? null : Json.leer(r.body());
            return new Resp(r.statusCode(), cuerpo);
        } catch (Exception e) {
            throw new IllegalStateException("Falló la petición: " + e.getMessage(), e);
        }
    }

    /** Resultado de descargar un archivo (fotos en /uploads). */
    protected record Descarga(int estado, String tipo, byte[] datos) {
    }

    protected static Descarga descargar(String ruta) {
        try {
            HttpResponse<byte[]> r = CLIENTE.send(peticion(ruta, null).GET().build(), HttpResponse.BodyHandlers.ofByteArray());
            return new Descarga(r.statusCode(), r.headers().firstValue("Content-Type").orElse(""), r.body());
        } catch (Exception e) {
            throw new IllegalStateException("Falló la descarga: " + e.getMessage(), e);
        }
    }

    // ------------------------------------------------------------------
    // Datos de prueba
    // ------------------------------------------------------------------
    protected record Usuario(long id, String correo, String contrasena) {
    }

    protected static Usuario crearUsuario(String rol, boolean activo) {
        contador++;
        String correo = "usuario" + contador + "@prueba.pe";
        String clave = "Clave12345";
        String hash = new Contrasenas(1000).cifrar(clave);
        long id = bd().uno("""
                INSERT INTO usuarios (nombre, correo, contrasena_hash, rol, activo)
                VALUES (?, ?, ?, ?, ?) RETURNING id""", "Usuario " + contador, correo, hash, rol, activo).entero("id");
        return new Usuario(id, correo, clave);
    }

    protected static Usuario crearCliente() {
        return crearUsuario("CLIENTE", true);
    }

    protected static String token(Usuario u, boolean recordarme) {
        Resp r = post("/api/auth/login", Json.obj("correo", u.correo(), "contrasena", u.contrasena(), "recordarme", recordarme), null);
        if (r.estado() != 200) throw new IllegalStateException("No se pudo iniciar sesión: " + r.cuerpo());
        return (String) r.json().get("token");
    }

    protected static String tokenAdmin() {
        return token(crearUsuario("ADMINISTRADOR", true), false);
    }

    protected static long crearCategoria(String nombre, boolean activa) {
        return bd().uno("INSERT INTO categorias (nombre, activa) VALUES (?, ?) RETURNING id", nombre, activa).entero("id");
    }

    protected static Map<String, Object> datosMaquina(long categoriaId) {
        return Json.obj(
                "categoriaId", categoriaId,
                "nombre", "Excavadora 320",
                "marca", "Caterpillar",
                "modelo", "320 GC",
                "tarifaDiaria", 1450,
                "ubicacion", "Trujillo",
                "especificaciones", Json.obj("Potencia", "146 HP"));
    }

    protected static long numero(Object valor) {
        return ((Number) valor).longValue();
    }
}
