package alquiler.categorias;

import alquiler.modelo.Categoria;
import alquiler.util.ErrorApp;

import java.util.List;

/** HU-14 Gestionar categorías de maquinaria */
public class CategoriaServicio {

    private static final String NO_EXISTE = "La categoría no existe";
    private final CategoriaRepositorio repo;

    public CategoriaServicio(CategoriaRepositorio repo) {
        this.repo = repo;
    }

    /** Catálogo público: solo activas (HU-14 criterio 3). */
    public List<Categoria> listarActivas() {
        return repo.listarActivas();
    }

    /** Panel de administración: todas, con cuántas máquinas tiene cada una. */
    public List<Categoria> listarTodas() {
        return repo.listarTodasConConteo();
    }

    /** HU-14 criterio 4: el nombre no puede repetirse. */
    public Categoria crear(String nombre, String descripcion) {
        if (repo.nombreEnUso(nombre, null)) throw ErrorApp.conflicto("Ya existe una categoría con ese nombre");
        return repo.crear(nombre, descripcion);
    }

    public Categoria actualizar(long id, String nombre, boolean cambiarDescripcion, String descripcion) {
        if (nombre != null && repo.nombreEnUso(nombre, id)) {
            throw ErrorApp.conflicto("Ya existe una categoría con ese nombre");
        }
        Categoria c = repo.actualizar(id, nombre, cambiarDescripcion, descripcion);
        if (c == null) throw ErrorApp.noEncontrado(NO_EXISTE);
        return c;
    }

    public Categoria cambiarEstado(long id, boolean activa) {
        Categoria c = repo.cambiarEstado(id, activa);
        if (c == null) throw ErrorApp.noEncontrado(NO_EXISTE);
        return c;
    }

    /** HU-14 criterio 2: con máquinas no se elimina, solo se desactiva. */
    public void eliminar(long id) {
        if (repo.contarMaquinas(id) > 0) {
            throw ErrorApp.conflicto("La categoría tiene máquinas registradas y no se puede eliminar. Desactívala en su lugar");
        }
        if (!repo.eliminar(id)) throw ErrorApp.noEncontrado(NO_EXISTE);
    }
}
