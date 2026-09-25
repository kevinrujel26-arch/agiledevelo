package alquiler.maquinas;

import alquiler.bd.Bd;
import alquiler.bd.Fila;
import alquiler.categorias.CategoriaRepositorio;
import alquiler.config.Config;
import alquiler.modelo.Categoria;
import alquiler.modelo.EstadoMaquina;
import alquiler.modelo.Foto;
import alquiler.modelo.Maquina;
import alquiler.util.ErrorApp;
import alquiler.util.Paginacion;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** HU-03 Ver catálogo · HU-04 Ver detalle (base) · HU-08 Registrar y publicar máquina */
public class MaquinaServicio {

    /** Archivo recibido del formulario, ya validado en tipo y tamaño. */
    public record ArchivoSubido(String nombreOriginal, String tipoMime, byte[] datos) {
    }

    private final Bd bd;
    private final MaquinaRepositorio maquinas;
    private final FotoRepositorio fotos;
    private final CategoriaRepositorio categorias;
    private final AlmacenFotos almacen;
    private final Config config;

    public MaquinaServicio(Bd bd, MaquinaRepositorio maquinas, FotoRepositorio fotos,
                           CategoriaRepositorio categorias, AlmacenFotos almacen, Config config) {
        this.bd = bd;
        this.maquinas = maquinas;
        this.fotos = fotos;
        this.categorias = categorias;
        this.almacen = almacen;
        this.config = config;
    }

    // ------------------------------------------------------------------
    // Catálogo público (HU-03)
    // ------------------------------------------------------------------
    public Map<String, Object> catalogo(Long categoriaId, String texto, Paginacion p) {
        List<Map<String, Object>> tarjetas = maquinas.listarPublicadas(categoriaId, texto, p.limite(), p.desplazamiento())
                .stream().map(Maquina::tarjetaJson).toList();
        return p.respuesta(tarjetas, maquinas.contarPublicadas(categoriaId, texto));
    }

    /** Detalle público: solo si está publicada (la URL se puede compartir, HU-04 criterio 4). */
    public Map<String, Object> detallePublico(long id) {
        Maquina m = maquinas.buscar(id);
        if (m == null || !m.estaPublicada()) {
            throw ErrorApp.noEncontrado("La máquina no existe o ya no está disponible");
        }
        return m.detalleJson(fotos.listar(bd, id));
    }

    // ------------------------------------------------------------------
    // Administración (HU-08)
    // ------------------------------------------------------------------
    public Map<String, Object> listarAdmin(String estado, Long categoriaId, String texto, Paginacion p) {
        List<Map<String, Object>> filas = maquinas.listarAdmin(estado, categoriaId, texto, p.limite(), p.desplazamiento())
                .stream().map(Maquina::filaAdminJson).toList();
        return p.respuesta(filas, maquinas.contarAdmin(estado, categoriaId, texto));
    }

    public Map<String, Object> detalleAdmin(long id) {
        return buscarOFallar(id).detalleJson(fotos.listar(bd, id));
    }

    private Maquina buscarOFallar(long id) {
        Maquina m = maquinas.buscar(id);
        if (m == null) throw ErrorApp.noEncontrado("La máquina no existe");
        return m;
    }

    private void validarCategoria(long categoriaId) {
        Categoria c = categorias.buscar(categoriaId);
        if (c == null) throw ErrorApp.solicitudInvalida("La categoría seleccionada no existe");
        if (!c.activa()) throw ErrorApp.solicitudInvalida("La categoría seleccionada está desactivada");
    }

    /** Se guarda siempre como BORRADOR (HU-08 criterio 4). */
    public Map<String, Object> crear(DatosMaquina datos, long usuarioId) {
        validarCategoria(datos.categoriaId());
        long id = maquinas.crear(datos, usuarioId);
        return detalleAdmin(id);
    }

    /** HU-08 criterio 6: se puede editar incluso después de publicada. Recibe solo los campos a cambiar. */
    public Map<String, Object> actualizar(long id, Map<String, Object> columnas) {
        Object categoriaId = columnas.get("categoria_id");
        if (categoriaId != null) validarCategoria((Long) categoriaId);
        if (!maquinas.actualizar(id, columnas)) throw ErrorApp.noEncontrado("La máquina no existe");
        return detalleAdmin(id);
    }

    /** HU-08 criterios 3 y 5: publicar exige foto principal; aparece de inmediato en el catálogo. */
    public Map<String, Object> publicar(long id) {
        Maquina m = buscarOFallar(id);
        if (m.estaPublicada()) return detalleAdmin(id);
        boolean tienePrincipal = fotos.listar(bd, id).stream().anyMatch(Foto::esPrincipal);
        if (!tienePrincipal) {
            throw ErrorApp.conflicto("Para publicar, sube al menos una foto y marca una como principal");
        }
        validarCategoria(m.categoriaId());
        maquinas.publicar(id);
        return detalleAdmin(id);
    }

    /** HU-08 criterio 7: retirar del catálogo sin borrar el historial de reservas. */
    public Map<String, Object> retirar(long id) {
        Maquina m = buscarOFallar(id);
        if (!m.estaPublicada()) throw ErrorApp.conflicto("Solo se puede retirar una máquina publicada");
        maquinas.cambiarEstado(id, EstadoMaquina.RETIRADA);
        return detalleAdmin(id);
    }

    /** Solo se eliminan borradores sin reservas; lo demás se retira. */
    public void eliminar(long id) {
        Maquina m = buscarOFallar(id);
        if (m.estado() != EstadoMaquina.BORRADOR) {
            throw ErrorApp.conflicto("Solo se pueden eliminar borradores. Una máquina publicada se retira del catálogo");
        }
        if (maquinas.contarReservas(id) > 0) throw ErrorApp.conflicto("La máquina tiene reservas y no se puede eliminar");
        List<Foto> susFotos = fotos.listar(bd, id);
        maquinas.eliminar(id);
        susFotos.forEach(f -> almacen.borrar(f.ruta()));
    }

    // ------------------------------------------------------------------
    // Fotos (HU-08 criterios 2 y 3)
    // ------------------------------------------------------------------
    public List<Foto> agregarFotos(long maquinaId, List<ArchivoSubido> archivos) {
        if (archivos.isEmpty()) throw ErrorApp.solicitudInvalida("Selecciona al menos una foto");
        for (ArchivoSubido a : archivos) {
            if (!AlmacenFotos.esImagenReal(a.datos(), a.tipoMime())) {
                throw ErrorApp.solicitudInvalida("\"" + a.nombreOriginal() + "\" no es una imagen JPG o PNG válida");
            }
        }
        int max = config.maxFotosPorMaquina;
        List<String> guardadas = new ArrayList<>();
        try {
            bd.transaccion(tx -> {
                if (MaquinaRepositorio.bloquearFila(tx, maquinaId) == null) {
                    throw ErrorApp.noEncontrado("La máquina no existe");
                }
                Fila r = fotos.resumen(tx, maquinaId);
                long total = r.enteroOCero("total");
                boolean tienePrincipal = r.bool("tiene_principal");
                long maxOrden = r.enteroOCero("max_orden");

                if (total + archivos.size() > max) {
                    long disponibles = max - total;
                    throw ErrorApp.solicitudInvalida(disponibles > 0
                            ? "La máquina ya tiene " + total + " foto(s). Solo puedes subir " + disponibles + " más (máximo " + max + ")"
                            : "La máquina ya tiene el máximo de " + max + " fotos");
                }
                for (int i = 0; i < archivos.size(); i++) {
                    ArchivoSubido a = archivos.get(i);
                    String ruta = almacen.guardar(a.datos(), a.tipoMime());
                    guardadas.add(ruta);
                    // La primera foto se vuelve principal si no había una
                    fotos.insertar(tx, maquinaId, ruta, a.tipoMime(), !tienePrincipal && i == 0, maxOrden + i + 1);
                }
                return null;
            });
        } catch (RuntimeException e) {
            guardadas.forEach(almacen::borrar); // si falló, no dejamos archivos sueltos
            throw e;
        }
        return fotos.listar(bd, maquinaId);
    }

    public List<Foto> marcarPrincipal(long maquinaId, long fotoId) {
        bd.transaccion(tx -> {
            if (!fotos.pertenece(tx, fotoId, maquinaId)) throw ErrorApp.noEncontrado("La foto no existe");
            fotos.marcarPrincipal(tx, maquinaId, fotoId);
            return null;
        });
        return fotos.listar(bd, maquinaId);
    }

    public List<Foto> eliminarFoto(long maquinaId, long fotoId) {
        String ruta = bd.transaccion(tx -> {
            EstadoMaquina estado = MaquinaRepositorio.bloquearFila(tx, maquinaId);
            if (estado == null) throw ErrorApp.noEncontrado("La máquina no existe");

            List<Foto> actuales = fotos.listar(tx, maquinaId);
            Foto foto = actuales.stream().filter(f -> f.id() == fotoId).findFirst()
                    .orElseThrow(() -> ErrorApp.noEncontrado("La foto no existe"));

            if (estado == EstadoMaquina.PUBLICADA && actuales.size() == 1) {
                throw ErrorApp.conflicto("Una máquina publicada debe tener al menos una foto");
            }
            fotos.eliminar(tx, fotoId);

            // Si se borró la principal, la siguiente pasa a ser principal
            if (foto.esPrincipal()) {
                actuales.stream().filter(f -> f.id() != fotoId).findFirst()
                        .ifPresent(sig -> fotos.marcarPrincipal(tx, maquinaId, sig.id()));
            }
            return foto.ruta();
        });
        almacen.borrar(ruta);
        return fotos.listar(bd, maquinaId);
    }
}
