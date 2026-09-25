package alquiler.seguridad;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * Cifrado de contraseñas con PBKDF2-HMAC-SHA256 (incluido en el JDK,
 * recomendado por NIST y OWASP). Nunca se guarda la contraseña en texto plano.
 *
 * Formato guardado en la BD:  pbkdf2_sha256$ITERACIONES$SAL$HASH
 * (la sal y el hash en Base64). Como las iteraciones quedan guardadas,
 * se pueden aumentar en el futuro sin romper las contraseñas existentes.
 */
public final class Contrasenas {

    private static final String ALGORITMO = "PBKDF2WithHmacSHA256";
    private static final String PREFIJO = "pbkdf2_sha256";
    private static final int BYTES_SAL = 16;
    private static final int BITS_HASH = 256;
    private static final SecureRandom ALEATORIO = new SecureRandom();

    private final int iteraciones;

    public Contrasenas(int iteraciones) {
        if (iteraciones < 1) throw new IllegalArgumentException("iteraciones debe ser positivo");
        this.iteraciones = iteraciones;
    }

    public String cifrar(String contrasena) {
        byte[] sal = new byte[BYTES_SAL];
        ALEATORIO.nextBytes(sal);
        byte[] hash = pbkdf2(contrasena, sal, iteraciones);
        Base64.Encoder b64 = Base64.getEncoder();
        return PREFIJO + "$" + iteraciones + "$" + b64.encodeToString(sal) + "$" + b64.encodeToString(hash);
    }

    public boolean verificar(String contrasena, String guardado) {
        if (contrasena == null || guardado == null) return false;
        String[] partes = guardado.split("\\$");
        if (partes.length != 4 || !PREFIJO.equals(partes[0])) return false;
        try {
            int iter = Integer.parseInt(partes[1]);
            byte[] sal = Base64.getDecoder().decode(partes[2]);
            byte[] esperado = Base64.getDecoder().decode(partes[3]);
            byte[] calculado = pbkdf2(contrasena, sal, iter);
            // Comparación en tiempo constante (evita ataques por tiempo de respuesta)
            return MessageDigest.isEqual(esperado, calculado);
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    private static byte[] pbkdf2(String contrasena, byte[] sal, int iteraciones) {
        PBEKeySpec spec = new PBEKeySpec(contrasena.toCharArray(), sal, iteraciones, BITS_HASH);
        try {
            return SecretKeyFactory.getInstance(ALGORITMO).generateSecret(spec).getEncoded();
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("PBKDF2 no disponible en este JDK", e);
        } finally {
            spec.clearPassword();
        }
    }
}
