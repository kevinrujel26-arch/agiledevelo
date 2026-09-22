package pe.upao.alquiler.modelo;

import pe.upao.alquiler.bd.Fila;
import pe.upao.alquiler.json.Json;

import java.util.Map;

/** HU-14: categoría de maquinaria. Los conteos solo vienen en el listado del administrador. */
public record Categoria(long id, String nombre, String descripcion, boolean activa,
                        Long totalMaquinas, Long maquinasPublicadas) implements Json.Convertible {

    public static Categoria desde(Fila f) {
        return new Categoria(f.entero("id"), f.texto("nombre"), f.texto("descripcion"), f.bool("activa"),
                f.containsKey("total_maquinas") ? f.entero("total_maquinas") : null,
                f.containsKey("maquinas_publicadas") ? f.entero("maquinas_publicadas") : null);
    }

    @Override
    public Map<String, Object> aJson() {
        Map<String, Object> json = Json.obj("id", id, "nombre", nombre, "descripcion", descripcion, "activa", activa);
        if (totalMaquinas != null) {
            json.put("totalMaquinas", totalMaquinas);
            json.put("maquinasPublicadas", maquinasPublicadas);
        }
        return json;
    }
}
