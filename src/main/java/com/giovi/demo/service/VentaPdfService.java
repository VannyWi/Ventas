package com.giovi.demo.service;

import com.giovi.demo.entity.DetalleVenta;
import com.giovi.demo.entity.Venta;
import com.lowagie.text.*;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import org.springframework.stereotype.Service;

import jakarta.servlet.http.HttpServletResponse;
import java.awt.Color;
import java.io.IOException;
import java.text.DecimalFormat;
import java.time.format.DateTimeFormatter;

@Service
public class VentaPdfService {

    public void exportar(HttpServletResponse response, Venta venta) throws IOException {
        // 1. TAMAÑO TICKET (80mm)
        // Ancho: 226 puntos (~80mm). Alto: 1000 puntos (lo suficiente para que no corte antes)
        // La mayoría de drivers de impresora cortan automáticamente al final del contenido.
        Rectangle ticketSize = new Rectangle(226, 1000); 
        
        // Márgenes muy pequeños (5 puntos) para aprovechar el papel
        Document document = new Document(ticketSize, 5, 5, 5, 5); 
        PdfWriter.getInstance(document, response.getOutputStream());

        document.open();

        // 2. FUENTES ADAPTADAS (Más pequeñas)
        Font fontTitulo = FontFactory.getFont(FontFactory.HELVETICA_BOLD);
        fontTitulo.setSize(12); // Título legible pero no gigante
        fontTitulo.setColor(Color.BLACK); // Tickets siempre en negro

        Font fontNegrita = FontFactory.getFont(FontFactory.HELVETICA_BOLD);
        fontNegrita.setSize(8); // Datos importantes

        Font fontNormal = FontFactory.getFont(FontFactory.HELVETICA);
        fontNormal.setSize(8); // Texto general
        
        Font fontPeque = FontFactory.getFont(FontFactory.HELVETICA);
        fontPeque.setSize(7); // Para detalles largos si es necesario
        
        DecimalFormat df = new DecimalFormat("0.00");
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

        // --- ENCABEZADO ---
        Paragraph titulo = new Paragraph("TICKET DE VENTA", fontTitulo);
        titulo.setAlignment(Paragraph.ALIGN_CENTER);
        document.add(titulo);

        // Datos Tienda
        String nombreTienda = (venta.getTienda() != null) ? venta.getTienda().getNombre() : "MI TIENDA";
        String dirTienda = (venta.getTienda() != null && venta.getTienda().getDireccion() != null) 
                           ? venta.getTienda().getDireccion() : "";
        
        Paragraph infoTienda = new Paragraph(nombreTienda + "\n" + dirTienda, fontNormal);
        infoTienda.setAlignment(Paragraph.ALIGN_CENTER);
        infoTienda.setSpacingAfter(5);
        document.add(infoTienda);

        document.add(new Paragraph("---------------------------------------------", fontNormal));

        // --- DATOS GENERALES ---
        PdfPTable tablaDatos = new PdfPTable(1); // 1 sola columna para que no se apriete
        tablaDatos.setWidthPercentage(100);
        
        tablaDatos.addCell(getCellNoBorder("Folio: " + (venta.getNumeroVenta() != null ? venta.getNumeroVenta() : "---"), fontNegrita));
        tablaDatos.addCell(getCellNoBorder("Fecha: " + (venta.getFecha() != null ? venta.getFecha().format(formatter) : "--/--"), fontNormal));
        
        String nomCliente = (venta.getCliente() != null) ? venta.getCliente().getNombre() : "Público General";
        tablaDatos.addCell(getCellNoBorder("Cliente: " + nomCliente, fontNormal));
        
        String nomUsuario = (venta.getUsuario() != null) ? venta.getUsuario().getNombre() : "Cajero";
        tablaDatos.addCell(getCellNoBorder("Atendió: " + nomUsuario, fontNormal));

        document.add(tablaDatos);

        document.add(new Paragraph("---------------------------------------------", fontNormal));

        // --- TABLA PRODUCTOS ---
        // Ajustamos anchos: Producto(50%), Cant(15%), Precio(15%), Total(20%)
        PdfPTable tablaProd = new PdfPTable(4);
        tablaProd.setWidthPercentage(100);
        tablaProd.setWidths(new float[] {3f, 0.8f, 1.1f, 1.1f}); 
        tablaProd.setSpacingBefore(2);

        // Cabecera sin fondo negro (ahorra tinta térmica)
        tablaProd.addCell(getHeaderCell("Prod", fontNegrita));
        tablaProd.addCell(getHeaderCell("Cant", fontNegrita));
        tablaProd.addCell(getHeaderCell("P.U.", fontNegrita));
        tablaProd.addCell(getHeaderCell("Total", fontNegrita));

        if (venta.getDetalleVenta() != null) {
            for (DetalleVenta dt : venta.getDetalleVenta()) {
                String prodNombre = (dt.getProducto() != null) ? dt.getProducto().getNombre() : "Item";
                // Recortar nombre si es muy largo
                if(prodNombre.length() > 20) prodNombre = prodNombre.substring(0, 20) + "..";

                tablaProd.addCell(getCellNoBorder(prodNombre, fontPeque));
                
                PdfPCell cellCant = getCellNoBorder(String.valueOf(dt.getCantidad()), fontPeque);
                cellCant.setHorizontalAlignment(Element.ALIGN_CENTER);
                tablaProd.addCell(cellCant);
                
                PdfPCell cellPrecio = getCellNoBorder(df.format(dt.getPrecioUnitario()), fontPeque);
                cellPrecio.setHorizontalAlignment(Element.ALIGN_RIGHT);
                tablaProd.addCell(cellPrecio);
                
                PdfPCell cellSub = getCellNoBorder(df.format(dt.getSubtotal()), fontPeque);
                cellSub.setHorizontalAlignment(Element.ALIGN_RIGHT);
                tablaProd.addCell(cellSub);
            }
        }

        document.add(tablaProd);

        document.add(new Paragraph("---------------------------------------------", fontNormal));

        // --- TOTALES ---
        PdfPTable tablaTotales = new PdfPTable(2);
        tablaTotales.setWidthPercentage(100);
        
        Double total = (venta.getMontoTotal() != null) ? venta.getMontoTotal() : 0.0;
        Double desc = (venta.getDescuento() != null) ? venta.getDescuento() : 0.0;
        Double subtotal = total + desc;
        Double pago = (venta.getMontoPago() != null) ? venta.getMontoPago() : 0.0;
        Double vuelto = (venta.getVuelto() != null) ? venta.getVuelto() : 0.0;

        tablaTotales.addCell(getCellNoBorder("Subtotal:", fontNormal));
        tablaTotales.addCell(getCellRight("S/ " + df.format(subtotal), fontNormal));
        
        if (desc > 0) {
            tablaTotales.addCell(getCellNoBorder("Descuento:", fontNormal));
            tablaTotales.addCell(getCellRight("- S/ " + df.format(desc), fontNormal));
        }
        
        // Total más grande
        tablaTotales.addCell(getCellNoBorder("TOTAL:", fontTitulo));
        tablaTotales.addCell(getCellRight("S/ " + df.format(total), fontTitulo));
        
        if("Efectivo".equals(venta.getMetodoPago())) {
            tablaTotales.addCell(getCellNoBorder("Efectivo:", fontNormal));
            tablaTotales.addCell(getCellRight(df.format(pago), fontNormal));
            
            tablaTotales.addCell(getCellNoBorder("Cambio:", fontNormal));
            tablaTotales.addCell(getCellRight(df.format(vuelto), fontNormal));
        } else {
            tablaTotales.addCell(getCellNoBorder("Pago:", fontNormal));
            tablaTotales.addCell(getCellRight(venta.getMetodoPago(), fontNormal));
        }

        document.add(tablaTotales);

        // Pie de página simple
        document.add(new Paragraph("\n"));
        Paragraph footer = new Paragraph("¡Gracias por su compra!\nNO SE ACEPTAN DEVOLUCIONES", fontPeque);
        footer.setAlignment(Paragraph.ALIGN_CENTER);
        document.add(footer);

        document.close();
    }
    
    // --- Helpers Adaptados ---
    private PdfPCell getHeaderCell(String texto, Font font) {
        PdfPCell cell = new PdfPCell(new Phrase(texto, font));
        cell.setBorder(Rectangle.BOTTOM); // Solo línea abajo
        cell.setHorizontalAlignment(Element.ALIGN_CENTER);
        cell.setPaddingBottom(2);
        return cell;
    }

    private PdfPCell getCellNoBorder(String texto, Font font) {
        PdfPCell cell = new PdfPCell(new Phrase(texto, font));
        cell.setBorder(Rectangle.NO_BORDER);
        cell.setPadding(2);
        return cell;
    }
    
    private PdfPCell getCellRight(String texto, Font font) {
        PdfPCell cell = new PdfPCell(new Phrase(texto, font));
        cell.setBorder(Rectangle.NO_BORDER);
        cell.setHorizontalAlignment(Element.ALIGN_RIGHT);
        cell.setPadding(2);
        return cell;
    }
}