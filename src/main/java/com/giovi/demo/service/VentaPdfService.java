package com.giovi.demo.service;

import com.giovi.demo.entity.DetalleVenta;
import com.giovi.demo.entity.Tienda;
import com.giovi.demo.entity.Venta;
import com.lowagie.text.*; // <--- IMPORTACIONES CORRECTAS PARA OPENPDF
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.awt.Color; // <--- CORRECCIÓN: OpenPDF usa java.awt.Color
import java.io.IOException;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
public class VentaPdfService {

    public void exportar(HttpServletResponse response, Venta venta) throws IOException, DocumentException {
        // 1. Configurar la hoja (A4 con márgenes)
        Document document = new Document(PageSize.A4, 30, 30, 30, 30);
        PdfWriter.getInstance(document, response.getOutputStream());

        document.open();

        // Fuentes (OpenPDF usa FontFactory igual, pero del paquete com.lowagie)
        Font fontTitulo = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 16);
        Font fontSubtitulo = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 12);
        Font fontNormal = FontFactory.getFont(FontFactory.HELVETICA, 10);
        Font fontNegrita = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10);

        // --- SECCIÓN 1: LOGO E INFORMACIÓN DE LA TIENDA ---
        
        // 1.1 Logo
        try {
            ClassPathResource imageResource = new ClassPathResource("static/Imagenes/logo.png");
            // Para OpenPDF, cargamos la imagen así:
            Image logo = Image.getInstance(imageResource.getURL());
            logo.scaleToFit(80, 80);
            logo.setAlignment(Element.ALIGN_CENTER);
            document.add(logo);
        } catch (Exception e) {
            System.err.println("No se pudo cargar el logo: " + e.getMessage());
        }

        // 1.2 Datos de la Tienda
        Tienda tienda = venta.getTienda();
        
        Paragraph pTienda = new Paragraph();
        pTienda.setAlignment(Element.ALIGN_CENTER);
        pTienda.setSpacingBefore(5);
        
        if (tienda != null) {
            pTienda.add(new Phrase(tienda.getNombre().toUpperCase() + "\n", fontTitulo));
            pTienda.add(new Phrase("RUC: " + tienda.getRuc() + "\n", fontNormal));
            pTienda.add(new Phrase(tienda.getDireccion() + "\n", fontNormal));
            pTienda.add(new Phrase("Tel/Cel: " + tienda.getCelular() + "\n", fontNormal));
        } else {
            pTienda.add(new Phrase("TIENDA NO ASIGNADA\n", fontTitulo));
        }
        document.add(pTienda);

        document.add(new Paragraph("\n"));

        // --- SECCIÓN 2: TICKET DE VENTA ---
        Paragraph pTicket = new Paragraph();
        pTicket.setAlignment(Element.ALIGN_CENTER);
        pTicket.add(new Phrase("TICKET DE VENTA\n", fontSubtitulo));
        
        String nroVenta = (venta.getNumeroVenta() != null) ? venta.getNumeroVenta() : String.format("%06d", venta.getId());
        pTicket.add(new Phrase("Nro: " + nroVenta + "\n", fontTitulo));
        document.add(pTicket);

        document.add(new Paragraph("----------------------------------------------------------------------------------", fontNormal));

        // --- SECCIÓN 3: INFORMACIÓN DEL CLIENTE ---
        Paragraph pCliente = new Paragraph();
        pCliente.setAlignment(Element.ALIGN_LEFT);
        pCliente.setIndentationLeft(20);
        pCliente.setSpacingAfter(10);

        String nombreCliente = (venta.getCliente() != null) ? venta.getCliente().getNombre() : "Cliente General";
        String dniCliente = (venta.getCliente() != null && venta.getCliente().getDni() != null) ? venta.getCliente().getDni() : "---";
        String vendedor = (venta.getUsuario() != null) ? venta.getUsuario().getNombre() : "Sistema";

        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");
        String fechaFormateada = (venta.getFecha() != null) ? venta.getFecha().format(formatter) : "---";

        pCliente.add(new Phrase("Cliente      : " + nombreCliente + "\n", fontNormal));
        pCliente.add(new Phrase("DNI/RUC    : " + dniCliente + "\n", fontNormal));
        pCliente.add(new Phrase("Fecha        : " + fechaFormateada + "\n", fontNormal));
        pCliente.add(new Phrase("Atendido por : " + vendedor + "\n", fontNormal));
        
        document.add(pCliente);

        // --- SECCIÓN 4: DETALLE DE VENTA (TABLA) ---
        PdfPTable tabla = new PdfPTable(4);
        tabla.setWidthPercentage(100);
        tabla.setWidths(new float[]{1.5f, 5f, 2.5f, 2.5f});
        tabla.setSpacingBefore(10);

        String[] headers = {"Cant", "Descripción", "P. Unit", "Importe"};
        for (String header : headers) {
            PdfPCell cell = new PdfPCell(new Phrase(header, fontNegrita));
            cell.setHorizontalAlignment(Element.ALIGN_CENTER);
            // CORRECCIÓN: Usamos java.awt.Color para OpenPDF
            cell.setBackgroundColor(Color.LIGHT_GRAY); 
            cell.setPadding(5);
            tabla.addCell(cell);
        }

        List<DetalleVenta> detalles = venta.getDetalleVenta();
        if (detalles != null) {
            for (DetalleVenta detalle : detalles) {
                PdfPCell c1 = new PdfPCell(new Phrase(String.valueOf(detalle.getCantidad()), fontNormal));
                c1.setHorizontalAlignment(Element.ALIGN_CENTER);
                tabla.addCell(c1);

                String prodNombre = (detalle.getProducto() != null) ? detalle.getProducto().getNombre() : "Producto Eliminado";
                PdfPCell c2 = new PdfPCell(new Phrase(prodNombre, fontNormal));
                c2.setHorizontalAlignment(Element.ALIGN_LEFT);
                tabla.addCell(c2);

                // CORRECCIÓN: Usamos getPrecioUnitario() en lugar de getPrecio()
                Double precio = (detalle.getPrecioUnitario() != null) ? detalle.getPrecioUnitario() : 0.0;
                PdfPCell c3 = new PdfPCell(new Phrase("S/. " + String.format("%.2f", precio), fontNormal));
                c3.setHorizontalAlignment(Element.ALIGN_RIGHT);
                tabla.addCell(c3);

                Double subtotal = (detalle.getSubtotal() != null) ? detalle.getSubtotal() : 0.0;
                PdfPCell c4 = new PdfPCell(new Phrase("S/. " + String.format("%.2f", subtotal), fontNormal));
                c4.setHorizontalAlignment(Element.ALIGN_RIGHT);
                tabla.addCell(c4);
            }
        }

        document.add(tabla);

        // --- TOTALES ---
        Paragraph pTotal = new Paragraph();
        pTotal.setAlignment(Element.ALIGN_RIGHT);
        pTotal.setSpacingBefore(10);
        
        Double total = (venta.getMontoTotal() != null) ? venta.getMontoTotal() : 0.0;
        pTotal.add(new Phrase("TOTAL A PAGAR: S/. " + String.format("%.2f", total), fontTitulo));
        
        document.add(pTotal);
        
        Paragraph pFooter = new Paragraph("\n¡Gracias por su compra!", fontNormal);
        pFooter.setAlignment(Element.ALIGN_CENTER);
        document.add(pFooter);

        document.close();
    }
}