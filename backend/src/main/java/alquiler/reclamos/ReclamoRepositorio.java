package alquiler.reclamos;

import alquiler.bd.Consultas;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;

@Repository
public class ReclamoRepositorio {

    private final Consultas bd;

    public ReclamoRepositorio(Consultas bd) {
        this.bd = bd;
    }

    public long crear(String tipo, String nombre, String tipoDocumento, String documento, String domicilio,
                      String telefono, String correo, String tipoBien, String descripcionBien,
                      BigDecimal monto, String detalle, String pedido) {
        return bd.uno("""
                INSERT INTO reclamos (tipo, nombre, tipo_documento, documento, domicilio, telefono, correo,
                                      tipo_bien, descripcion_bien, monto, detalle, pedido)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                RETURNING id""",
                tipo, nombre, tipoDocumento, documento, domicilio, telefono, correo,
                tipoBien, descripcionBien, monto, detalle, pedido).entero("id");
    }
}