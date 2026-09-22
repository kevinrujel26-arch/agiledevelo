package pe.upao.alquiler.auth;

import pe.upao.alquiler.bd.Consultas;

import java.time.Instant;

/** Acceso a la tabla "sesiones" (HU-02: cerrar sesión, expiración, recordarme). */
public class SesionRepositorio {

    private final Consultas bd;

    public SesionRepositorio(Consultas bd) {
        this.bd = bd;
    }

    /** Crea la sesión y devuelve su id (UUID). */
    public String crear(long usuarioId, boolean recordarme, Instant expiraEn, String ip, String agenteUsuario) {
        return bd.uno("""
                INSERT INTO sesiones (usuario_id, recordarme, expira_en, ip, agente_usuario)
                VALUES (?, ?, ?, ?, ?)
                RETURNING id""", usuarioId, recordarme, expiraEn, ip, agenteUsuario).texto("id");
    }

    /** HU-02 criterio 4: cerrar sesión invalida el token. */
    public void revocar(String sesionId) {
        bd.ejecutar("UPDATE sesiones SET revocada_en = now() WHERE id = ?::uuid AND revocada_en IS NULL", sesionId);
    }
}
