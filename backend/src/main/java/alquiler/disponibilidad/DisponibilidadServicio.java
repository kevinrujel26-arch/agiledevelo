package alquiler.disponibilidad;

import org.springframework.stereotype.Service;
import alquiler.bd.Bd;
import alquiler.bd.Fila;
import alquiler.config.Config;
import alquiler.json.Json;
import alquiler.maquinas.MaquinaRepositorio;
import alquiler.modelo.Bloqueo;
import alquiler.modelo.EstadoMaquina;
import alquiler.modelo.Maquina;
import alquiler.reservas.ReservaHoraServicio;
import alquiler.util.ErrorApp;
import alquiler.util.Fechas;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** HU-09 Gestionar disponibilidad (bloqueo de fechas) */
@Service
public class DisponibilidadServicio {

    private final Bd bd;
    private final BloqueoRepositorio bloqueos;
    private final MaquinaRepositorio maquinas;
    private final Config config;
    private final ReservaHoraServicio reservaHoras;

    public DisponibilidadServicio(Bd bd, BloqueoRepositorio bloqueos, MaquinaRepositorio maquinas,
                                  Config config, ReservaHoraServicio reservaHoras) {
        this.bd = bd;
        this.bloqueos = bloqueos;
        this.maquinas = maquinas;
        this.config = config;
        this.reservaHoras = reservaHoras;
    }

    private Maquina maquinaOFallar(long id) {
        Maquina m = maquinas.buscar(id);
        if (m == null) throw ErrorApp.noEncontrado("La máquina no existe");
        return m;
    }

    public List<Bloqueo> listar(long maquinaId, boolean incluirPasados) {
        maquinaOFallar(maquinaId);
        return bloqueos.listar(bd, maquinaId, incluirPasados, Fechas.hoy(config.zonaHoraria));
    }

    /**
     * Bloquea uno o varios rangos (HU-09 criterio 1) en una sola transacción:
     * o se crean todos, o ninguno.
     */
    public List<Bloqueo> bloquear(long maquinaId, List<Fechas.Rango> rangos, String motivo, long usuarioId) {
        String hoy = Fechas.hoy(config.zonaHoraria);
        for (Fechas.Rango r : rangos) {
            if (r.fechaInicio().compareTo(hoy) < 0) {
                throw ErrorApp.solicitudInvalida("No se pueden bloquear fechas pasadas (" + r.fechaInicio() + ")");
            }
        }
        Fechas.Rango[] cruce = Fechas.buscarSuperposicion(rangos);
        if (cruce != null) {
            throw ErrorApp.solicitudInvalida("Los rangos " + cruce[0].fechaInicio() + "–" + cruce[0].fechaFin() + " y "
                    + cruce[1].fechaInicio() + "–" + cruce[1].fechaFin() + " se superponen entre sí");
        }

        return bd.transaccion(tx -> {
            if (MaquinaRepositorio.bloquearFila(tx, maquinaId) == null) throw ErrorApp.noEncontrado("La máquina no existe");

            // HU-09 criterio 3: no se puede bloquear una fecha con una reserva ya pagada
            for (Fechas.Rango r : rangos) {
                Fila reserva = bloqueos.reservaPagadaQueSeCruza(tx, maquinaId, r.fechaInicio(), r.fechaFin());
                if (reserva != null) {
                    throw ErrorApp.conflicto("No se puede bloquear: hay una reserva pagada del "
                            + reserva.fecha("fecha_inicio") + " al " + reserva.fecha("fecha_fin"));
                }
            }
            List<Bloqueo> creados = new ArrayList<>();
            for (Fechas.Rango r : rangos) {
                creados.add(bloqueos.insertar(tx, maquinaId, r.fechaInicio(), r.fechaFin(), motivo, usuarioId));
            }
            return creados;
        });
    }

    /** HU-09 criterio 4 */
    public void desbloquear(long maquinaId, long bloqueoId) {
        if (!bloqueos.eliminar(bd, maquinaId, bloqueoId)) throw ErrorApp.noEncontrado("El bloqueo no existe");
    }

    /**
     * Fechas ocupadas de una máquina publicada (calendario público).
     * Un día es "no disponible" si:
     * - Tiene un bloqueo de mantenimiento, O
     * - No queda ninguna hora de inicio válida (07:00-19:00, mínimo 1 hora, margen 1 hora)
     */
    public Map<String, Object> ocupacionPublica(long maquinaId, String desde, String hasta) {
        Maquina m = maquinas.buscar(maquinaId);
        if (m == null || m.estado() != EstadoMaquina.PUBLICADA) {
            throw ErrorApp.noEncontrado("La máquina no existe o ya no está disponible");
        }
        LocalDate d0 = LocalDate.parse(desde);
        LocalDate d1 = LocalDate.parse(hasta);

        List<Map<String, Object>> ocupados = new ArrayList<>();
        Set<LocalDate> bloqueados = new HashSet<>();

        // Bloqueos por mantenimiento: ocupan días completos
        for (Fila f : bloqueos.ocupacion(bd, maquinaId, desde, hasta)) {
            LocalDate a = LocalDate.parse(f.fecha("fecha_inicio"));
            LocalDate b = LocalDate.parse(f.fecha("fecha_fin"));
            for (LocalDate d = a; !d.isAfter(b); d = d.plusDays(1)) bloqueados.add(d);
            ocupados.add(Json.obj("fechaInicio", f.fecha("fecha_inicio"), "fechaFin", f.fecha("fecha_fin"), "tipo", f.texto("tipo")));
        }

        // Días sin ninguna hora de inicio válida (desde hoy en adelante)
        LocalDate hoy = LocalDate.parse(Fechas.hoy(config.zonaHoraria));
        LocalDate primero = d0.isBefore(hoy) ? hoy : d0;
        if (!primero.isAfter(d1)) {
            for (LocalDate d : reservaHoras.diasSinHoraLibre(maquinaId, primero, d1)) {
                if (!bloqueados.contains(d)) {
                    ocupados.add(Json.obj("fechaInicio", d.toString(), "fechaFin", d.toString(), "tipo", "RESERVAS"));
                }
            }
        }
        return Json.obj("maquinaId", maquinaId, "desde", desde, "hasta", hasta, "ocupados", ocupados);
    }
}
