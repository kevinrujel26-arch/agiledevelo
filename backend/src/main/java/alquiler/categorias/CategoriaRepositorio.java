package alquiler.categorias;

import alquiler.bd.Consultas;
import alquiler.bd.Fila;
import alquiler.modelo.Categoria;

import java.util.List;

/** Acceso a la tabla "categorias" (HU-14). */
public class CategoriaRepositorio {

    private final Consultas bd;

    public CategoriaRepositorio(Consultas bd) {
        this.bd = bd;
    }

    public List<Categoria> listarActivas() {
        return bd.consultar("SELECT id, nombre, descripcion, activa FROM categorias WHERE activa ORDER BY nombre")
                .stream().map(Categoria::desde).toList();
    }

    public List<Categoria> listarTodasConConteo() {
        return bd.consultar("""
                SELECT c.id, c.nombre, c.descripcion, c.activa,
                       count(m.id) AS total_maquinas,
                       count(m.id) FILTER (WHERE m.estado = 'PUBLICADA') AS maquinas_publicadas
                  FROM categorias c
                  LEFT JOIN maquinas m ON m.categoria_id = c.id
                 GROUP BY c.id
                 ORDER BY c.nombre""").stream().map(Categoria::desde).toList();
    }

    public Categoria buscar(long id) {
        Fila f = bd.uno("SELECT id, nombre, descripcion, activa FROM categorias WHERE id = ?", id);
        return f == null ? null : Categoria.desde(f);
    }

    /** ¿Ya existe otra categoría con ese nombre? (sin distinguir mayúsculas ni espacios) */
    public boolean nombreEnUso(String nombre, Long excluirId) {
        return bd.uno("""
                SELECT 1 FROM categorias
                 WHERE lower(trim(nombre)) = lower(trim(?))
                   AND (?::int IS NULL OR id <> ?::int)""", nombre, excluirId, excluirId) != null;
    }

    public Categoria crear(String nombre, String descripcion) {
        return Categoria.desde(bd.uno(
                "INSERT INTO categorias (nombre, descripcion) VALUES (?, ?) RETURNING id, nombre, descripcion, activa",
                nombre, descripcion));
    }

    /** nombre null = no cambia; cambiarDescripcion=false = no cambia la descripción. */
    public Categoria actualizar(long id, String nombre, boolean cambiarDescripcion, String descripcion) {
        Fila f = bd.uno("""
                UPDATE categorias
                   SET nombre = COALESCE(?, nombre),
                       descripcion = CASE WHEN ?::boolean THEN ? ELSE descripcion END
                 WHERE id = ?
             RETURNING id, nombre, descripcion, activa""", nombre, cambiarDescripcion, descripcion, id);
        return f == null ? null : Categoria.desde(f);
    }

    public Categoria cambiarEstado(long id, boolean activa) {
        Fila f = bd.uno("UPDATE categorias SET activa = ? WHERE id = ? RETURNING id, nombre, descripcion, activa", activa, id);
        return f == null ? null : Categoria.desde(f);
    }

    public long contarMaquinas(long id) {
        return bd.uno("SELECT count(*) AS total FROM maquinas WHERE categoria_id = ?", id).enteroOCero("total");
    }

    public boolean eliminar(long id) {
        return bd.ejecutar("DELETE FROM categorias WHERE id = ?", id) > 0;
    }
}
