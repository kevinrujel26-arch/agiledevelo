package alquiler.reclamos;

import alquiler.http.Respuesta;
import alquiler.http.Solicitud;
import alquiler.json.Json;
import alquiler.util.Validador;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.Year;
import java.util.List;
import java.util.regex.Pattern;

@RestController
public class ReclamoControlador {

    private static final Pattern DNI = Pattern.compile("^\\d{8}$");
    private static final Pattern OTRO_DOCUMENTO = Pattern.compile("^[A-Za-z0-9]{6,12}$");

    private final ReclamoRepositorio repositorio;

    public ReclamoControlador(ReclamoRepositorio repositorio) {
        this.repositorio = repositorio;
    }

    @PostMapping("/api/reclamos")
    public ResponseEntity<Object> registrar(HttpServletRequest peticion) {
        Solicitud s = Solicitud.de(peticion);
        Validador v = Validador.de(s.json());
        String tipo = v.opcion("tipo", List.of("RECLAMO", "QUEJA"), true);
        String nombre = v.nombrePersona("nombre");
        String tipoDocumento = v.opcion("tipoDocumento", List.of("DNI", "CE", "PASAPORTE"), true);
        String documento = v.texto("documento", 1, 12, true, "El documento es obligatorio");
        String domicilio = v.linea("domicilio", "El domicilio", 5, 200, true, "El domicilio es obligatorio");
        String telefono = v.celular("telefono", true);
        String correo = v.correo("correo");
        String tipoBien = v.opcion("tipoBien", List.of("PRODUCTO", "SERVICIO"), true);
        String descripcionBien = v.linea("descripcionBien", "La descripción del bien", 3, 200, true,
                "Indica el servicio o producto relacionado");
        BigDecimal monto = v.decimal("monto", "El monto", false, null, false, new BigDecimal("1000000"), 2);
        String detalle = v.parrafo("detalle", "El detalle", 2000);
        String pedido = v.parrafo("pedido", "El pedido", 1000);
        if (detalle == null) v.error("detalle", "El detalle es obligatorio");
        if (pedido == null) v.error("pedido", "El pedido del consumidor es obligatorio");

        if (tipoDocumento != null && documento != null) {
            boolean valido = "DNI".equals(tipoDocumento)
                    ? DNI.matcher(documento).matches()
                    : OTRO_DOCUMENTO.matcher(documento).matches();
            if (!valido) {
                v.error("documento", "DNI".equals(tipoDocumento)
                        ? "El DNI debe tener 8 dígitos"
                        : "El documento debe tener entre 6 y 12 letras o números");
            }
        }
        v.validar();

        long id = repositorio.crear(tipo, nombre, tipoDocumento, documento, domicilio, telefono, correo,
                tipoBien, descripcionBien, monto, detalle, pedido);
        String codigo = String.format("HR-%d-%06d", Year.now().getValue(), id);
        return Respuesta.creado(Json.obj("codigo", codigo, "mensaje", "Tu " + tipo.toLowerCase() + " fue registrado"));
    }
}