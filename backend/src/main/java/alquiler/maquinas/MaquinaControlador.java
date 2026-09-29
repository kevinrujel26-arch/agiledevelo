package alquiler.maquinas;

import alquiler.config.Config;
import alquiler.http.Multipart;
import alquiler.http.Respuesta;
import alquiler.http.Solicitud;
import alquiler.json.Json;
import alquiler.util.ErrorApp;
import alquiler.util.Paginacion;
import alquiler.util.Validador;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;


/** Rutas /api/maquinas (público) y /api/admin/maquinas (HU-03, HU-08). */
@RestController
public class MaquinaControlador {

    private static final List<String> ESTADOS = List.of("BORRADOR", "PUBLICADA", "RETIRADA");
    private static final BigDecimal TARIFA_MAXIMA = new BigDecimal("99999999");
    private static final BigDecimal HOROMETRO_MAXIMO = new BigDecimal("999999.9");
    /** Tope razonable para la tarifa por hora de una máquina (S/). */
    public static final BigDecimal TARIFA_MAXIMA_POR_HORA = new BigDecimal("100000");

    private final MaquinaServicio servicio;
    private final Config config;

    public MaquinaControlador(MaquinaServicio servicio, Config config) {
        this.servicio = servicio;
        this.config = config;
    }

    @GetMapping("/api/maquinas/{id}")
    public ResponseEntity<Object> detallePublico(HttpServletRequest peticion) {
        return Respuesta.ok(servicio.detallePublico(Solicitud.de(peticion).id("id")));
    }

    @GetMapping("/api/admin/maquinas/{id}")
    public ResponseEntity<Object> detalleAdmin(HttpServletRequest peticion) {
        return Respuesta.ok(servicio.detalleAdmin(Solicitud.de(peticion).id("id")));
    }

    @PostMapping("/api/admin/maquinas/{id}/publicar")
    public ResponseEntity<Object> publicar(HttpServletRequest peticion) {
        return Respuesta.ok(servicio.publicar(Solicitud.de(peticion).id("id")));
    }

    @PostMapping("/api/admin/maquinas/{id}/retirar")
    public ResponseEntity<Object> retirar(HttpServletRequest peticion) {
        return Respuesta.ok(servicio.retirar(Solicitud.de(peticion).id("id")));
    }

    @DeleteMapping("/api/admin/maquinas/{id}")
    public ResponseEntity<Object> eliminar(HttpServletRequest peticion) {
        servicio.eliminar(Solicitud.de(peticion).id("id"));
        return Respuesta.sinContenido();
    }

    @PatchMapping("/api/admin/maquinas/{id}/fotos/{fotoId}/principal")
    public ResponseEntity<Object> marcarPrincipal(HttpServletRequest peticion) {
        Solicitud s = Solicitud.de(peticion);
        return Respuesta.ok(Json.obj("fotos", servicio.marcarPrincipal(s.id("id"), s.id("fotoId"))));
    }

    @DeleteMapping("/api/admin/maquinas/{id}/fotos/{fotoId}")
    public ResponseEntity<Object> eliminarFoto(HttpServletRequest peticion) {
        Solicitud s = Solicitud.de(peticion);
        return Respuesta.ok(Json.obj("fotos", servicio.eliminarFoto(s.id("id"), s.id("fotoId"))));
    }

    /**
     * HU-03: catálogo paginado, visible sin iniciar sesión. Criterio 2: filtro por categoría y búsqueda por palabra clave.
     * HU-06: filtro por rango de precio por hora (precioMin y precioMax, inclusivos).
     */
    @GetMapping("/api/maquinas")
    public ResponseEntity<Object> catalogo(HttpServletRequest peticion) {
        Solicitud s = Solicitud.de(peticion);
        Validador v = Validador.de(s.query());
        Paginacion p = Paginacion.desde(v);
        MaquinaRepositorio.Filtros f = leerFiltros(v, "PUBLICADA");
        v.validar();
        return Respuesta.ok(servicio.catalogo(f, p));
    }

    @GetMapping("/api/admin/maquinas")
    public ResponseEntity<Object> listarAdmin(HttpServletRequest peticion) {
        Solicitud s = Solicitud.de(peticion);
        Validador v = Validador.de(s.query());
        Paginacion p = Paginacion.desde(v);
        String estado = v.opcion("estado", ESTADOS, false);
        MaquinaRepositorio.Filtros f = leerFiltros(v, estado);
        v.validar();
        return Respuesta.ok(servicio.listarAdmin(f, p));
    }

    /** Filtros comunes del catálogo y del listado del administrador: categoría, texto y rango de precio (HU-06). */
    private static MaquinaRepositorio.Filtros leerFiltros(Validador v, String estado) {
        Long categoriaId = v.idPositivo("categoriaId", false, null);
        String texto = v.texto("q", 0, 80, false, null);
        BigDecimal precioMin = v.decimalNoNegativoOpcional("precioMin", "El precio mínimo", TARIFA_MAXIMA);
        BigDecimal precioMax = v.decimalNoNegativoOpcional("precioMax", "El precio máximo", TARIFA_MAXIMA);
        if (precioMin != null && precioMax != null && precioMin.compareTo(precioMax) > 0) {
            v.error("precioMin", "El precio mínimo no puede ser mayor que el precio máximo");
        }
        return new MaquinaRepositorio.Filtros(estado, categoriaId, texto, precioMin, precioMax);
    }

    // ------------------------------------------------------------------
    // Crear y editar (HU-08 criterio 1)
    // ------------------------------------------------------------------
    @PostMapping("/api/admin/maquinas")
    public ResponseEntity<Object> crear(HttpServletRequest peticion) {
        Solicitud s = Solicitud.de(peticion);
        Validador v = Validador.de(s.json());
        DatosMaquina d = leerDatos(v, true);
        v.validar();
        return Respuesta.creado(servicio.crear(d, s.usuario().id()));
    }

    @PutMapping("/api/admin/maquinas/{id}")
    public ResponseEntity<Object> actualizar(HttpServletRequest peticion) {
        Solicitud s = Solicitud.de(peticion);
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
        valores.put("tarifaHoraria", d.tarifaHoraria());
        valores.put("ubicacion", d.ubicacion());
        valores.put("enMantenimiento", d.enMantenimiento());
        valores.put("horometroInicial", d.horometroInicial() == null ? BigDecimal.ZERO : d.horometroInicial());
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
        String nombre = (t || v.tiene("nombre")) ? v.textoMaquina("nombre", "El nombre", 2, 120, "El nombre es obligatorio") : null;
        String marca = (t || v.tiene("marca")) ? v.textoMaquina("marca", "La marca", 1, 80, "La marca es obligatoria") : null;
        String modelo = (t || v.tiene("modelo")) ? v.textoMaquina("modelo", "El modelo", 1, 80, "El modelo es obligatorio") : null;
        String descripcion = v.parrafo("descripcion", "La descripción", 2000);
        Map<String, String> especificaciones = v.especificaciones("especificaciones");
        BigDecimal tarifa = (t || v.tiene("tarifaHoraria"))
                ? v.decimal("tarifaHoraria", "La tarifa por hora", true, "La tarifa por hora es obligatoria", true,
                        TARIFA_MAXIMA_POR_HORA, 2)
                : null;
        if (tarifa != null) tarifa = tarifa.setScale(2, RoundingMode.UNNECESSARY);
        String ubicacion = (t || v.tiene("ubicacion"))
                ? v.lineaConLetra("ubicacion", "La ubicación", 2, 160, "La ubicación es obligatoria") : null;
        Boolean enMantenimiento = v.booleano("enMantenimiento", t ? Boolean.FALSE : null);
        // Opcional: horas que ya tenía la máquina al registrarla (si es usada). Vacío = 0.
        BigDecimal horometroInicial = v.decimal("horometroInicial", "El horómetro inicial", false, null, false,
                HOROMETRO_MAXIMO, 1);
        if (horometroInicial != null) horometroInicial = horometroInicial.setScale(1, RoundingMode.UNNECESSARY);
        return new DatosMaquina(categoriaId, nombre, marca, modelo, descripcion, especificaciones, tarifa, ubicacion,
                enMantenimiento, horometroInicial);
    }

    // ------------------------------------------------------------------
    // Fotos: multipart/form-data con el campo "fotos" (1 a 5 archivos)
    // ------------------------------------------------------------------
    @PostMapping("/api/admin/maquinas/{id}/fotos")
    public ResponseEntity<Object> subirFotos(HttpServletRequest peticion) {
        Solicitud s = Solicitud.de(peticion);
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
