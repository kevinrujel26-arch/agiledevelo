package alquiler.comprobantes;

import alquiler.bd.Fila;
import alquiler.http.Solicitud;
import alquiler.util.ErrorApp;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ComprobanteControlador {

    private final ComprobanteServicio servicio;
    private final ComprobanteDocumentos documentos;

    public ComprobanteControlador(ComprobanteServicio servicio, ComprobanteDocumentos documentos) {
        this.servicio = servicio;
        this.documentos = documentos;
    }

    @GetMapping("/api/reservas/{id}/comprobante")
    public ResponseEntity<byte[]> descargar(HttpServletRequest peticion) {
        Solicitud s = Solicitud.de(peticion);
        String formato = s.query().get("formato");
        boolean xml = "xml".equals(formato);
        if (formato != null && !xml && !"pdf".equals(formato)) {
            throw ErrorApp.solicitudInvalida("Formato no válido");
        }
        Fila c = servicio.delCliente(s.usuario().id(), s.id("id"));
        String nombre = documentos.numero(c) + (xml ? ".xml" : ".pdf");
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + nombre + "\"")
                .contentType(xml ? MediaType.APPLICATION_XML : MediaType.APPLICATION_PDF)
                .body(xml ? documentos.xml(c) : documentos.pdf(c));
    }
}
