package alquiler.auth;

/**
 * Verifica el ID token ("credential") que entrega el botón "Continuar con Google".
 * Es una interfaz para que las pruebas lo puedan simular sin una cuenta real de Google.
 */
public interface VerificadorGoogle {

    /** Datos de la cuenta de Google tomados de un ID token ya verificado. */
    record CuentaGoogle(String id, String correo, boolean correoVerificado, String nombre) {
    }

    /** false si falta GOOGLE_CLIENT_ID: el inicio de sesión con Google queda desactivado. */
    boolean configurado();

    /**
     * Comprueba firma, vencimiento, emisor y audiencia (= GOOGLE_CLIENT_ID).
     * Devuelve la cuenta si el token es válido o null si no lo es.
     */
    CuentaGoogle verificar(String idToken);
}
