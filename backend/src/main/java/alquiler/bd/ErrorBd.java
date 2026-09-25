package alquiler.bd;

import java.lang.reflect.Method;
import java.sql.SQLException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Error de base de datos. Guarda el código SQLSTATE y, si lo hay, el nombre
 * de la restricción que falló (p. ej. "ux_usuarios_correo"), para que el
 * servidor pueda responder con un mensaje entendible.
 */
public class ErrorBd extends RuntimeException {

    private static final Pattern RESTRICCION = Pattern.compile("constraint \"([^\"]+)\"");

    private final String estadoSql;
    private final String restriccion;

    public ErrorBd(String mensaje, Throwable causa) {
        super(mensaje, causa);
        SQLException sql = causa instanceof SQLException s ? s : null;
        this.estadoSql = sql == null ? null : sql.getSQLState();
        this.restriccion = sql == null ? null : extraerRestriccion(sql);
    }

    public String getEstadoSql() {
        return estadoSql;
    }

    public String getRestriccion() {
        return restriccion;
    }

    /**
     * El driver de PostgreSQL expone el nombre de la restricción en
     * PSQLException.getServerErrorMessage().getConstraint(). Lo leemos por
     * reflexión para no depender del driver al compilar; si no se puede,
     * lo buscamos en el texto del mensaje.
     */
    private static String extraerRestriccion(SQLException e) {
        try {
            Method m = e.getClass().getMethod("getServerErrorMessage");
            Object detalle = m.invoke(e);
            if (detalle != null) {
                Object nombre = detalle.getClass().getMethod("getConstraint").invoke(detalle);
                if (nombre != null) return nombre.toString();
            }
        } catch (ReflectiveOperationException | RuntimeException ignorado) {
            // seguimos con el mensaje
        }
        Matcher m = RESTRICCION.matcher(String.valueOf(e.getMessage()));
        return m.find() ? m.group(1) : null;
    }
}
