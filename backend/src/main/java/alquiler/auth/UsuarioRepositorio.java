package alquiler.auth;

import org.springframework.stereotype.Repository;
import alquiler.bd.Consultas;
import alquiler.bd.Fila;

/** Acceso a la tabla "usuarios" (HU-01, HU-02). */
@Repository
public class UsuarioRepositorio {

    private final Consultas bd;

    public UsuarioRepositorio(Consultas bd) {
        this.bd = bd;
    }

    public boolean existeCorreo(String correo) {
        return bd.uno("SELECT 1 FROM usuarios WHERE correo = ?", correo) != null;
    }

    /** El teléfono llega ya normalizado (9 dígitos). */
    public Fila crear(String nombre, String correo, String telefono, String hash, String rol) {
        return bd.uno("""
                INSERT INTO usuarios (nombre, correo, telefono, contrasena_hash, rol)
                VALUES (?, ?, ?, ?, ?)
                RETURNING id, nombre, correo, telefono, rol""", nombre, correo, telefono, hash, rol);
    }

    public Fila buscarPorCorreo(String correo) {
        return bd.uno("SELECT * FROM usuarios WHERE correo = ?", correo);
    }

    public Fila buscarPorGoogleId(String googleId) {
        return bd.uno("SELECT * FROM usuarios WHERE google_id = ?", googleId);
    }

    /** Cuenta creada con Google: sin contraseña (contrasena_hash NULL) y sin celular. */
    public Fila crearConGoogle(String nombre, String correo, String googleId, String rol) {
        return bd.uno("""
                INSERT INTO usuarios (nombre, correo, google_id, rol)
                VALUES (?, ?, ?, ?)
                RETURNING *""", nombre, correo, googleId, rol);
    }

    /** Vincula Google a una cuenta existente. Devuelve null si otra petición ya la vinculó. */
    public Fila vincularGoogle(long usuarioId, String googleId) {
        return bd.uno("""
                UPDATE usuarios SET google_id = ?
                 WHERE id = ? AND google_id IS NULL
             RETURNING *""", googleId, usuarioId);
    }

    /** El teléfono llega ya normalizado (9 dígitos). */
    public Fila actualizarTelefono(long usuarioId, String telefono) {
        return bd.uno("""
                UPDATE usuarios SET telefono = ?
                 WHERE id = ?
             RETURNING id, nombre, correo, telefono, rol""", telefono, usuarioId);
    }

    /**
     * Suma un intento fallido. Al llegar al máximo, bloquea la cuenta N minutos.
     * Si un bloqueo anterior ya venció, el contador vuelve a empezar desde 1.
     */
    public Fila registrarIntentoFallido(long usuarioId, int maxIntentos, int minutosBloqueo) {
        return bd.uno("""
                UPDATE usuarios u
                   SET intentos_fallidos = n.nuevo,
                       bloqueado_hasta = CASE WHEN n.nuevo >= ?
                                              THEN now() + make_interval(mins => ?::int)
                                              ELSE NULL END
                  FROM (SELECT id,
                               CASE WHEN bloqueado_hasta IS NOT NULL AND bloqueado_hasta <= now()
                                    THEN 1 ELSE intentos_fallidos + 1 END AS nuevo
                          FROM usuarios WHERE id = ?) n
                 WHERE u.id = n.id
             RETURNING u.intentos_fallidos, u.bloqueado_hasta""", maxIntentos, minutosBloqueo, usuarioId);
    }

    public void reiniciarIntentos(long usuarioId) {
        bd.ejecutar("UPDATE usuarios SET intentos_fallidos = 0, bloqueado_hasta = NULL WHERE id = ?", usuarioId);
    }

    public Fila buscarPorId(long usuarioId) {
        return bd.uno("SELECT * FROM usuarios WHERE id = ?", usuarioId);
    }

    public Fila cambiarContrasena(long usuarioId, String hash) {
        return bd.uno("""
                UPDATE usuarios SET contrasena_hash = ?
                 WHERE id = ?
             RETURNING *""", hash, usuarioId);
    }
}
