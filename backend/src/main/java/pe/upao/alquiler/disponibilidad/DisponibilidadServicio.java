package pe.upao.alquiler.disponibilidad;

import pe.upao.alquiler.bd.Bd;
import pe.upao.alquiler.bd.Fila;
import pe.upao.alquiler.config.Config;
import pe.upao.alquiler.json.Json;
import pe.upao.alquiler.maquinas.MaquinaRepositorio;
import pe.upao.alquiler.modelo.Bloqueo;
import pe.upao.alquiler.modelo.EstadoMaquina;
import pe.upao.alquiler.modelo.Maquina;
import pe.upao.alquiler.util.ErrorApp;
import pe.upao.alquiler.util.Fechas;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** HU-09 Gestionar disponibilidad (bloqueo de fechas) */
public class DisponibilidadServicio {

    private final Bd bd;
    private final BloqueoRepositorio bloqueos;
    private final MaquinaRepositorio maquinas;
    private final Config config;

    public DisponibilidadServicio(Bd bd, BloqueoRepositorio bloqueos, MaquinaRepositorio maquinas, Config config) {
        this.bd = bd;
        this.bloqueos = bloqueos;
        this.maquinas = maquinas;
        this.config = config;
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
     * HU-09 criterio 5: se consulta en vivo, así que los cambios se ven de inmediato.
     * No se expone el motivo del bloqueo ni quién reservó.
     */
    public Map<String, Object> ocupacionPublica(long maquinaId, String desde, String hasta) {
        Maquina m = maquinas.buscar(maquinaId);
        if (m == null || m.estado() != EstadoMaquina.PUBLICADA) {
            throw ErrorApp.noEncontrado("La máquina no existe o ya no está disponible");
        }
        List<Map<String, Object>> ocupados = bloqueos.ocupacion(bd, maquinaId, desde, hasta).stream()
                .map(f -> Json.obj("fechaInicio", f.fecha("fecha_inicio"), "fechaFin", f.fecha("fecha_fin"), "tipo", f.texto("tipo")))
                .toList();
        return Json.obj("maquinaId", maquinaId, "desde", desde, "hasta", hasta, "ocupados", ocupados);
    }
}
