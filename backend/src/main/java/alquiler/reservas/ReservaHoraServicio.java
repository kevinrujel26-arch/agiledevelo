package alquiler.reservas;

import alquiler.bd.*;
import alquiler.config.Config;
import alquiler.json.Json;
import alquiler.maquinas.MaquinaRepositorio;
import alquiler.modelo.*;
import alquiler.util.*;
import org.springframework.stereotype.Service;

import java.math.*;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;

@Service
public class ReservaHoraServicio {

    // Constantes configurables
    private static final int HORA_APERTURA = 7;  // 07:00
    private static final int HORA_CIERRE = 19;   // 19:00
    private static final int MARGEN_ENTRE_ALQUILERES_HORAS = 1;
    private static final int MAX_PENDIENTES_POR_USUARIO = 2;
    private static final int HORAS_MINIMAS = 1;
    private static final int ANTELACION_MINIMA_HORAS = 2;

    private final Bd bd;
    private final ReservaHoraRepositorio reservas;
    private final MaquinaRepositorio maquinas;
    private final Config config;

    public ReservaHoraServicio(Bd bd, ReservaHoraRepositorio reservas, MaquinaRepositorio maquinas, Config config) {
        this.bd = bd;
        this.reservas = reservas;
        this.maquinas = maquinas;
        this.config = config;
    }

    /**
     * Crea una nueva reserva por horas con validaciones completas.
     */
    public ReservaHora crearReserva(long usuarioId, long maquinaId, String fecha,
                                     int horaInicio, int horas,
                                     String nombreSolicitante, String ruc, String telefono,
                                     String email, String ubicacionObra, String detalle,
                                     boolean aceptaPolitica) {

        // Validar que el usuario tiene celular registrado
        String celularUsuario = bd.uno("SELECT telefono FROM usuarios WHERE id = ?", usuarioId).texto("telefono");
        if (celularUsuario == null || celularUsuario.trim().isEmpty()) {
            throw ErrorApp.prohibido("Debe registrar un celular antes de hacer reservas");
        }

        // Validación de máquina
        Maquina m = maquinas.buscar(maquinaId);
        if (m == null || m.estado() != EstadoMaquina.PUBLICADA) {
            throw ErrorApp.noEncontrado("La máquina no existe o no está disponible");
        }
        if (m.enMantenimiento()) {
            throw ErrorApp.conflicto("La máquina está en mantenimiento");
        }

        // Validaciones de datos del solicitante
        validarDatosDelSolicitante(nombreSolicitante, ruc, telefono, email, ubicacionObra, detalle, aceptaPolitica);

        // Validaciones de horario
        LocalDate fechaLocal = LocalDate.parse(fecha);
        ZoneId zonaHoraria = ZoneId.of(config.zonaHoraria);
        LocalDate hoy = LocalDate.now(zonaHoraria);
        Instant ahora = Instant.now();

        if (fechaLocal.compareTo(hoy) < 0) {
            throw ErrorApp.solicitudInvalida("No se puede reservar en fechas pasadas");
        }

        if (horaInicio < HORA_APERTURA || horaInicio >= HORA_CIERRE) {
            throw ErrorApp.solicitudInvalida("La hora de inicio debe estar entre " + HORA_APERTURA + ":00 y " + HORA_CIERRE + ":00");
        }

        if (horas < HORAS_MINIMAS) {
            throw ErrorApp.solicitudInvalida("Mínimo " + HORAS_MINIMAS + " hora de reserva");
        }

        int horaFin = horaInicio + horas;
        if (horaFin > HORA_CIERRE) {
            throw ErrorApp.solicitudInvalida("La reserva terminaría a las " + horaFin + ":00, pero el cierre es a las " + HORA_CIERRE + ":00");
        }

        // Crear instantes de tiempo (sin minutos, siempre en horas completas)
        Instant fechaInicio = crearInstante(fechaLocal, horaInicio, 0, zonaHoraria);
        Instant fechaFin = crearInstante(fechaLocal, horaFin, 0, zonaHoraria);
        Instant finBloqueo = fechaFin.plus(MARGEN_ENTRE_ALQUILERES_HORAS, ChronoUnit.HOURS);

        // Si la reserva es para hoy, validar anticipación mínima
        if (fechaLocal.equals(hoy)) {
            Instant antelacionMinima = ahora.plus(ANTELACION_MINIMA_HORAS, ChronoUnit.HOURS);
            if (fechaInicio.isBefore(antelacionMinima)) {
                throw ErrorApp.solicitudInvalida("Las reservas para hoy requieren al menos " + ANTELACION_MINIMA_HORAS + " horas de anticipación");
            }
        }

        // Validar límite de pendientes
        int pendientes = reservas.contarPendientesPorUsuario(usuarioId);
        if (pendientes >= MAX_PENDIENTES_POR_USUARIO) {
            throw ErrorApp.conflicto("Tienes reservas pendientes de pago. Págalas o cancélalas antes de reservar otra");
        }

        // Calcular total
        BigDecimal tarifaHoraria = m.tarifaHoraria();
        BigDecimal combustibleHora = m.combustiblePorHora();
        BigDecimal numHoras = new BigDecimal(horas);
        BigDecimal combustibleTotal = combustibleHora.multiply(numHoras).setScale(2, RoundingMode.HALF_UP);
        BigDecimal montoTotal = tarifaHoraria.add(combustibleHora).multiply(numHoras).setScale(2, RoundingMode.HALF_UP);

        // Generar código único
        String codigo = generarCodigo();

        // expira_pago_en = ahora + 2 horas
        Instant expiraPagoEn = Instant.now().plus(2, ChronoUnit.HOURS);

        // Intentar crear la reserva (el constraint de exclusión fallará si hay solapamiento)
        try {
            return reservas.insertar(
                    codigo, usuarioId, maquinaId,
                    fechaInicio, fechaFin, finBloqueo,
                    horas, tarifaHoraria, montoTotal, combustibleTotal,
                    nombreSolicitante, ruc, telefono, email,
                    ubicacionObra, detalle, aceptaPolitica, expiraPagoEn);
        } catch (Exception e) {
            // Si falla por el constraint de exclusión, es 409
            if (e.getMessage() != null && e.getMessage().contains("ex_reservas_sin_solapamiento")) {
                throw ErrorApp.conflicto("Ese horario ya no está disponible");
            }
            throw e;
        }
    }

    /**
     * Obtiene la disponibilidad de horas para un día específico.
     * Retorna por cada hora si está LIBRE, RESERVADA, PREPARACION o PASADA.
     */
    public Map<String, Object> obtenerDisponibilidadPorHoras(long maquinaId, String fecha) {
        Maquina m = maquinas.buscar(maquinaId);
        if (m == null || m.estado() != EstadoMaquina.PUBLICADA) {
            throw ErrorApp.noEncontrado("La máquina no existe o no está disponible");
        }

        LocalDate fechaLocal = LocalDate.parse(fecha);
        ZoneId zonaHoraria = ZoneId.of(config.zonaHoraria);
        LocalDate hoy = LocalDate.now(zonaHoraria);
        Instant ahora = Instant.now();

        // No se puede consultar fechas pasadas
        if (fechaLocal.compareTo(hoy) < 0) {
            throw ErrorApp.solicitudInvalida("No se puede consultar fechas pasadas");
        }

        // Obtener todas las reservas vigentes para este día
        List<ReservaHora> reservasDelDia = obtenerReservasDelDia(maquinaId, fechaLocal, zonaHoraria);

        // Crear mapa de horas: cada hora del horario
        Map<Integer, String> estadosPorHora = new LinkedHashMap<>();
        Map<Integer, Integer> horasMaximasPorHora = new LinkedHashMap<>();
        Instant antelacionMinima = ahora.plus(ANTELACION_MINIMA_HORAS, ChronoUnit.HOURS);

        for (int h = HORA_APERTURA; h < HORA_CIERRE; h++) {
            Instant inicioHora = crearInstante(fechaLocal, h, 0, zonaHoraria);
            Instant finHora = inicioHora.plus(1, ChronoUnit.HOURS);

            // Verificar si esta hora ya pasó (solo para hoy)
            if (fechaLocal.equals(hoy) && inicioHora.isBefore(antelacionMinima)) {
                estadosPorHora.put(h, "PASADA");
                horasMaximasPorHora.put(h, 0);
                continue;
            }

            // Verificar si esta hora está en margen (PREPARACION) o en reserva (RESERVADA)
            String estadoHora = null;
            for (ReservaHora r : reservasDelDia) {
                if (!esInstanteAntesDe(finHora, r.fechaInicio()) && esInstanteAntesDe(inicioHora, r.finBloqueo())) {
                    // Hora está ocupada por esta reserva o su margen (rango semiabierto [inicio, fin_bloqueo))
                    if (!esInstanteAntesDe(inicioHora, r.fechaFin()) && esInstanteAntesDe(inicioHora, r.finBloqueo())) {
                        // Hora está dentro de [fecha_fin, fin_bloqueo): es PREPARACION (margen)
                        estadoHora = "PREPARACION";
                        break;
                    } else {
                        // Hora está dentro de [fecha_inicio, fecha_fin): es RESERVADA
                        estadoHora = "RESERVADA";
                        break;
                    }
                }
            }

            if (estadoHora != null) {
                estadosPorHora.put(h, estadoHora);
                horasMaximasPorHora.put(h, 0);
            } else {
                // Calcular cuántas horas máximo se pueden reservar desde esta hora
                int horasMaximas = calcularHorasMaximas(h, reservasDelDia, fechaLocal, zonaHoraria);

                if (horasMaximas >= HORAS_MINIMAS) {
                    estadosPorHora.put(h, "LIBRE");
                    horasMaximasPorHora.put(h, horasMaximas);
                } else {
                    estadosPorHora.put(h, "PREPARACION");
                    horasMaximasPorHora.put(h, 0);
                }
            }
        }

        // Verificar si hay alguna hora de inicio válida (LIBRE con al menos 1 hora)
        boolean hayHoraValida = estadosPorHora.values().stream()
                .anyMatch(e -> e.equals("LIBRE"));

        List<Map<String, Object>> horasDetalle = new ArrayList<>();
        for (int h = HORA_APERTURA; h < HORA_CIERRE; h++) {
            horasDetalle.add(Json.obj(
                    "hora", h,
                    "estado", estadosPorHora.get(h),
                    "horasMaximas", horasMaximasPorHora.get(h)));
        }

        return Json.obj(
                "maquinaId", maquinaId,
                "fecha", fecha,
                "disponible", hayHoraValida,
                "horas", horasDetalle);
    }

    /** Días del rango [desde, hasta] SIN ninguna hora de inicio válida. Una sola consulta a la BD. */
    public Set<LocalDate> diasSinHoraLibre(long maquinaId, LocalDate desde, LocalDate hasta) {
        ZoneId zona = ZoneId.of(config.zonaHoraria);
        LocalDate hoy = LocalDate.now(zona);
        Instant ahora = Instant.now();
        Instant antelacionMinima = ahora.plus(ANTELACION_MINIMA_HORAS, ChronoUnit.HOURS);

        Instant ini = crearInstante(desde, 0, 0, zona);
        Instant fin = crearInstante(hasta.plusDays(1), 0, 0, zona);
        List<ReservaHora> todas = bd.consultar("""
                SELECT * FROM reservas
                WHERE maquina_id = ? AND estado <> ? AND fecha_inicio < ? AND fecha_fin > ?
                ORDER BY fecha_inicio""",
                maquinaId, EstadoReserva.CANCELADA.name(), fin, ini)
                .stream().map(ReservaHora::desde).toList();
        Set<LocalDate> sinHora = new TreeSet<>();
        for (LocalDate d = desde; !d.isAfter(hasta); d = d.plusDays(1)) {
            Instant diaIni = crearInstante(d, 0, 0, zona);
            Instant diaFin = crearInstante(d.plusDays(1), 0, 0, zona);
            List<ReservaHora> delDia = todas.stream()
                    .filter(r -> r.fechaInicio().isBefore(diaFin) && r.fechaFin().isAfter(diaIni))
                    .toList();

            boolean hayHora = false;
            for (int h = HORA_APERTURA; h < HORA_CIERRE && !hayHora; h++) {
                Instant inicioHora = crearInstante(d, h, 0, zona);
                // Para hoy, excluir horas que no cumplen la anticipación mínima
                if (d.equals(hoy) && inicioHora.isBefore(antelacionMinima)) {
                    continue;
                }
                if (calcularHorasMaximas(h, delDia, d, zona) >= HORAS_MINIMAS) hayHora = true;
            }
            if (!hayHora) sinHora.add(d);
        }
        return sinHora;
    }

    /**
     * Lista todas las reservas para el panel de admin.
     */
    public List<Map<String, Object>> listarTodasParaAdmin() {
        return reservas.listarTodasConDetalles().stream()
                .map(this::toAdminJsonConDetalles)
                .toList();
    }

    /**
     * Cancela una reserva (solo si está vigente).
     */
    public void cancelar(long id) {
        ReservaHora r = reservas.buscarPorId(id);
        if (r == null) {
            throw ErrorApp.noEncontrado("La reserva no existe");
        }
        if (r.estado() == EstadoReserva.CANCELADA) {
            throw ErrorApp.conflicto("La reserva ya está cancelada");
        }
        reservas.cancelar(id);
    }

    // ===== Métodos privados de validación y utilidad =====

    private void validarDatosDelSolicitante(String nombre, String ruc, String telefono,
                                            String email, String ubicacion, String detalle,
                                            boolean aceptaPolitica) {
        if (nombre == null || nombre.trim().isEmpty()) {
            throw ErrorApp.solicitudInvalida("El nombre del solicitante es obligatorio");
        }
        if (nombre.trim().length() > 160) {
            throw ErrorApp.solicitudInvalida("El nombre no puede exceder 160 caracteres");
        }

        if (!ValidadorRuc.esDocumentoValido(ruc)) {
            throw ErrorApp.solicitudInvalida("Documento (DNI o RUC) no válido");
        }

        if (telefono == null || telefono.trim().isEmpty()) {
            throw ErrorApp.solicitudInvalida("El teléfono es obligatorio");
        }
        if (telefono.trim().length() > 20) {
            throw ErrorApp.solicitudInvalida("El teléfono no puede exceder 20 caracteres");
        }

        if (email == null || email.trim().isEmpty()) {
            throw ErrorApp.solicitudInvalida("El email es obligatorio");
        }
        if (!esEmailValido(email)) {
            throw ErrorApp.solicitudInvalida("El email no es válido");
        }

        if (ubicacion == null || ubicacion.trim().isEmpty()) {
            throw ErrorApp.solicitudInvalida("La ubicación de la obra es obligatoria");
        }
        if (ubicacion.trim().length() > 200) {
            throw ErrorApp.solicitudInvalida("La ubicación no puede exceder 200 caracteres");
        }

        if (detalle != null && detalle.trim().length() > 300) {
            throw ErrorApp.solicitudInvalida("El detalle no puede exceder 300 caracteres");
        }

        if (!aceptaPolitica) {
            throw ErrorApp.solicitudInvalida("Debes aceptar la política de privacidad");
        }
    }

    private boolean esEmailValido(String email) {
        return email != null && email.matches("^[A-Za-z0-9+_.-]+@(.+)$");
    }

    private List<ReservaHora> obtenerReservasDelDia(long maquinaId, LocalDate fecha, ZoneId zonaHoraria) {
        Instant inicioDelDia = crearInstante(fecha, 0, 0, zonaHoraria);
        Instant finDelDia = crearInstante(fecha.plusDays(1), 0, 0, zonaHoraria);

        return bd.consultar("""
                SELECT * FROM reservas
                WHERE maquina_id = ? AND estado <> ? AND fecha_inicio < ? AND fecha_fin > ?
                ORDER BY fecha_inicio""",
                maquinaId,
                EstadoReserva.CANCELADA.name(),
                finDelDia,
                inicioDelDia)
                .stream()
                .map(ReservaHora::desde)
                .toList();
    }

    /** Horas consecutivas máximas reservables empezando en horaInicio (0 si no se puede). Considera margen antes y después. */
    private int calcularHorasMaximas(int horaInicio, List<ReservaHora> reservasDelDia,
                                       LocalDate fecha, ZoneId zonaHoraria) {
        Instant inicio = crearInstante(fecha, horaInicio, 0, zonaHoraria);
        int max = 0;
        for (int n = 1; horaInicio + n <= HORA_CIERRE; n++) {
            Instant fin = crearInstante(fecha, horaInicio + n, 0, zonaHoraria);
            boolean choca = reservasDelDia.stream().anyMatch(r ->
                    inicio.isBefore(r.finBloqueo())
                            && fin.isAfter(r.fechaInicio().minus(MARGEN_ENTRE_ALQUILERES_HORAS, ChronoUnit.HOURS)));
            if (choca) break;
            max = n;
        }
        return max;
    }

    private Instant crearInstante(LocalDate fecha, int hora, int minuto, ZoneId zonaHoraria) {
        return fecha.atTime(hora, minuto)
                .atZone(zonaHoraria)
                .toInstant();
    }

    private boolean esInstanteAntesDe(Instant a, Instant b) {
        return a.isBefore(b);
    }

    private boolean esInstanteDespuesDe(Instant a, Instant b) {
        return a.isAfter(b);
    }

    private String generarCodigo() {
        String caracteres = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
        StringBuilder codigo = new StringBuilder();
        Random random = new Random();

        for (int i = 0; i < 10; i++) {
            codigo.append(caracteres.charAt(random.nextInt(caracteres.length())));
        }

        return codigo.toString();
    }

    private Map<String, Object> toAdminJson(ReservaHora r) {
        return Json.obj(
                "id", r.id(),
                "codigo", r.codigo(),
                "clienteId", r.clienteId(),
                "maquinaId", r.maquinaId(),
                "fechaInicio", r.fechaInicio(),
                "fechaFin", r.fechaFin(),
                "horas", r.horas(),
                "tarifaHoraria", r.tarifaHoraria(),
                "montoTotal", r.montoTotal(),
                "estado", r.estado().name(),
                "nombreSolicitante", r.nombreSolicitante(),
                "ruc", r.ruc(),
                "telefonoContacto", r.telefonoContacto(),
                "emailContacto", r.emailContacto(),
                "ubicacionObra", r.ubicacionObra(),
                "detalle", r.detalle(),
                "creadoEn", r.creadoEn(),
                "canceladaEn", r.canceladaEn());
    }

    private Map<String, Object> toAdminJsonConDetalles(Fila f) {
        return Json.obj(
                "id", f.entero("id"),
                "codigo", f.texto("codigo"),
                "clienteId", f.entero("cliente_id"),
                "clienteNombre", f.texto("cliente_nombre"),
                "clienteCorreo", f.texto("cliente_correo"),
                "maquinaId", f.entero("maquina_id"),
                "maquinaNombre", f.texto("maquina_nombre"),
                "fechaInicio", f.instante("fecha_inicio"),
                "fechaFin", f.instante("fecha_fin"),
                "horas", f.entero("horas"),
                "tarifaHoraria", f.decimal("tarifa_horaria"),
                "montoTotal", f.decimal("monto_total"),
                "combustibleTotal", f.decimal("combustible_total"),
                "estado", f.texto("estado"),
                "nombreSolicitante", f.texto("nombre_solicitante"),
                "ruc", f.texto("ruc"),
                "telefonoContacto", f.texto("telefono_contacto"),
                "emailContacto", f.texto("email_contacto"),
                "ubicacionObra", f.texto("ubicacion_obra"),
                "detalle", f.texto("detalle"),
                "creadoEn", f.instante("creado_en"),
                "canceladaEn", f.instante("cancelada_en"));
    }

    /** HU-07 criterio 5: cancela las reservas que no se pagaron en 2 horas. Devuelve cuántas canceló. */
    public int cancelarVencidas() {
        return reservas.cancelarVencidas();
    }
}
