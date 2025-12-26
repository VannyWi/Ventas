package com.giovi.demo.service;

import com.giovi.demo.entity.DetalleVenta;
import com.giovi.demo.entity.Tienda;
import com.giovi.demo.entity.Venta;
import com.lowagie.text.*;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.awt.Color;
import java.io.IOException;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
public class VentaPdfService {

    public void exportar(HttpServletResponse response, Venta venta) throws IOException, DocumentException {
        // 1. CONFIGURACIÓN DEL PAPEL
        // Ancho de 226 puntos es estándar para 80mm. 
        // Aumentamos el alto (1200) para que no cree una segunda hoja innecesariamente en tickets largos.
        Rectangle ticketSize = new Rectangle(226, 1200); 
        Document document = new Document(ticketSize, 5, 5, 10, 10); // Márgenes reducidos a 5 para aprovechar ancho
        PdfWriter.getInstance(document, response.getOutputStream());

        document.open();

        // 2. DEFINICIÓN DE FUENTES
        Font fTiendaNombre = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 14); // Nombre Tienda
        Font fInfoTienda = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 8);     // Datos Tienda (Negrita pedida)
        
        Font fTituloNormal = FontFactory.getFont(FontFactory.HELVETICA, 8);        // Títulos normales
        Font fNegrita = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 8);        // Negritas generales
        
        // Fuentes Monoespaciadas para la tabla (alineación perfecta)
        Font fCourier = FontFactory.getFont(FontFactory.COURIER, 8);
        Font fCourierBold = FontFactory.getFont(FontFactory.COURIER_BOLD, 8);

        Font fTotalLabel = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10);
        Font fTotalValue = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 15);
        Font fPie = FontFactory.getFont(FontFactory.HELVETICA, 7);

        // Separador largo para cubrir los 80mm
        String separadorLinea = "--------------------------------------------"; 
        String separadorDoble = "============================================";

        // --- SECCIÓN 1: LOGO ---
        try {
            ClassPathResource imageResource = new ClassPathResource("static/Imagenes/logo.png");
            Image logo = Image.getInstance(imageResource.getURL());
            logo.scaleToFit(50, 50);
            logo.setAlignment(Element.ALIGN_CENTER);
            logo.setSpacingAfter(5);
            document.add(logo);
        } catch (Exception e) { } 

        // --- SECCIÓN 2: CABECERA TIENDA ---
        Tienda tienda = venta.getTienda();
        if (tienda == null && venta.getUsuario() != null) {
            tienda = venta.getUsuario().getTienda();
        }

        Paragraph pTienda = new Paragraph();
        pTienda.setAlignment(Element.ALIGN_CENTER);
        
        if (tienda != null) {
            String nombre = tienda.getNombre() != null ? tienda.getNombre().toUpperCase() : "TIENDA";
            String ruc = tienda.getRuc() != null ? tienda.getRuc() : "-";
            String dir = tienda.getDireccion() != null ? tienda.getDireccion() : "-";
            String cel = tienda.getCelular() != null ? tienda.getCelular() : "-";

            pTienda.add(new Phrase(nombre + "\n", fTiendaNombre));
            // Datos en negrita como pediste
            pTienda.add(new Phrase("RUC: " + ruc + "\n", fInfoTienda));
            pTienda.add(new Phrase("Dirección: " + dir + "\n", fInfoTienda));
            pTienda.add(new Phrase("Cel: " + cel + "\n", fInfoTienda)); // Solo "Cel"
        } else {
            pTienda.add(new Phrase("PUNTO DE VENTA\n", fTiendaNombre));
        }
        document.add(pTienda);
        
        Paragraph pSep1 = new Paragraph(separadorDoble, fCourier);
        pSep1.setAlignment(Element.ALIGN_CENTER); // Centramos para asegurar que cubra
        document.add(pSep1);

        // --- SECCIÓN 3: DATOS TICKET ---
        Paragraph pDatos = new Paragraph();
        pDatos.setAlignment(Element.ALIGN_CENTER);
        
        String nroVenta = (venta.getNumeroVenta() != null) ? venta.getNumeroVenta() : String.format("%06d", venta.getId());
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy  hh:mm a");
        String fecha = (venta.getFecha() != null) ? venta.getFecha().format(formatter) : "--/--/--";

        pDatos.add(new Phrase("TICKET DE VENTA: " + nroVenta + "\n", fNegrita));
        pDatos.add(new Phrase("Fecha y Hora: " + fecha + "\n", fTituloNormal));
        
        document.add(pDatos);
        
        Paragraph pSep2 = new Paragraph(separadorLinea, fCourier);
        pSep2.setAlignment(Element.ALIGN_CENTER);
        document.add(pSep2);

        // --- SECCIÓN 4: CLIENTE ---
        PdfPTable tCliente = new PdfPTable(2);
        tCliente.setWidthPercentage(100);
        tCliente.setWidths(new float[]{2f, 8f}); 

        String nomCli = (venta.getCliente() != null) ? venta.getCliente().getNombre().toUpperCase() : "PUBLICO GENERAL";
        String dniCli = (venta.getCliente() != null && venta.getCliente().getDni() != null) ? venta.getCliente().getDni() : "-";
        String nomCajero = (venta.getUsuario() != null) ? venta.getUsuario().getNombre() : "Sistema";

        agregarFilaInfo(tCliente, "CLI:", nomCli, fNegrita, fTituloNormal);
        agregarFilaInfo(tCliente, "DNI:", dniCli, fNegrita, fTituloNormal);
        agregarFilaInfo(tCliente, "CAJ:", nomCajero, fNegrita, fTituloNormal);

        document.add(tCliente);
        
        Paragraph pSep3 = new Paragraph(separadorLinea, fCourier);
        pSep3.setAlignment(Element.ALIGN_CENTER);
        document.add(pSep3);

        // --- SECCIÓN 5: PRODUCTOS ---
        PdfPTable tProd = new PdfPTable(4); 
        tProd.setWidthPercentage(100);
        // CORRECCIÓN: Aumenté el primer valor (1.3f) para que "CANT" entre bien sin saltar línea
        tProd.setWidths(new float[]{1.3f, 4.2f, 2f, 2.5f}); 

        tProd.addCell(celda("CANT", fCourierBold, Element.ALIGN_CENTER));
        tProd.addCell(celda("DESCRIPCION", fCourierBold, Element.ALIGN_LEFT));
        tProd.addCell(celda("P.UNIT", fCourierBold, Element.ALIGN_RIGHT));
        tProd.addCell(celda("TOTAL", fCourierBold, Element.ALIGN_RIGHT));

        List<DetalleVenta> detalles = venta.getDetalleVenta();
        if (detalles != null) {
            for (DetalleVenta d : detalles) {
                String desc = (d.getProducto() != null) ? d.getProducto().getNombre() : "-";
                // Corte de seguridad para nombres muy largos
                if(desc.length() > 22) desc = desc.substring(0, 22);

                Double precio = (d.getPrecioUnitario() != null) ? d.getPrecioUnitario() : 0.0;
                Double sub = (d.getSubtotal() != null) ? d.getSubtotal() : 0.0;

                tProd.addCell(celda(String.valueOf(d.getCantidad()), fCourier, Element.ALIGN_CENTER));
                tProd.addCell(celda(desc, fCourier, Element.ALIGN_LEFT));
                tProd.addCell(celda(String.format("%.2f", precio), fCourier, Element.ALIGN_RIGHT));
                tProd.addCell(celda(String.format("%.2f", sub), fCourier, Element.ALIGN_RIGHT));
            }
        }
        document.add(tProd);
        
        Paragraph pSep4 = new Paragraph(separadorLinea, fCourier);
        pSep4.setAlignment(Element.ALIGN_CENTER);
        document.add(pSep4);

        // --- SECCIÓN 6: TOTALES ---
        PdfPTable tTotal = new PdfPTable(2);
        tTotal.setWidthPercentage(100);
        tTotal.setWidths(new float[]{4f, 6f});

        Double total = venta.getMontoTotal() != null ? venta.getMontoTotal() : 0.0;
        Double descuento = venta.getDescuento() != null ? venta.getDescuento() : 0.0;

        if (descuento > 0) {
            agregarFilaTotal(tTotal, "SUBTOTAL:", String.format("S/. %.2f", total + descuento), fTituloNormal);
            agregarFilaTotal(tTotal, "DESCUENTO:", String.format("- S/. %.2f", descuento), fTituloNormal);
        }

        // Espacio vacío
        tTotal.addCell(celda(" ", fTituloNormal, Element.ALIGN_CENTER));
        tTotal.addCell(celda(" ", fTituloNormal, Element.ALIGN_CENTER));

        // TOTAL GRANDE
        PdfPCell lTotal = celda("TOTAL A PAGAR:", fTotalLabel, Element.ALIGN_RIGHT);
        lTotal.setVerticalAlignment(Element.ALIGN_BOTTOM);
        tTotal.addCell(lTotal);

        PdfPCell vTotal = celda("S/. " + String.format("%.2f", total), fTotalValue, Element.ALIGN_RIGHT);
        tTotal.addCell(vTotal);

        document.add(tTotal);
        document.add(new Paragraph("\n"));

        // --- SECCIÓN 7: PAGO ---
        PdfPTable tPago = new PdfPTable(2);
        tPago.setWidthPercentage(100);
        tPago.setWidths(new float[]{5f, 5f});

        String metodo = venta.getMetodoPago() != null ? venta.getMetodoPago().toUpperCase() : "EFECTIVO";
        Double pago = venta.getMontoPago() != null ? venta.getMontoPago() : 0.0;
        Double vuelto = venta.getVuelto() != null ? venta.getVuelto() : 0.0;

        agregarFilaTotal(tPago, "FORMA PAGO:", metodo, fNegrita);
        if(pago > 0) {
            agregarFilaTotal(tPago, "PAGADO CON:", String.format("S/. %.2f", pago), fTituloNormal);
            agregarFilaTotal(tPago, "SU CAMBIO:", String.format("S/. %.2f", vuelto), fNegrita);
        }
        document.add(tPago);

        // --- PIE ---
        Paragraph pSep5 = new Paragraph(separadorDoble, fCourier);
        pSep5.setAlignment(Element.ALIGN_CENTER);
        document.add(pSep5);

        Paragraph pie = new Paragraph("¡GRACIAS POR SU COMPRA!", fPie);
        pie.setAlignment(Element.ALIGN_CENTER);
        document.add(pie);

        document.close();
    }

    // --- MÉTODOS AYUDA ---

    private PdfPCell celda(String txt, Font f, int align) {
        PdfPCell c = new PdfPCell(new Phrase(txt, f));
        c.setBorder(Rectangle.NO_BORDER);
        c.setHorizontalAlignment(align);
        c.setPaddingBottom(2);
        return c;
    }

    private void agregarFilaInfo(PdfPTable t, String label, String val, Font fL, Font fV) {
        PdfPCell c1 = new PdfPCell(new Phrase(label, fL));
        c1.setBorder(Rectangle.NO_BORDER);
        c1.setHorizontalAlignment(Element.ALIGN_LEFT);
        
        PdfPCell c2 = new PdfPCell(new Phrase(val, fV));
        c2.setBorder(Rectangle.NO_BORDER);
        c2.setHorizontalAlignment(Element.ALIGN_LEFT); // Datos cliente a la izquierda
        
        t.addCell(c1);
        t.addCell(c2);
    }

    private void agregarFilaTotal(PdfPTable t, String label, String val, Font f) {
        PdfPCell c1 = new PdfPCell(new Phrase(label, f));
        c1.setBorder(Rectangle.NO_BORDER);
        c1.setHorizontalAlignment(Element.ALIGN_RIGHT);
        
        PdfPCell c2 = new PdfPCell(new Phrase(val, f));
        c2.setBorder(Rectangle.NO_BORDER);
        c2.setHorizontalAlignment(Element.ALIGN_RIGHT);
        
        t.addCell(c1);
        t.addCell(c2);
    }
}