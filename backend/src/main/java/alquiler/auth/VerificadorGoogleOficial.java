package alquiler.auth;

import alquiler.config.Config;
import alquiler.util.ErrorApp;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.gson.GsonFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.List;

/**
 * Verificación con la librería oficial de Google (GoogleIdTokenVerifier).
 * Descarga y guarda en caché las claves públicas de Google para comprobar la
 * firma; no necesita Client Secret. El token nunca se escribe en los logs.
 */
@Component
public class VerificadorGoogleOficial implements VerificadorGoogle {

    private final JsonFactory json = GsonFactory.getDefaultInstance();
    /** null si falta GOOGLE_CLIENT_ID. */
    private final GoogleIdTokenVerifier verificador;

    public VerificadorGoogleOficial(Config config) {
        this.verificador = config.googleClientId == null ? null
                : new GoogleIdTokenVerifier.Builder(new NetHttpTransport(), json)
                        .setAudience(List.of(config.googleClientId))
                        .build();
    }

    @Override
    public boolean configurado() {
        return verificador != null;
    }

    @Override
    public CuentaGoogle verificar(String idToken) {
        if (verificador == null || idToken == null) return null;
        GoogleIdToken token;
        try {
            token = GoogleIdToken.parse(json, idToken);
        } catch (IOException | RuntimeException e) { // no es un JWT bien formado
            return null;
        }
        try {
            if (!verificador.verify(token)) return null;
        } catch (GeneralSecurityException e) {
            return null;
        } catch (IOException e) { // no se pudieron descargar las claves públicas de Google
            throw new ErrorApp(503, "No se pudo verificar tu cuenta con Google. Intenta nuevamente en unos minutos");
        }
        GoogleIdToken.Payload datos = token.getPayload();
        return new CuentaGoogle(
                datos.getSubject(),
                datos.getEmail(),
                Boolean.TRUE.equals(datos.getEmailVerified()),
                datos.get("name") instanceof String nombre ? nombre : null);
    }
}
