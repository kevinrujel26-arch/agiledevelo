package alquiler.modelo;

import alquiler.bd.Fila;
import alquiler.json.Json;

import java.util.Map;

/** HU-08: foto de una máquina (máximo 5, una principal). */
public record Foto(long id, long maquinaId, String ruta, boolean esPrincipal, long orden) implements Json.Convertible {

    public static Foto desde(Fila f) {
        return new Foto(f.entero("id"), f.entero("maquina_id"), f.texto("ruta"), f.bool("es_principal"), f.enteroOCero("orden"));
    }

    @Override
    public Map<String, Object> aJson() {
        return Json.obj("id", id, "url", ruta, "esPrincipal", esPrincipal, "orden", orden);
    }
}
