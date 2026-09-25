package alquiler.bd;

import alquiler.config.Config;
import alquiler.json.Json;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

/**
 * Acceso a PostgreSQL con JDBC puro y un pool de conexiones sencillo.
 *
 * <pre>
 *   List&lt;Fila&gt; filas = bd.consultar("SELECT * FROM categorias WHERE activa = ?", true);
 *   bd.transaccion(tx -&gt; { tx.ejecutar(...); tx.ejecutar(...); return null; });
 * </pre>
 */
public class Bd implements Consultas, AutoCloseable {

    /** Si una conexión estuvo libre más de este tiempo, se verifica antes de reutilizarla. */
    private static final long VERIFICAR_SI_INACTIVA_MS = 30_000;

    private final String url;
    private final Properties propiedades;
    private final Semaphore permisos;
    private final ConcurrentLinkedDeque<ConexionLibre> libres = new ConcurrentLinkedDeque<>();

    private record ConexionLibre(Connection conexion, long liberadaEn) {
    }

    /** Una operación que se ejecuta dentro de una transacción. */
    @FunctionalInterface
    public interface Trabajo<T> {
        T ejecutar(Transaccion tx) throws Exception;
    }

    public Bd(Config config) {
        this(config.jdbcUrl, config.propiedadesBd, config.maxConexiones);
    }

    public Bd(String url, Properties propiedades, int maxConexiones) {
        this.url = url;
        this.propiedades = propiedades;
        this.permisos = new Semaphore(maxConexiones, true);
    }

    // ------------------------------------------------------------------
    // Pool de conexiones
    // ------------------------------------------------------------------
    private Connection obtenerConexion() {
        try {
            if (!permisos.tryAcquire(15, TimeUnit.SECONDS)) {
                throw new ErrorBd("No hay conexiones libres a la base de datos (tiempo de espera agotado)", null);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ErrorBd("Interrumpido esperando una conexión", e);
        }
        try {
            ConexionLibre libre;
            while ((libre = libres.pollFirst()) != null) {
                Connection c = libre.conexion();
                boolean inactivaMucho = System.currentTimeMillis() - libre.liberadaEn() > VERIFICAR_SI_INACTIVA_MS;
                if (!c.isClosed() && (!inactivaMucho || c.isValid(3))) return c;
                cerrarSinError(c);
            }
            return DriverManager.getConnection(url, propiedades);
        } catch (SQLException e) {
            permisos.release();
            throw new ErrorBd("No se pudo conectar a la base de datos: " + e.getMessage(), e);
        } catch (RuntimeException e) {
            permisos.release();
            throw e;
        }
    }

    private void liberarConexion(Connection c, boolean reutilizable) {
        try {
            if (reutilizable && !c.isClosed() && c.getAutoCommit()) {
                libres.offerFirst(new ConexionLibre(c, System.currentTimeMillis()));
            } else {
                cerrarSinError(c);
            }
        } catch (SQLException e) {
            cerrarSinError(c);
        } finally {
            permisos.release();
        }
    }

    private static void cerrarSinError(Connection c) {
        try {
            c.close();
        } catch (SQLException ignorado) {
            // nada que hacer
        }
    }

    @Override
    public void close() {
        ConexionLibre libre;
        while ((libre = libres.pollFirst()) != null) cerrarSinError(libre.conexion());
    }

    // ------------------------------------------------------------------
    // Consultas fuera de transacción
    // ------------------------------------------------------------------
    @Override
    public List<Fila> consultar(String sql, Object... parametros) {
        Connection c = obtenerConexion();
        boolean ok = false;
        try {
            List<Fila> filas = consultar(c, sql, parametros);
            ok = true;
            return filas;
        } finally {
            liberarConexion(c, ok);
        }
    }

    @Override
    public int ejecutar(String sql, Object... parametros) {
        Connection c = obtenerConexion();
        boolean ok = false;
        try {
            int n = ejecutar(c, sql, parametros);
            ok = true;
            return n;
        } finally {
            liberarConexion(c, ok);
        }
    }

    /** Ejecuta un script SQL completo (varias sentencias). Lo usan las migraciones. */
    public void ejecutarScript(Connection c, String script) throws SQLException {
        try (Statement st = c.createStatement()) {
            st.execute(script);
        }
    }

    // ------------------------------------------------------------------
    // Transacciones
    // ------------------------------------------------------------------
    /**
     * Ejecuta el trabajo en una transacción: si termina bien hace COMMIT,
     * si lanza cualquier error hace ROLLBACK y vuelve a lanzar el error.
     */
    public <T> T transaccion(Trabajo<T> trabajo) {
        Connection c = obtenerConexion();
        boolean reutilizable = false;
        try {
            c.setAutoCommit(false);
            T resultado = trabajo.ejecutar(new Transaccion(c));
            c.commit();
            c.setAutoCommit(true);
            reutilizable = true;
            return resultado;
        } catch (Exception e) {
            try {
                c.rollback();
                c.setAutoCommit(true);
                reutilizable = true;
            } catch (SQLException ignorado) {
                // la conexión se descarta
            }
            if (e instanceof RuntimeException re) throw re;
            throw new ErrorBd(e.getMessage(), e);
        } finally {
            liberarConexion(c, reutilizable);
        }
    }

    /** Operaciones sobre la conexión de una transacción en curso. */
    public static final class Transaccion implements Consultas {
        private final Connection conexion;

        private Transaccion(Connection conexion) {
            this.conexion = conexion;
        }

        public Connection conexion() {
            return conexion;
        }

        @Override
        public List<Fila> consultar(String sql, Object... parametros) {
            return Bd.consultar(conexion, sql, parametros);
        }

        @Override
        public int ejecutar(String sql, Object... parametros) {
            return Bd.ejecutar(conexion, sql, parametros);
        }
    }

    // ------------------------------------------------------------------
    // JDBC
    // ------------------------------------------------------------------
    static List<Fila> consultar(Connection c, String sql, Object... parametros) {
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            asignarParametros(ps, parametros);
            boolean hayResultados = ps.execute();
            List<Fila> filas = new ArrayList<>();
            if (!hayResultados) return filas;
            try (ResultSet rs = ps.getResultSet()) {
                ResultSetMetaData md = rs.getMetaData();
                int columnas = md.getColumnCount();
                while (rs.next()) {
                    Fila fila = new Fila();
                    for (int i = 1; i <= columnas; i++) {
                        fila.put(md.getColumnLabel(i), leerValor(rs, i, md.getColumnTypeName(i)));
                    }
                    filas.add(fila);
                }
            }
            return filas;
        } catch (SQLException e) {
            throw new ErrorBd(e.getMessage(), e);
        }
    }

    static int ejecutar(Connection c, String sql, Object... parametros) {
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            asignarParametros(ps, parametros);
            return ps.executeUpdate();
        } catch (SQLException e) {
            throw new ErrorBd(e.getMessage(), e);
        }
    }

    private static void asignarParametros(PreparedStatement ps, Object[] parametros) throws SQLException {
        for (int i = 0; i < parametros.length; i++) {
            Object p = parametros[i];
            int n = i + 1;
            if (p == null) {
                ps.setNull(n, Types.NULL);
            } else if (p instanceof Integer x) {
                ps.setInt(n, x);
            } else if (p instanceof Long x) {
                ps.setLong(n, x);
            } else if (p instanceof Boolean x) {
                ps.setBoolean(n, x);
            } else if (p instanceof BigDecimal x) {
                ps.setBigDecimal(n, x);
            } else if (p instanceof Double x) {
                ps.setDouble(n, x);
            } else if (p instanceof Instant x) {
                ps.setObject(n, OffsetDateTime.ofInstant(x, ZoneOffset.UTC));
            } else if (p instanceof LocalDate x) {
                ps.setObject(n, x);
            } else if (p instanceof Map<?, ?> || p instanceof Collection<?>) {
                ps.setString(n, Json.escribir(p)); // usar "?::jsonb" en el SQL
            } else {
                ps.setString(n, p.toString());
            }
        }
    }

    private static Object leerValor(ResultSet rs, int i, String tipo) throws SQLException {
        switch (tipo) {
            case "date":
                return rs.getString(i); // 'AAAA-MM-DD'
            case "timestamptz":
            case "timestamp": {
                OffsetDateTime o = rs.getObject(i, OffsetDateTime.class);
                return o == null ? null : o.toInstant();
            }
            case "json":
            case "jsonb": {
                String s = rs.getString(i);
                return s == null ? null : Json.leer(s);
            }
            case "numeric":
                return rs.getBigDecimal(i);
            case "uuid": {
                Object o = rs.getObject(i);
                return o == null ? null : o.toString();
            }
            case "int2":
            case "int4": {
                int v = rs.getInt(i);
                return rs.wasNull() ? null : (long) v;
            }
            default:
                return rs.getObject(i);
        }
    }
}
