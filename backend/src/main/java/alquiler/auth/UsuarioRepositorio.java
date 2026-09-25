package alquiler.auth;

import alquiler.bd.Consultas;
import alquiler.bd.Fila;

/** Acceso a la tabla "usuarios" (HU-01, HU-02). */
public class UsuarioRepositorio {

    private final Consultas bd;

    public UsuarioRepositorio(Consultas bd) {
        this.bd = bd;
    }

    public boolean existeCorreo(String correo) {
        return bd.uno("SELECT 1 FROM usuarios WHERE correo = ?", correo) != null;
    }

    public Fila crear(String nombre, String correo, String hash, String rol) {
        return bd.uno("""
                INSERT INTO usuarios (nombre, correo, contrasena_hash, rol)
                VALUES (?, ?, ?, ?)
                RETURNING id, nombre, correo, rol""", nombre, correo, hash, rol);
    }

    public Fila buscarPorCorreo(String correo) {
        return bd.uno("SELECT * FROM usuarios WHERE correo = ?", correo);
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
}
