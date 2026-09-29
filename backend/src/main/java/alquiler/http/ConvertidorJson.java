package alquiler.http;

import alquiler.json.Json;
import org.springframework.http.HttpInputMessage;
import org.springframework.http.HttpOutputMessage;
import org.springframework.http.MediaType;
import org.springframework.http.converter.AbstractHttpMessageConverter;
import org.springframework.http.converter.HttpMessageNotReadableException;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Escribe cualquier respuesta con nuestra clase {@link Json}. Así el JSON que
 * recibe el frontend (fechas, decimales, nombres de campos) sigue siendo
 * exactamente el mismo que antes de usar Spring.
 */
public class ConvertidorJson extends AbstractHttpMessageConverter<Object> {

    public ConvertidorJson() {
        super(new MediaType("application", "json", StandardCharsets.UTF_8));
    }

    @Override
    protected boolean supports(Class<?> clase) {
        // Los textos y bytes los siguen manejando los convertidores normales de Spring
        return clase != String.class && clase != byte[].class;
    }

    @Override
    public boolean canRead(Class<?> clase, MediaType tipo) {
        return false; // el cuerpo de las peticiones lo lee Solicitud
    }

    @Override
    protected Object readInternal(Class<?> clase, HttpInputMessage entrada) throws HttpMessageNotReadableException {
        throw new HttpMessageNotReadableException("No se lee con este convertidor", entrada);
    }

    @Override
    protected void writeInternal(Object valor, HttpOutputMessage salida) throws IOException {
        salida.getBody().write(Json.escribir(valor).getBytes(StandardCharsets.UTF_8));
    }
}
