package alquiler.config;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.function.Function;

/**
 * Configuración de la aplicación. Se lee de las variables de entorno y,
 * en desarrollo, también del archivo backend/.env (las variables reales
 * del sistema tienen prioridad).
 */
public final class Config {

    public final String entorno;            // development | test | production
    public final boolean esPrueba;
    public final int puerto;

    public final String jdbcUrl;
    public final Properties propiedadesBd;
    public final int maxConexiones;

    public final String jwtSecreto;
    public final int iteracionesContrasena;

    // HU-02: reglas de sesión
    public final int inactividadMinutos = 30;
    public final int duracionHoras = 8;
    public final int recordarmeInactividadDias = 7;
    public final int recordarmeDuracionDias = 30;
    public final int maxIntentosFallidos = 5;
    public final int minutosBloqueo = 15;

    // HU-08: fotos
    public final int maxFotosPorMaquina = 5;
    public final int maxBytesPorFoto = 5 * 1024 * 1024;

    // Cloudinary: guarda las fotos de forma permanente (Render borra el disco local en cada deploy)
    public final String cloudinaryCloudName;
    public final String cloudinaryApiKey;
    public final String cloudinaryApiSecret;

    public final List<String> corsOrigenes;
    public final Path dirSubidas;
    public final String zonaHoraria;
    public final Path dirMigraciones;
    public final boolean migrarAlIniciar;

    private final Function<String, String> fuente;

    private Config(Function<String, String> fuente) {
        this.fuente = fuente;
        this.entorno = valor("APP_ENV", "development");
        this.esPrueba = "test".equals(entorno);
        this.puerto = Integer.parseInt(valor("PORT", "3000"));

        String urlBd = esPrueba ? requerido("DATABASE_URL_TEST") : requerido("DATABASE_URL");
        boolean ssl = "true".equalsIgnoreCase(valor("DB_SSL", "false"));
        this.propiedadesBd = new Properties();
        this.jdbcUrl = convertirUrlBd(urlBd, ssl, propiedadesBd);
        this.maxConexiones = Integer.parseInt(valor("DB_MAX_CONEXIONES", "10"));

        this.jwtSecreto = esPrueba ? valor("JWT_SECRET", "secreto-de-pruebas") : requerido("JWT_SECRET");
        this.iteracionesContrasena = Integer.parseInt(valor("PBKDF2_ITERACIONES", esPrueba ? "1000" : "310000"));

        this.corsOrigenes = Arrays.stream(valor("CORS_ORIGIN", "http://localhost:5173").split(","))
                .map(String::trim).filter(s -> !s.isEmpty()).toList();
        this.dirSubidas = Path.of(valor("UPLOAD_DIR", "uploads")).toAbsolutePath().normalize();
        this.zonaHoraria = valor("APP_TZ", "America/Lima");
        this.dirMigraciones = buscarDirMigraciones(valor("MIGRATIONS_DIR", null));
        this.migrarAlIniciar = !"false".equalsIgnoreCase(valor("MIGRAR_AL_INICIAR", "true"));

        this.cloudinaryCloudName = valor("CLOUDINARY_CLOUD_NAME", null);
        this.cloudinaryApiKey = valor("CLOUDINARY_API_KEY", null);
        this.cloudinaryApiSecret = valor("CLOUDINARY_API_SECRET", null);
    }

    /** Configuración normal: variables de entorno + archivo .env */
    public static Config cargar() {
        Map<String, String> archivoEnv = leerArchivoEnv(Path.of(".env"));
        return new Config(clave -> {
            String v = System.getenv(clave);
            return v != null ? v : archivoEnv.get(clave);
        });
    }

    /** Para las pruebas: igual que cargar(), pero con APP_ENV=test y valores extra. */
    public static Config paraPruebas(Map<String, String> extra) {
        Map<String, String> archivoEnv = leerArchivoEnv(Path.of(".env"));
        Map<String, String> fijos = new HashMap<>(extra);
        fijos.put("APP_ENV", "test");
        return new Config(clave -> {
            if (fijos.containsKey(clave)) return fijos.get(clave);
            String v = System.getenv(clave);
            return v != null ? v : archivoEnv.get(clave);
        });
    }

    public String valor(String clave, String porDefecto) {
        String v = fuente.apply(clave);
        return (v == null || v.isBlank()) ? porDefecto : v.trim();
    }

    private String requerido(String clave) {
        String v = valor(clave, null);
        if (v == null) {
            throw new IllegalStateException("Falta la variable de entorno " + clave + ". Revisa tu archivo backend/.env");
        }
        return v;
    }

    /**
     * Convierte "postgres://usuario:clave@host:5432/bd?sslmode=require"
     * (el formato que dan Render, Neon y docker) al formato JDBC.
     */
    static String convertirUrlBd(String url, boolean ssl, Properties props) {
        if (url.startsWith("jdbc:")) {
            if (ssl) props.setProperty("sslmode", "require");
            props.setProperty("stringtype", "unspecified");
            return url;
        }
        URI uri;
        try {
            uri = new URI(url);
        } catch (URISyntaxException e) {
            throw new IllegalStateException("La URL de la base de datos no es válida: " + e.getMessage());
        }
        if (uri.getHost() == null) throw new IllegalStateException("La URL de la base de datos no tiene host");

        String info = uri.getUserInfo();
        if (info != null) {
            int dosPuntos = info.indexOf(':');
            props.setProperty("user", dosPuntos >= 0 ? info.substring(0, dosPuntos) : info);
            if (dosPuntos >= 0) props.setProperty("password", info.substring(dosPuntos + 1));
        }
        boolean sslEnUrl = false;
        if (uri.getQuery() != null) {
            for (String par : uri.getQuery().split("&")) {
                if (par.equals("sslmode=require") || par.equals("sslmode=verify-full") || par.equals("ssl=true")) {
                    sslEnUrl = true;
                }
            }
        }
        if (ssl || sslEnUrl) props.setProperty("sslmode", "require");
        // Los textos se envían "sin tipo" y PostgreSQL deduce si son fecha, número, etc.
        props.setProperty("stringtype", "unspecified");
        props.setProperty("ApplicationName", "alquiler-backend");

        String puertoTexto = uri.getPort() > 0 ? ":" + uri.getPort() : "";
        String baseDatos = uri.getPath() == null ? "" : uri.getPath();
        return "jdbc:postgresql://" + uri.getHost() + puertoTexto + baseDatos;
    }

    private static Path buscarDirMigraciones(String configurado) {
        if (configurado != null) return Path.of(configurado).toAbsolutePath().normalize();
        for (String candidato : List.of("../database/migrations", "database/migrations")) {
            Path p = Path.of(candidato).toAbsolutePath().normalize();
            if (Files.isDirectory(p)) return p;
        }
        return Path.of("../database/migrations").toAbsolutePath().normalize();
    }

    static Map<String, String> leerArchivoEnv(Path archivo) {
        Map<String, String> valores = new HashMap<>();
        if (!Files.isRegularFile(archivo)) return valores;
        try {
            for (String linea : Files.readAllLines(archivo, StandardCharsets.UTF_8)) {
                String l = linea.trim();
                if (l.isEmpty() || l.startsWith("#")) continue;
                int igual = l.indexOf('=');
                if (igual <= 0) continue;
                String clave = l.substring(0, igual).trim();
                String v = l.substring(igual + 1).trim();
                if (v.length() >= 2 && ((v.startsWith("\"") && v.endsWith("\"")) || (v.startsWith("'") && v.endsWith("'")))) {
                    v = v.substring(1, v.length() - 1);
                }
                valores.put(clave, v);
            }
        } catch (IOException e) {
            throw new IllegalStateException("No se pudo leer " + archivo + ": " + e.getMessage());
        }
        return valores;
    }
}
