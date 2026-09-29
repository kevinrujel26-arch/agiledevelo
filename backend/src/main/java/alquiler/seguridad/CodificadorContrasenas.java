package alquiler.seguridad;

import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Conecta nuestro PBKDF2 ({@link Contrasenas}) con la interfaz PasswordEncoder de
 * Spring Security. Usa el mismo formato "pbkdf2_sha256$iteraciones$sal$hash",
 * así las contraseñas que ya están guardadas en la BD siguen funcionando.
 */
public class CodificadorContrasenas implements PasswordEncoder {

    private final Contrasenas contrasenas;

    public CodificadorContrasenas(Contrasenas contrasenas) {
        this.contrasenas = contrasenas;
    }

    @Override
    public String encode(CharSequence contrasena) {
        return contrasenas.cifrar(contrasena.toString());
    }

    @Override
    public boolean matches(CharSequence contrasena, String guardado) {
        return contrasenas.verificar(contrasena.toString(), guardado);
    }
}
