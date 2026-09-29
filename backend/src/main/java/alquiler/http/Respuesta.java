package alquiler.http;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/** Atajos para armar la respuesta HTTP de un controlador. */
public final class Respuesta {

    private Respuesta() {
    }

    public static ResponseEntity<Object> ok(Object cuerpo) {
        return ResponseEntity.ok(cuerpo);
    }

    public static ResponseEntity<Object> creado(Object cuerpo) {
        return ResponseEntity.status(HttpStatus.CREATED).body(cuerpo);
    }

    public static ResponseEntity<Object> sinContenido() {
        return ResponseEntity.noContent().build();
    }
}
