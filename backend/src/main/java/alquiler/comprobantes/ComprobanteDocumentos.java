package alquiler.comprobantes;

import alquiler.bd.Fila;
import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.Rectangle;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

@Component
public class ComprobanteDocumentos {

    private static final ZoneId LIMA = ZoneId.of("America/Lima");
    private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final String LEYENDA = "Comprobante simulado, sin validez tributaria";

    public String numero(Fila c) {
        return c.texto("serie") + "-" + String.format("%08d", c.entero("numero"));
    }

    public byte[] xml(Fila c) {
        boolean factura = "FACTURA".equals(c.texto("tipo"));
        Instant emitido = c.instante("emitido_en");
        String xml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <Comprobante>
                  <Leyenda>%s</Leyenda>
                  <Tipo codigo="%s">%s</Tipo>
                  <Numero>%s</Numero>
                  <FechaEmision>%s</FechaEmision>
                  <Moneda>%s</Moneda>
                  <Emisor>MaquiRenta</Emisor>
                  <Cliente>
                    <TipoDocumento>%s</TipoDocumento>
                    <Documento>%s</Documento>
                    <Nombre>%s</Nombre>
                  </Cliente>
                  <Detalle>%s</Detalle>
                  <ValorVenta>%s</ValorVenta>
                  <Igv>%s</Igv>
                  <Total>%s</Total>
                  <Hash>%s</Hash>
                </Comprobante>
                """.formatted(
                esc(LEYENDA), factura ? "01" : "03", c.texto("tipo"), numero(c),
                emitido == null ? "" : emitido.atZone(LIMA).toLocalDate().toString(),
                c.texto("moneda").trim(),
                factura ? "RUC" : "DNI", esc(c.texto("documento_cliente")), esc(c.texto("nombre_cliente")),
                esc(detalle(c)),
                c.decimal("valor_venta").toPlainString(), c.decimal("igv").toPlainString(),
                c.decimal("total").toPlainString(), esc(c.texto("codigo_hash")));
        return xml.getBytes(StandardCharsets.UTF_8);
    }

    public byte[] pdf(Fila c) {
        boolean factura = "FACTURA".equals(c.texto("tipo"));
        Instant emitido = c.instante("emitido_en");
        Font grande = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 18);
        Font negrita = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 11);
        Font normal = FontFactory.getFont(FontFactory.HELVETICA, 10);
        Font pequena = FontFactory.getFont(FontFactory.HELVETICA_OBLIQUE, 9);

        ByteArrayOutputStream salida = new ByteArrayOutputStream();
        Document doc = new Document(PageSize.A4, 50, 50, 50, 50);
        try {
            PdfWriter.getInstance(doc, salida);
            doc.open();
            doc.add(new Paragraph("MaquiRenta", grande));
            doc.add(new Paragraph(factura ? "FACTURA ELECTRÓNICA" : "BOLETA DE VENTA ELECTRÓNICA", negrita));
            doc.add(new Paragraph(numero(c), negrita));
            Paragraph aviso = new Paragraph(LEYENDA, pequena);
            aviso.setSpacingAfter(16);
            doc.add(aviso);

            PdfPTable datos = new PdfPTable(2);
            datos.setWidthPercentage(100);
            datos.setWidths(new float[]{25, 75});
            datos.setSpacingAfter(16);
            fila(datos, "Fecha de emisión", emitido == null ? "" : FECHA.format(emitido.atZone(LIMA)), negrita, normal);
            fila(datos, factura ? "RUC" : "DNI", c.texto("documento_cliente"), negrita, normal);
            fila(datos, factura ? "Razón social" : "Cliente", c.texto("nombre_cliente"), negrita, normal);
            fila(datos, "Reserva", c.texto("reserva_codigo"), negrita, normal);
            doc.add(datos);

            PdfPTable items = new PdfPTable(2);
            items.setWidthPercentage(100);
            items.setWidths(new float[]{75, 25});
            items.addCell(celda("Descripción", negrita, Element.ALIGN_LEFT, true));
            items.addCell(celda("Importe", negrita, Element.ALIGN_RIGHT, true));
            items.addCell(celda(detalle(c), normal, Element.ALIGN_LEFT, true));
            items.addCell(celda(dinero(c, "valor_venta"), normal, Element.ALIGN_RIGHT, true));
            items.setSpacingAfter(12);
            doc.add(items);

            PdfPTable totales = new PdfPTable(2);
            totales.setWidthPercentage(45);
            totales.setHorizontalAlignment(Element.ALIGN_RIGHT);
            totales.addCell(celda("Valor de venta", normal, Element.ALIGN_LEFT, false));
            totales.addCell(celda(dinero(c, "valor_venta"), normal, Element.ALIGN_RIGHT, false));
            totales.addCell(celda("IGV (18%)", normal, Element.ALIGN_LEFT, false));
            totales.addCell(celda(dinero(c, "igv"), normal, Element.ALIGN_RIGHT, false));
            totales.addCell(celda("Total", negrita, Element.ALIGN_LEFT, false));
            totales.addCell(celda(dinero(c, "total"), negrita, Element.ALIGN_RIGHT, false));
            totales.setSpacingAfter(24);
            doc.add(totales);

            doc.add(new Paragraph("Código hash: " + c.texto("codigo_hash"), pequena));
            doc.add(new Paragraph(LEYENDA, pequena));
        } catch (DocumentException e) {
            throw new IllegalStateException(e);
        } finally {
            doc.close();
        }
        return salida.toByteArray();
    }

    private static String detalle(Fila c) {
        return "Alquiler de maquinaria: " + c.texto("maquina_nombre") + " - Reserva " + c.texto("reserva_codigo");
    }

    private static String dinero(Fila c, String columna) {
        return "S/ " + c.decimal(columna).toPlainString();
    }

    private static void fila(PdfPTable tabla, String etiqueta, String valor, Font fe, Font fv) {
        tabla.addCell(celda(etiqueta, fe, Element.ALIGN_LEFT, false));
        tabla.addCell(celda(valor, fv, Element.ALIGN_LEFT, false));
    }

    private static PdfPCell celda(String texto, Font fuente, int alineacion, boolean borde) {
        PdfPCell celda = new PdfPCell(new Phrase(texto, fuente));
        celda.setHorizontalAlignment(alineacion);
        celda.setPadding(5);
        if (!borde) celda.setBorder(Rectangle.NO_BORDER);
        return celda;
    }

    private static String esc(String s) {
        return s == null ? "" : s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;").replace("'", "&apos;");
    }
}
