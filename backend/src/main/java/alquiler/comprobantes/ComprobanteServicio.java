package alquiler.comprobantes;

import alquiler.bd.Bd;
import alquiler.bd.Fila;
import alquiler.util.ErrorApp;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

@Service
public class ComprobanteServicio {

    private static final int MAX_INTENTOS = 5;
    private static final int MINUTOS_REINTENTO = 10;
    private static final BigDecimal FACTOR_IGV = new BigDecimal("1.18");

    private final Bd bd;
    private final EmisorSimulado emisor;

    public ComprobanteServicio(Bd bd, EmisorSimulado emisor) {
        this.bd = bd;
        this.emisor = emisor;
    }

    public void emitir(long reservaId) {
        if (bd.uno("SELECT id FROM comprobantes WHERE reserva_id = ?", reservaId) != null) return;
        Fila r = bd.uno("""
                SELECT p.id AS pago_id, r.ruc, r.nombre_solicitante, r.monto_total
                  FROM reservas r
                  JOIN pagos p ON p.reserva_id = r.id AND p.estado = 'APROBADO'
                 WHERE r.id = ?
                 ORDER BY p.id DESC LIMIT 1""", reservaId);
        if (r == null) return;

        String ruc = r.texto("ruc");
        String solicitante = r.texto("nombre_solicitante");
        String documento = ruc == null || ruc.isBlank() ? "00000000" : ruc.trim();
        String nombre = solicitante == null || solicitante.isBlank() ? "CLIENTES VARIOS" : solicitante.trim();
        boolean factura = documento.length() == 11;
        BigDecimal total = r.decimal("monto_total");
        BigDecimal valorVenta = total.divide(FACTOR_IGV, 2, RoundingMode.HALF_UP);
        BigDecimal igv = total.subtract(valorVenta);

        Fila c = bd.uno("""
                INSERT INTO comprobantes (reserva_id, pago_id, tipo, serie, numero, documento_cliente,
                                          nombre_cliente, valor_venta, igv, total, proximo_intento)
                VALUES (?, ?, ?, ?, nextval(?::regclass), ?, ?, ?, ?, ?, now() + interval '1 minute')
                ON CONFLICT (reserva_id) DO NOTHING
                RETURNING *""",
                reservaId, r.entero("pago_id"),
                factura ? "FACTURA" : "BOLETA",
                factura ? "F001" : "B001",
                factura ? "seq_factura" : "seq_boleta",
                documento, nombre, valorVenta, igv, total);
        if (c != null) enviar(c);
    }

    public Fila delCliente(long usuarioId, long reservaId) {
        Fila c = bd.uno("""
                SELECT c.*, r.codigo AS reserva_codigo, m.nombre AS maquina_nombre
                  FROM comprobantes c
                  JOIN reservas r ON r.id = c.reserva_id
                  JOIN maquinas m ON m.id = r.maquina_id
                 WHERE c.reserva_id = ? AND r.cliente_id = ?""", reservaId, usuarioId);
        if (c == null) throw new ErrorApp(404, "Comprobante no encontrado");
        if (!"ACEPTADO".equals(c.texto("estado"))) throw ErrorApp.conflicto("El comprobante todavía no fue emitido");
        return c;
    }

    public int reintentarPendientes() {        List<Fila> pendientes = bd.consultar("SELECT * FROM comprobantes WHERE estado = 'PENDIENTE' AND proximo_intento <= now() ORDER BY id");
        pendientes.forEach(this::enviar);
        return pendientes.size();
    }

    void enviar(Fila c) {
        long id = c.entero("id");
        int intentos = (int) (long) c.entero("intentos") + 1;
        EmisorSimulado.Respuesta respuesta = emisor.emitir(
                c.texto("serie"), (int) (long) c.entero("numero"),
                c.texto("documento_cliente"), c.decimal("total"));
        if (respuesta.aceptado()) {
            bd.ejecutar("""
                    UPDATE comprobantes
                       SET estado = 'ACEPTADO', codigo_hash = ?, intentos = ?, motivo = NULL,
                           proximo_intento = NULL, emitido_en = now()
                     WHERE id = ?""", respuesta.hash(), intentos, id);
        } else if (intentos >= MAX_INTENTOS) {
            bd.ejecutar("""
                    UPDATE comprobantes
                       SET estado = 'RECHAZADO', motivo = ?, intentos = ?, proximo_intento = NULL
                     WHERE id = ?""", respuesta.motivo(), intentos, id);
        } else {
            bd.ejecutar("""
                    UPDATE comprobantes
                       SET motivo = ?, intentos = ?, proximo_intento = now() + make_interval(mins => ?)
                     WHERE id = ?""", respuesta.motivo(), intentos, MINUTOS_REINTENTO, id);
        }
    }
}
