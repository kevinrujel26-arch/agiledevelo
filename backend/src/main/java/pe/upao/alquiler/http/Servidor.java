package pe.upao.alquiler.http;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import pe.upao.alquiler.bd.ErrorBd;
import pe.upao.alquiler.config.Config;
import pe.upao.alquiler.json.Json;
import pe.upao.alquiler.seguridad.Autenticador;
import pe.upao.alquiler.seguridad.Rol;
import pe.upao.alquiler.seguridad.UsuarioSesion;
import pe.upao.alquiler.util.ErrorApp;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Servidor HTTP basado en com.sun.net.httpserver (incluido en el JDK).
 *
 * Para cada petición: aplica CORS y cabeceras de seguridad, sirve las fotos
 * de /uploads, busca la ruta, verifica sesión y rol, ejecuta el manejador
 * y convierte cualquier error en una respuesta JSON.
 */
public final class Servidor {

    // Restricciones de la BD -> mensaje entendible para el usuario
    private static final Map<String, Object[]> MENSAJES_RESTRICCION = Map.of(
            "ux_usuarios_correo", new Object[]{409, "Ya existe una cuenta registrada con ese correo"},
            "ux_categorias_nombre", new Object[]{409, "Ya existe una categoría con ese nombre"},
            "ux_fotos_una_principal", new Object[]{409, "La máquina ya tiene una foto principal"},
            "ck_max_5_fotos", new Object[]{400, "Una máquina puede tener como máximo 5 fotos"},
            "ex_bloqueos_sin_superposicion", new Object[]{409, "El rango se superpone con otro bloqueo existente de esta máquina"},
            "ex_reservas_sin_superposicion", new Object[]{409, "Las fechas elegidas ya están ocupadas"});

    private final Config config;
    private final Enrutador enrutador;
    private final Autenticador autenticador;
    private HttpServer servidor;
    private ExecutorService hilos;

    public Servidor(Config config, Enrutador enrutador, Autenticador autenticador) {
        this.config = config;
        this.enrutador = enrutador;
        this.autenticador = autenticador;
    }

    /** Arranca en el puerto indicado (0 = uno libre al azar, útil en pruebas). */
    public void iniciar(int puerto) throws IOException {
        servidor = HttpServer.create(new InetSocketAddress(puerto), 0);
        hilos = Executors.newVirtualThreadPerTaskExecutor(); // Java 21: un hilo virtual por petición
        servidor.setExecutor(hilos);
        servidor.createContext("/", this::atender);
        servidor.start();
    }

    public int puerto() {
        return servidor.getAddress().getPort();
    }

    public void detener() {
        if (servidor != null) servidor.stop(1);
        if (hilos != null) hilos.shutdown();
    }

    // ------------------------------------------------------------------
    private void atender(HttpExchange ex) {
        long inicio = System.nanoTime();
        int estado = 500;
        try {
            aplicarCabeceras(ex);
            String metodo = ex.getRequestMethod();
            String ruta = ex.getRequestURI().getPath();

            if (metodo.equals("OPTIONS")) {           // CORS "preflight"
                estado = 204;
                enviar(ex, 204, null);
                return;
            }
            if (ruta.startsWith("/uploads/") && (metodo.equals("GET") || metodo.equals("HEAD"))) {
                estado = servirArchivo(ex, ruta);
                return;
            }

            Enrutador.Coincidencia c = enrutador.buscar(metodo, ruta);
            if (c == null) {
                estado = 404;
                enviar(ex, 404, Json.obj("error", "No existe la ruta " + metodo + " " + ruta));
                return;
            }

            Solicitud solicitud = new Solicitud(ex, c.params());
            Enrutador.Acceso acceso = c.ruta().acceso();
            if (acceso != Enrutador.Acceso.PUBLICO) {
                UsuarioSesion usuario = autenticador.autenticar(solicitud.cabecera("Authorization"));
                if (acceso == Enrutador.Acceso.ADMINISTRADOR) Autenticador.exigirRol(usuario, Rol.ADMINISTRADOR);
                solicitud.setUsuario(usuario);
            }

            Respuesta r = c.ruta().manejador().manejar(solicitud);
            estado = r.estado();
            enviar(ex, r.estado(), r.cuerpo());
        } catch (Throwable error) {
            Respuesta r = respuestaDeError(error);
            estado = r.estado();
            try {
                enviar(ex, r.estado(), r.cuerpo());
            } catch (IOException | RuntimeException ignorado) {
                // el cliente ya cerró la conexión
            }
        } finally {
            ex.close();
            if (!config.esPrueba) {
                long ms = (System.nanoTime() - inicio) / 1_000_000;
                System.out.printf("%s %s %d %d ms%n", ex.getRequestMethod(), ex.getRequestURI().getPath(), estado, ms);
            }
        }
    }

    /** Convierte cualquier excepción en {"error": "..."} con el código HTTP adecuado. */
    Respuesta respuestaDeError(Throwable error) {
        if (error instanceof ErrorApp e) {
            Map<String, Object> cuerpo = new LinkedHashMap<>();
            cuerpo.put("error", e.getMessage());
            if (e.getDetalles() != null) cuerpo.put("detalles", e.getDetalles());
            return new Respuesta(e.getEstadoHttp(), cuerpo);
        }
        if (error instanceof ErrorBd e) {
            Object[] conocido = e.getRestriccion() == null ? null : MENSAJES_RESTRICCION.get(e.getRestriccion());
            if (conocido != null) return new Respuesta((Integer) conocido[0], Json.obj("error", conocido[1]));
            if ("23503".equals(e.getEstadoSql())) {
                return new Respuesta(409, Json.obj("error", "El registro está relacionado con otros datos y no se puede modificar así"));
            }
        }
        if (!config.esPrueba) error.printStackTrace();
        return new Respuesta(500, Json.obj("error", "Ocurrió un error inesperado. Intenta nuevamente"));
    }

    // ------------------------------------------------------------------
    private void aplicarCabeceras(HttpExchange ex) {
        Headers h = ex.getResponseHeaders();
        // Seguridad básica
        h.set("X-Content-Type-Options", "nosniff");
        h.set("X-Frame-Options", "SAMEORIGIN");
        h.set("Referrer-Policy", "no-referrer");
        h.set("Cross-Origin-Resource-Policy", "cross-origin"); // permite mostrar las fotos desde el frontend

        // CORS: solo los orígenes configurados en CORS_ORIGIN
        String origen = ex.getRequestHeaders().getFirst("Origin");
        if (origen != null && config.corsOrigenes.contains(origen)) {
            h.set("Access-Control-Allow-Origin", origen);
            h.set("Vary", "Origin");
            h.set("Access-Control-Allow-Methods", "GET, POST, PUT, PATCH, DELETE, OPTIONS");
            h.set("Access-Control-Allow-Headers", "Authorization, Content-Type");
            h.set("Access-Control-Max-Age", "600");
        }
    }

    private void enviar(HttpExchange ex, int estado, Object cuerpo) throws IOException {
        if (cuerpo == null) {
            ex.sendResponseHeaders(estado, -1);
            return;
        }
        byte[] bytes = Json.escribir(cuerpo).getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        ex.sendResponseHeaders(estado, bytes.length);
        try (OutputStream os = ex.getResponseBody()) {
            os.write(bytes);
        }
    }

    /** HU-08: sirve las fotos subidas. Evita salir de la carpeta (../). */
    private int servirArchivo(HttpExchange ex, String ruta) throws IOException {
        Path base = config.dirSubidas;
        Path archivo = base.resolve(ruta.substring("/uploads/".length())).normalize();
        if (!archivo.startsWith(base) || !Files.isRegularFile(archivo)) {
            enviar(ex, 404, Json.obj("error", "Archivo no encontrado"));
            return 404;
        }
        String nombre = archivo.getFileName().toString().toLowerCase();
        String tipo = nombre.endsWith(".png") ? "image/png"
                : (nombre.endsWith(".jpg") || nombre.endsWith(".jpeg")) ? "image/jpeg"
                : "application/octet-stream";
        ex.getResponseHeaders().set("Content-Type", tipo);
        ex.getResponseHeaders().set("Cache-Control", "public, max-age=604800");
        long largo = Files.size(archivo);
        if (ex.getRequestMethod().equals("HEAD")) {
            ex.sendResponseHeaders(200, -1);
            return 200;
        }
        ex.sendResponseHeaders(200, largo);
        try (InputStream in = Files.newInputStream(archivo); OutputStream os = ex.getResponseBody()) {
            in.transferTo(os);
        }
        return 200;
    }
}
