package pe.upao.alquiler.maquinas;

import pe.upao.alquiler.config.Config;
import pe.upao.alquiler.http.Enrutador;
import pe.upao.alquiler.http.Multipart;
import pe.upao.alquiler.http.Respuesta;
import pe.upao.alquiler.http.Solicitud;
import pe.upao.alquiler.json.Json;
import pe.upao.alquiler.util.ErrorApp;
import pe.upao.alquiler.util.Paginacion;
import pe.upao.alquiler.util.Validador;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static pe.upao.alquiler.http.Enrutador.Acceso.ADMINISTRADOR;
import static pe.upao.alquiler.http.Enrutador.Acceso.PUBLICO;

/** Rutas /api/maquinas (público) y /api/admin/maquinas (HU-03, HU-08). */
public class MaquinaControlador {

    private static final List<String> ESTADOS = List.of("BORRADOR", "PUBLICADA", "RETIRADA");
    private static final BigDecimal TARIFA_MAXIMA = new BigDecimal("99999999");

    private final MaquinaServicio servicio;
    private final Config config;

    public MaquinaControlador(MaquinaServicio servicio, Config config) {
        this.servicio = servicio;
        this.config = config;
    }

    public void registrar(Enrutador r) {
        // Público
        r.get("/api/maquinas", PUBLICO, this::catalogo);
        r.get("/api/maquinas/{id}", PUBLICO, s -> Respuesta.ok(servicio.detallePublico(s.id("id"))));

        // Administrador
        r.get("/api/admin/maquinas", ADMINISTRADOR, this::listarAdmin);
        r.get("/api/admin/maquinas/{id}", ADMINISTRADOR, s -> Respuesta.ok(servicio.detalleAdmin(s.id("id"))));
        r.post("/api/admin/maquinas", ADMINISTRADOR, this::crear);
        r.put("/api/admin/maquinas/{id}", ADMINISTRADOR, this::actualizar);
        r.post("/api/admin/maquinas/{id}/publicar", ADMINISTRADOR, s -> Respuesta.ok(servicio.publicar(s.id("id"))));
        r.post("/api/admin/maquinas/{id}/retirar", ADMINISTRADOR, s -> Respuesta.ok(servicio.retirar(s.id("id"))));
        r.delete("/api/admin/maquinas/{id}", ADMINISTRADOR, s -> {
            servicio.eliminar(s.id("id"));
            return Respuesta.sinContenido();
        });

        // Fotos
        r.post("/api/admin/maquinas/{id}/fotos", ADMINISTRADOR, this::subirFotos);
        r.patch("/api/admin/maquinas/{id}/fotos/{fotoId}/principal", ADMINISTRADOR,
                s -> Respuesta.ok(Json.obj("fotos", servicio.marcarPrincipal(s.id("id"), s.id("fotoId")))));
        r.delete("/api/admin/maquinas/{id}/fotos/{fotoId}", ADMINISTRADOR,
                s -> Respuesta.ok(Json.obj("fotos", servicio.eliminarFoto(s.id("id"), s.id("fotoId")))));
    }

    /** HU-03: catálogo paginado, visible sin iniciar sesión. */
    private Respuesta catalogo(Solicitud s) {
        Validador v = Validador.de(s.query());
        Paginacion p = Paginacion.desde(v);
        v.validar();
        return Respuesta.ok(servicio.catalogo(p));
    }

    private Respuesta listarAdmin(Solicitud s) {
        Validador v = Validador.de(s.query());
        Paginacion p = Paginacion.desde(v);
        String estado = v.opcion("estado", ESTADOS, false);
        Long categoriaId = v.idPositivo("categoriaId", false, null);
        String texto = v.texto("q", 0, 80, false, null);
        v.validar();
        return Respuesta.ok(servicio.listarAdmin(estado, categoriaId, texto, p));
    }

    // ------------------------------------------------------------------
    // Crear y editar (HU-08 criterio 1)
    // ------------------------------------------------------------------
    private Respuesta crear(Solicitud s) {
        Validador v = Validador.de(s.json());
        DatosMaquina d = leerDatos(v, true);
        v.validar();
        return Respuesta.creado(servicio.crear(d, s.usuario().id()));
    }

    private Respuesta actualizar(Solicitud s) {
        long id = s.id("id");
        Map<String, Object> cuerpo = s.json();
        boolean algunCampo = MaquinaRepositorio.COLUMNAS_EDITABLES.keySet().stream().anyMatch(cuerpo::containsKey);
        if (!algunCampo) throw ErrorApp.solicitudInvalida("Envía al menos un campo para actualizar");

        Validador v = Validador.de(cuerpo);
        DatosMaquina d = leerDatos(v, false);
        v.validar();

        // Solo las columnas que llegaron en la petición
        Map<String, Object> columnas = new LinkedHashMap<>();
        Map<String, Object> valores = new LinkedHashMap<>();
        valores.put("categoriaId", d.categoriaId());
        valores.put("nombre", d.nombre());
        valores.put("marca", d.marca());
        valores.put("modelo", d.modelo());
        valores.put("descripcion", d.descripcion());
        valores.put("especificaciones", d.especificaciones());
        valores.put("tarifaDiaria", d.tarifaDiaria());
        valores.put("ubicacion", d.ubicacion());
        valores.put("enMantenimiento", d.enMantenimiento());
        for (Map.Entry<String, String> e : MaquinaRepositorio.COLUMNAS_EDITABLES.entrySet()) {
            if (cuerpo.containsKey(e.getKey())) columnas.put(e.getValue(), valores.get(e.getKey()));
        }
        return Respuesta.ok(servicio.actualizar(id, columnas));
    }

    /**
     * Lee y valida los datos de la máquina. Si "todosObligatorios" es false
     * (edición), solo valida los campos que vienen en la petición.
     */
    private DatosMaquina leerDatos(Validador v, boolean todosObligatorios) {
        boolean t = todosObligatorios;
        Long categoriaId = (t || v.tiene("categoriaId")) ? v.idPositivo("categoriaId", true, "Selecciona una categoría") : null;
        String nombre = (t || v.tiene("nombre")) ? v.texto("nombre", 1, 120, true, "El nombre es obligatorio") : null;
        String marca = (t || v.tiene("marca")) ? v.texto("marca", 1, 80, true, "La marca es obligatoria") : null;
        String modelo = (t || v.tiene("modelo")) ? v.texto("modelo", 1, 80, true, "El modelo es obligatorio") : null;
        String descripcion = v.texto("descripcion", 0, 2000, false, null);
        if (descripcion != null && descripcion.isEmpty()) descripcion = null;
        Map<String, String> especificaciones = v.mapaDeTextos("especificaciones", 30, 60, 200);
        BigDecimal tarifa = (t || v.tiene("tarifaDiaria"))
                ? v.decimalPositivo("tarifaDiaria", "La tarifa diaria", true, TARIFA_MAXIMA) : null;
        String ubicacion = (t || v.tiene("ubicacion")) ? v.texto("ubicacion", 1, 160, true, "La ubicación es obligatoria") : null;
        Boolean enMantenimiento = v.booleano("enMantenimiento", t ? Boolean.FALSE : null);
        return new DatosMaquina(categoriaId, nombre, marca, modelo, descripcion, especificaciones, tarifa, ubicacion, enMantenimiento);
    }

    // ------------------------------------------------------------------
    // Fotos: multipart/form-data con el campo "fotos" (1 a 5 archivos)
    // ------------------------------------------------------------------
    private Respuesta subirFotos(Solicitud s) {
        long id = s.id("id");
        String tipo = s.cabecera("Content-Type");
        if (!Multipart.esMultipart(tipo)) throw ErrorApp.solicitudInvalida("Envía las fotos en el campo \"fotos\"");

        int max = config.maxFotosPorMaquina;
        int limiteBytes = max * config.maxBytesPorFoto + 1024 * 1024; // + margen para cabeceras del formulario
        List<Multipart.Parte> partes = Multipart.leer(s.cuerpo(limiteBytes), tipo);

        List<MaquinaServicio.ArchivoSubido> archivos = new ArrayList<>();
        for (Multipart.Parte p : partes) {
            if (!p.esArchivo()) continue;
            if (!"fotos".equals(p.campo())) throw ErrorApp.solicitudInvalida("Envía las fotos en el campo \"fotos\"");
            if (p.nombreArchivo().isEmpty() && p.datos().length == 0) continue; // input vacío
            if (!p.tipo().equals("image/jpeg") && !p.tipo().equals("image/png")) {
                throw ErrorApp.solicitudInvalida("Solo se permiten fotos en formato JPG o PNG");
            }
            if (p.datos().length > config.maxBytesPorFoto) {
                throw ErrorApp.solicitudInvalida("Cada foto puede pesar como máximo " + config.maxBytesPorFoto / (1024 * 1024) + " MB");
            }
            archivos.add(new MaquinaServicio.ArchivoSubido(p.nombreArchivo(), p.tipo(), p.datos()));
        }
        if (archivos.size() > max) throw ErrorApp.solicitudInvalida("Puedes subir como máximo " + max + " fotos");

        return Respuesta.creado(Json.obj("fotos", servicio.agregarFotos(id, archivos)));
    }
}
