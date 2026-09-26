package alquiler.maquinas;

import alquiler.bd.Consultas;
import alquiler.bd.Fila;
import alquiler.modelo.EstadoMaquina;
import alquiler.modelo.Maquina;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Acceso a la tabla "maquinas" (HU-03, HU-08). */
public class MaquinaRepositorio {

    private static final String SELECT_MAQUINA = """
            SELECT m.*, c.nombre AS categoria_nombre,
                   (SELECT f.ruta FROM fotos_maquina f
                     WHERE f.maquina_id = m.id AND f.es_principal LIMIT 1) AS foto_principal
              FROM maquinas m
              JOIN categorias c ON c.id = m.categoria_id""";

    /** Campo de la API -> columna de la BD (para editar solo lo que llega). */
    public static final Map<String, String> COLUMNAS_EDITABLES = new LinkedHashMap<>();

    static {
        COLUMNAS_EDITABLES.put("categoriaId", "categoria_id");
        COLUMNAS_EDITABLES.put("nombre", "nombre");
        COLUMNAS_EDITABLES.put("marca", "marca");
        COLUMNAS_EDITABLES.put("modelo", "modelo");
        COLUMNAS_EDITABLES.put("descripcion", "descripcion");
        COLUMNAS_EDITABLES.put("especificaciones", "especificaciones");
        COLUMNAS_EDITABLES.put("tarifaHoraria", "tarifa_horaria");
        COLUMNAS_EDITABLES.put("ubicacion", "ubicacion");
        COLUMNAS_EDITABLES.put("enMantenimiento", "en_mantenimiento");
    }

    private final Consultas bd;

    public MaquinaRepositorio(Consultas bd) {
        this.bd = bd;
    }

    public Maquina buscar(long id) {
        Fila f = bd.uno(SELECT_MAQUINA + " WHERE m.id = ?", id);
        return f == null ? null : Maquina.desde(f);
    }

    // ---------------- Catálogo público (HU-03) ----------------
    /** HU-03 criterio 2: filtro opcional por categoría y búsqueda por palabra clave (nombre o marca). */
    public List<Maquina> listarPublicadas(Long categoriaId, String texto, int limite, long desplazamiento) {
        Filtro f = filtro("PUBLICADA", categoriaId, texto);
        List<Object> params = new ArrayList<>(f.params());
        params.add(limite);
        params.add(desplazamiento);
        return bd.consultar(SELECT_MAQUINA + f.where() + """
                 ORDER BY m.publicada_en DESC NULLS LAST, m.id DESC
                 LIMIT ? OFFSET ?""", params.toArray()).stream().map(Maquina::desde).toList();
    }

    public long contarPublicadas(Long categoriaId, String texto) {
        Filtro f = filtro("PUBLICADA", categoriaId, texto);
        return bd.uno("SELECT count(*) AS total FROM maquinas m" + f.where(), f.params().toArray()).enteroOCero("total");
    }

    // ---------------- Administración (HU-08) ----------------
    /** Listado con filtros opcionales. Devuelve [filas de la página, total]. */
    public List<Maquina> listarAdmin(String estado, Long categoriaId, String texto, int limite, long desplazamiento) {
        Filtro f = filtro(estado, categoriaId, texto);
        List<Object> params = new ArrayList<>(f.params());
        params.add(limite);
        params.add(desplazamiento);
        return bd.consultar(SELECT_MAQUINA + f.where() + """
                 ORDER BY m.actualizado_en DESC, m.id DESC
                 LIMIT ? OFFSET ?""", params.toArray()).stream().map(Maquina::desde).toList();
    }

    public long contarAdmin(String estado, Long categoriaId, String texto) {
        Filtro f = filtro(estado, categoriaId, texto);
        return bd.uno("SELECT count(*) AS total FROM maquinas m" + f.where(), f.params().toArray()).enteroOCero("total");
    }

    private record Filtro(String where, List<Object> params) {
    }

    /** Compartido entre el catálogo público y el listado del administrador. */
    private static Filtro filtro(String estado, Long categoriaId, String texto) {
        List<String> condiciones = new ArrayList<>();
        List<Object> params = new ArrayList<>();
        if (estado != null) {
            condiciones.add("m.estado = ?");
            params.add(estado);
        }
        if (categoriaId != null) {
            condiciones.add("m.categoria_id = ?");
            params.add(categoriaId);
        }
        if (texto != null && !texto.isBlank()) {
            String patron = "%" + texto.toLowerCase() + "%";
            condiciones.add("(lower(m.nombre) LIKE ? OR lower(m.marca) LIKE ?)");
            params.add(patron);
            params.add(patron);
        }
        String where = condiciones.isEmpty() ? "" : " WHERE " + String.join(" AND ", condiciones);
        return new Filtro(where + "\n", params);
    }

    /** Se crea siempre como BORRADOR (HU-08 criterio 4). Devuelve el id. */
    public long crear(DatosMaquina d, long creadoPor) {
        return bd.uno("""
                INSERT INTO maquinas (categoria_id, nombre, marca, modelo, descripcion, especificaciones,
                                      tarifa_horaria, ubicacion, en_mantenimiento, creado_por)
                VALUES (?, ?, ?, ?, ?, ?::jsonb, ?, ?, ?, ?)
                RETURNING id""",
                d.categoriaId(), d.nombre(), d.marca(), d.modelo(), d.descripcion(),
                d.especificaciones() == null ? Map.of() : d.especificaciones(),
                d.tarifaHoraria(), d.ubicacion(),
                d.enMantenimiento() != null && d.enMantenimiento(), creadoPor).entero("id");
    }

    /** Actualiza solo las columnas recibidas (clave = columna de la BD). */
    public boolean actualizar(long id, Map<String, Object> columnas) {
        if (columnas.isEmpty()) return buscar(id) != null;
        List<String> asignaciones = new ArrayList<>();
        List<Object> params = new ArrayList<>();
        for (Map.Entry<String, Object> e : columnas.entrySet()) {
            boolean esJson = e.getKey().equals("especificaciones");
            asignaciones.add(e.getKey() + (esJson ? " = ?::jsonb" : " = ?"));
            params.add(esJson && e.getValue() == null ? Map.of() : e.getValue());
        }
        params.add(id);
        return bd.ejecutar("UPDATE maquinas SET " + String.join(", ", asignaciones) + " WHERE id = ?", params.toArray()) > 0;
    }

    public void publicar(long id) {
        bd.ejecutar("UPDATE maquinas SET estado = ?, publicada_en = now() WHERE id = ?", EstadoMaquina.PUBLICADA.name(), id);
    }

    public void cambiarEstado(long id, EstadoMaquina estado) {
        bd.ejecutar("UPDATE maquinas SET estado = ? WHERE id = ?", estado.name(), id);
    }

    public long contarReservas(long id) {
        return bd.uno("SELECT count(*) AS total FROM reservas WHERE maquina_id = ?", id).enteroOCero("total");
    }

    public void eliminar(long id) {
        bd.ejecutar("DELETE FROM maquinas WHERE id = ?", id);
    }

    /** Bloquea la fila de la máquina hasta que termine la transacción. Devuelve su estado o null. */
    public static EstadoMaquina bloquearFila(Consultas tx, long id) {
        Fila f = tx.uno("SELECT estado FROM maquinas WHERE id = ? FOR UPDATE", id);
        return f == null ? null : EstadoMaquina.valueOf(f.texto("estado"));
    }
}
