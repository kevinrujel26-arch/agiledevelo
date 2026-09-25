package alquiler.modelo;

import alquiler.bd.Fila;
import alquiler.json.Json;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/** HU-03 / HU-04 / HU-08: máquina de la flota. */
public record Maquina(
        long id,
        long categoriaId,
        String categoriaNombre,
        String nombre,
        String marca,
        String modelo,
        String descripcion,
        Object especificaciones,
        BigDecimal tarifaDiaria,
        String ubicacion,
        EstadoMaquina estado,
        boolean enMantenimiento,
        String fotoPrincipal,
        Instant publicadaEn,
        Instant creadoEn,
        Instant actualizadoEn) {

    public static Maquina desde(Fila f) {
        return new Maquina(
                f.entero("id"),
                f.entero("categoria_id"),
                f.texto("categoria_nombre"),
                f.texto("nombre"),
                f.texto("marca"),
                f.texto("modelo"),
                f.texto("descripcion"),
                f.json("especificaciones"),
                f.decimal("tarifa_diaria"),
                f.texto("ubicacion"),
                EstadoMaquina.valueOf(f.texto("estado")),
                f.bool("en_mantenimiento"),
                f.texto("foto_principal"),
                f.instante("publicada_en"),
                f.instante("creado_en"),
                f.instante("actualizado_en"));
    }

    public boolean estaPublicada() {
        return estado == EstadoMaquina.PUBLICADA;
    }

    /** HU-03 criterio 2: foto, nombre, categoría y tarifa diaria. */
    public Map<String, Object> tarjetaJson() {
        return Json.obj(
                "id", id,
                "nombre", nombre,
                "marca", marca,
                "modelo", modelo,
                "categoria", Json.obj("id", categoriaId, "nombre", categoriaNombre),
                "tarifaDiaria", tarifaDiaria,
                "ubicacion", ubicacion,
                "enMantenimiento", enMantenimiento,
                "fotoPrincipal", fotoPrincipal);
    }

    /** Fila del listado del administrador. */
    public Map<String, Object> filaAdminJson() {
        Map<String, Object> json = tarjetaJson();
        json.put("estado", estado.name());
        json.put("actualizadoEn", actualizadoEn);
        return json;
    }

    /** Detalle completo con fotos. */
    public Map<String, Object> detalleJson(List<Foto> fotos) {
        Map<String, Object> json = tarjetaJson();
        json.put("descripcion", descripcion);
        json.put("especificaciones", especificaciones == null ? Map.of() : especificaciones);
        json.put("estado", estado.name());
        json.put("publicadaEn", publicadaEn);
        json.put("creadoEn", creadoEn);
        json.put("actualizadoEn", actualizadoEn);
        json.put("fotos", fotos);
        return json;
    }
}
