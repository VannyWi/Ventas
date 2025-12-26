package com.giovi.demo.service;

import com.giovi.demo.entity.DetalleVenta;
import com.giovi.demo.entity.Tienda;
import com.giovi.demo.entity.Venta;
import com.lowagie.text.*;
import com.lowagie.text.pdf.*;
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
        // 1. CONFIGURACIÓN
        Rectangle ticketSize = new Rectangle(226, 1200); 
        Document document = new Document(ticketSize, 5, 5, 10, 10);
        
        PdfWriter writer = PdfWriter.getInstance(document, response.getOutputStream());

        document.open();

        // 2. FUENTES
        Font fTiendaNombre = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 14);
        Font fInfoTienda = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 11); // Negrita     
        Font fTituloNormal = FontFactory.getFont(FontFactory.HELVETICA, 9);        
        Font fNegrita = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9);        
        
        // --- CORRECCIÓN 1: TAMAÑO DE LETRA ---
        // Bajamos a 8 para asegurar que los precios (ej. 1200.00) entren en una sola línea
        Font fCourier = FontFactory.getFont(FontFactory.COURIER, 8);
        Font fCourierBold = FontFactory.getFont(FontFactory.COURIER_BOLD, 8);

        Font fTotalLabel = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9);
        Font fTotalValue = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 15);
        Font fPie = FontFactory.getFont(FontFactory.HELVETICA, 7);

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
            pTienda.add(new Phrase("RUC: " + ruc + "\n", fInfoTienda));
            pTienda.add(new Phrase("Dirección: " + dir + "\n", fInfoTienda));
            pTienda.add(new Phrase("Cel: " + cel + "\n", fInfoTienda));
        } else {
            pTienda.add(new Phrase("PUNTO DE VENTA\n", fTiendaNombre));
        }
        document.add(pTienda);
        
        Paragraph pSep1 = new Paragraph(separadorDoble, fCourier);
        pSep1.setAlignment(Element.ALIGN_CENTER);
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
        // --- CORRECCIÓN 2: ESPACIO PARA ETIQUETAS ---
        // Aumentamos la primera columna (3.5f) para que "VENDEDOR:" entre cómodo
        tCliente.setWidths(new float[]{3.5f, 6.5f}); 

        String nomCli = (venta.getCliente() != null) ? venta.getCliente().getNombre().toUpperCase() : "PUBLICO GENERAL";
        String dniCli = (venta.getCliente() != null && venta.getCliente().getDni() != null) ? venta.getCliente().getDni() : "-";
        String nomCajero = (venta.getUsuario() != null) ? venta.getUsuario().getNombre() : "Sistema";

        agregarFilaInfo(tCliente, "CLIENTE:", nomCli, fNegrita, fTituloNormal);
        agregarFilaInfo(tCliente, "DNI:", dniCli, fNegrita, fTituloNormal);
        agregarFilaInfo(tCliente, "VENDEDOR:", nomCajero, fNegrita, fTituloNormal);

        document.add(tCliente);
        
        Paragraph pSep3 = new Paragraph(separadorLinea, fCourier);
        pSep3.setAlignment(Element.ALIGN_CENTER);
        document.add(pSep3);

        // --- SECCIÓN 5: PRODUCTOS ---
        PdfPTable tProd = new PdfPTable(4); 
        tProd.setWidthPercentage(100);
        // --- CORRECCIÓN 3: ANCHOS DE COLUMNA ---
        // Ajustamos para dar un poco más de espacio al P.Unit (2.2f) quitando a Desc
        tProd.setWidths(new float[]{1.2f, 4.1f, 2.2f, 2.5f}); 

        tProd.addCell(celda("CANT", fCourierBold, Element.ALIGN_CENTER));
        tProd.addCell(celda("DESCRIPCION", fCourierBold, Element.ALIGN_LEFT));
        tProd.addCell(celda("P.UNIT", fCourierBold, Element.ALIGN_RIGHT));
        tProd.addCell(celda("TOTAL", fCourierBold, Element.ALIGN_RIGHT));

        List<DetalleVenta> detalles = venta.getDetalleVenta();
        if (detalles != null) {
            for (DetalleVenta d : detalles) {
                String desc = (d.getProducto() != null) ? d.getProducto().getNombre() : "-";
                if(desc.length() > 20) desc = desc.substring(0, 20); // Recorte preventivo

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

        tTotal.addCell(celda(" ", fTituloNormal, Element.ALIGN_CENTER));
        tTotal.addCell(celda(" ", fTituloNormal, Element.ALIGN_CENTER));

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
        
        document.add(new Paragraph("\n"));

        // --- CÓDIGO DE BARRAS (SIN TEXTO) ---
        try {
            PdfContentByte cb = writer.getDirectContent();
            Barcode128 code128 = new Barcode128();
            code128.setCode(nroVenta);
            code128.setCodeType(Barcode128.CODE128);
            code128.setBarHeight(35f); // Altura de barras
            
            // ESTA LÍNEA ELIMINA EL TEXTO ABAJO DEL CÓDIGO:
            code128.setFont(null); 
            
            Image codeImage = code128.createImageWithBarcode(cb, null, null);
            codeImage.setAlignment(Element.ALIGN_CENTER);
            codeImage.scalePercent(140);
            
            document.add(codeImage);
        } catch (Exception e) { }
        
        document.add(new Paragraph("\n"));

        // --- PIE ---
        Paragraph pSep5 = new Paragraph(separadorDoble, fCourier);
        pSep5.setAlignment(Element.ALIGN_CENTER);
        document.add(pSep5);

        Paragraph pie = new Paragraph("¡GRACIAS POR SU COMPRA!", fPie);
        pie.setAlignment(Element.ALIGN_CENTER);
        document.add(pie);

        document.close();
    }

    // --- MÉTODOS AUXILIARES ---

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
        c2.setHorizontalAlignment(Element.ALIGN_LEFT);
        
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