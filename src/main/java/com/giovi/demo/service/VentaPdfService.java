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
        Document document = new Document(PageSize.A4);
        PdfWriter.getInstance(document, response.getOutputStream());

        document.open();

        // Estilos
        Font fontTitulo = FontFactory.getFont(FontFactory.HELVETICA_BOLD);
        fontTitulo.setSize(18);
        fontTitulo.setColor(Color.BLUE);

        Font fontNegrita = FontFactory.getFont(FontFactory.HELVETICA_BOLD);
        fontNegrita.setSize(12);

        Font fontNormal = FontFactory.getFont(FontFactory.HELVETICA);
        fontNormal.setSize(12);
        
        DecimalFormat df = new DecimalFormat("0.00");
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");

        // --- 1. DATOS TIENDA ---
        Paragraph titulo = new Paragraph("TICKET DE VENTA", fontTitulo);
        titulo.setAlignment(Paragraph.ALIGN_CENTER);
        document.add(titulo);

        // Validación de Tienda (Evita NullPointer)
        String nombreTienda = (venta.getTienda() != null) ? venta.getTienda().getNombre() : "Tienda Principal";
        String dirTienda = (venta.getTienda() != null && venta.getTienda().getDireccion() != null) 
                           ? venta.getTienda().getDireccion() : "Sin dirección registrada";
        
        Paragraph infoTienda = new Paragraph(nombreTienda + "\n" + dirTienda, fontNormal);
        infoTienda.setAlignment(Paragraph.ALIGN_CENTER);
        infoTienda.setSpacingAfter(10);
        document.add(infoTienda);

        document.add(new Paragraph("-----------------------------------------------------------------------------"));

        // --- 2. DATOS CABECERA ---
        PdfPTable tablaDatos = new PdfPTable(2);
        tablaDatos.setWidthPercentage(100);
        tablaDatos.setSpacingBefore(10);
        tablaDatos.setSpacingAfter(10);

        tablaDatos.addCell(getCellNoBorder("N° Venta: " + (venta.getNumeroVenta() != null ? venta.getNumeroVenta() : "---"), fontNegrita));
        tablaDatos.addCell(getCellNoBorder("Fecha: " + (venta.getFecha() != null ? venta.getFecha().format(formatter) : "--/--/----"), fontNormal));
        
        // Validación Cliente
        String nomCliente = (venta.getCliente() != null) ? venta.getCliente().getNombre() : "Cliente General";
        String dniCliente = (venta.getCliente() != null) ? venta.getCliente().getDni() : "---";
        
        tablaDatos.addCell(getCellNoBorder("Cliente: " + nomCliente, fontNormal));
        tablaDatos.addCell(getCellNoBorder("DNI: " + dniCliente, fontNormal));
        
        // Validación Usuario
        String nomUsuario = (venta.getUsuario() != null) ? venta.getUsuario().getNombre() : "Sistema";
        tablaDatos.addCell(getCellNoBorder("Vendedor: " + nomUsuario, fontNormal));
        tablaDatos.addCell(getCellNoBorder("Pago: " + (venta.getMetodoPago() != null ? venta.getMetodoPago() : "Efectivo"), fontNormal));

        document.add(tablaDatos);

        // --- 3. TABLA PRODUCTOS ---
        PdfPTable tablaProd = new PdfPTable(4);
        tablaProd.setWidthPercentage(100);
        tablaProd.setWidths(new float[] {3.5f, 1f, 1.5f, 1.5f}); 
        tablaProd.setSpacingBefore(10);

        tablaProd.addCell(getHeaderCell("Producto"));
        tablaProd.addCell(getHeaderCell("Cant."));
        tablaProd.addCell(getHeaderCell("Precio"));
        tablaProd.addCell(getHeaderCell("Subtotal"));

        if (venta.getDetalleVenta() != null) {
            for (DetalleVenta dt : venta.getDetalleVenta()) {
                // Nombre producto seguro
                String prodNombre = (dt.getProducto() != null) ? dt.getProducto().getNombre() : "Producto Eliminado";
                
                tablaProd.addCell(new Phrase(prodNombre, fontNormal));
                
                PdfPCell cellCant = new PdfPCell(new Phrase(String.valueOf(dt.getCantidad()), fontNormal));
                cellCant.setHorizontalAlignment(Element.ALIGN_CENTER);
                tablaProd.addCell(cellCant);
                
                PdfPCell cellPrecio = new PdfPCell(new Phrase("S/ " + df.format(dt.getPrecioUnitario()), fontNormal));
                cellPrecio.setHorizontalAlignment(Element.ALIGN_RIGHT);
                tablaProd.addCell(cellPrecio);
                
                PdfPCell cellSub = new PdfPCell(new Phrase("S/ " + df.format(dt.getSubtotal()), fontNormal));
                cellSub.setHorizontalAlignment(Element.ALIGN_RIGHT);
                tablaProd.addCell(cellSub);
            }
        }

        document.add(tablaProd);

        // --- 4. TOTALES ---
        document.add(new Paragraph(" "));
        
        PdfPTable tablaTotales = new PdfPTable(2);
        tablaTotales.setWidthPercentage(40);
        tablaTotales.setHorizontalAlignment(Element.ALIGN_RIGHT);
        
        Double total = (venta.getMontoTotal() != null) ? venta.getMontoTotal() : 0.0;
        Double desc = (venta.getDescuento() != null) ? venta.getDescuento() : 0.0;
        Double subtotal = total + desc;
        Double pago = (venta.getMontoPago() != null) ? venta.getMontoPago() : 0.0;
        Double vuelto = (venta.getVuelto() != null) ? venta.getVuelto() : 0.0;

        tablaTotales.addCell(getCellNoBorder("Subtotal:", fontNormal));
        tablaTotales.addCell(getCellRight("S/ " + df.format(subtotal), fontNormal));
        
        tablaTotales.addCell(getCellNoBorder("Descuento:", fontNormal));
        tablaTotales.addCell(getCellRight("- S/ " + df.format(desc), fontNormal));
        
        tablaTotales.addCell(getCellNoBorder("TOTAL:", fontNegrita));
        tablaTotales.addCell(getCellRight("S/ " + df.format(total), fontNegrita));
        
        if("Efectivo".equals(venta.getMetodoPago())) {
            tablaTotales.addCell(getCellNoBorder("Pagó con:", fontNormal));
            tablaTotales.addCell(getCellRight("S/ " + df.format(pago), fontNormal));
            
            tablaTotales.addCell(getCellNoBorder("Vuelto:", fontNormal));
            tablaTotales.addCell(getCellRight("S/ " + df.format(vuelto), fontNormal));
        }

        document.add(tablaTotales);

        // Pie de página
        document.add(new Paragraph("\n\n"));
        Paragraph footer = new Paragraph("¡Gracias por su compra!", fontNormal);
        footer.setAlignment(Paragraph.ALIGN_CENTER);
        document.add(footer);

        document.close();
    }

    // --- Helpers ---
    private PdfPCell getHeaderCell(String texto) {
        PdfPCell cell = new PdfPCell(new Phrase(texto, FontFactory.getFont(FontFactory.HELVETICA_BOLD, 12, Color.WHITE)));
        cell.setBackgroundColor(Color.DARK_GRAY);
        cell.setHorizontalAlignment(Element.ALIGN_CENTER);
        cell.setPadding(5);
        return cell;
    }

    private PdfPCell getCellNoBorder(String texto, Font font) {
        PdfPCell cell = new PdfPCell(new Phrase(texto, font));
        cell.setBorder(Rectangle.NO_BORDER);
        return cell;
    }
    
    private PdfPCell getCellRight(String texto, Font font) {
        PdfPCell cell = new PdfPCell(new Phrase(texto, font));
        cell.setBorder(Rectangle.NO_BORDER);
        cell.setHorizontalAlignment(Element.ALIGN_RIGHT);
        return cell;
    }
}