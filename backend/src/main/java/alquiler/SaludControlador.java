package alquiler;

import alquiler.bd.Bd;
import alquiler.http.Respuesta;
import alquiler.json.Json;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;

/** EN-04: verifica que la API y la BD respondan (Render lo usa como "health check"). */
@RestController
public class SaludControlador {

    private final Bd bd;

    public SaludControlador(Bd bd) {
        this.bd = bd;
    }

    @GetMapping("/api/salud")
    public ResponseEntity<Object> salud() {
        bd.uno("SELECT 1");
        return Respuesta.ok(Json.obj("estado", "ok", "fecha", Instant.now()));
    }
}
