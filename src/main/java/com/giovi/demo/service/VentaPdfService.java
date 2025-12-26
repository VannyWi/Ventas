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
        // 1. CONFIGURACIÓN TAMAÑO TICKET (80mm aprox = 226 puntos de ancho)
        // La altura (800) es referencial, la impresora térmica cortará donde termine el contenido.
        Rectangle ticketSize = new Rectangle(226, 800); 
        Document document = new Document(ticketSize, 10, 10, 10, 10); // Márgenes pequeños (Izq, Der, Arr, Aba)
        PdfWriter.getInstance(document, response.getOutputStream());

        document.open();

        // 2. FUENTES AJUSTADAS (Tamaños pequeños para ticket)
        Font fontEmpresa = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 11);
        Font fontTitulo = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10);
        Font fontNormal = FontFactory.getFont(FontFactory.HELVETICA, 8);
        Font fontNegrita = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 8);
        Font fontPie = FontFactory.getFont(FontFactory.HELVETICA, 7);

        // --- SECCIÓN 1: LOGO ---
        try {
            ClassPathResource imageResource = new ClassPathResource("static/Imagenes/logo.png");
            Image logo = Image.getInstance(imageResource.getURL());
            logo.scaleToFit(50, 50); // Logo más pequeño para que entre bien
            logo.setAlignment(Element.ALIGN_CENTER);
            document.add(logo);
        } catch (Exception e) {
            // Si falla el logo, no detenemos la impresión
            System.err.println("Error cargando logo: " + e.getMessage());
        }

        // --- SECCIÓN 2: DATOS DE LA TIENDA ---
        Tienda tienda = venta.getTienda();
        Paragraph pTienda = new Paragraph();
        pTienda.setAlignment(Element.ALIGN_CENTER);
        pTienda.setSpacingBefore(5);
        
        if (tienda != null) {
            pTienda.add(new Phrase(tienda.getNombre().toUpperCase() + "\n", fontEmpresa));
            pTienda.add(new Phrase("RUC: " + tienda.getRuc() + "\n", fontNormal));
            pTienda.add(new Phrase(tienda.getDireccion() + "\n", fontNormal));
            pTienda.add(new Phrase("Cel: " + tienda.getCelular() + "\n", fontNormal));
        } else {
            pTienda.add(new Phrase("TIENDA NO ASIGNADA\n", fontEmpresa));
        }
        document.add(pTienda);
        document.add(new Paragraph("---------------------------------------------", fontNormal));

        // --- SECCIÓN 3: INFO DEL TICKET ---
        Paragraph pTicket = new Paragraph();
        pTicket.setAlignment(Element.ALIGN_CENTER);
        
        // Número de venta
        String nroVenta = (venta.getNumeroVenta() != null) ? venta.getNumeroVenta() : String.format("%06d", venta.getId());
        pTicket.add(new Phrase("TICKET DE VENTA: " + nroVenta + "\n", fontTitulo));
        
        // Fecha y Hora formateada
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");
        String fechaHora = (venta.getFecha() != null) ? venta.getFecha().format(formatter) : "---";
        pTicket.add(new Phrase("Fecha: " + fechaHora + "\n", fontNormal));
        
        document.add(pTicket);
        document.add(new Paragraph("---------------------------------------------", fontNormal));

        // --- SECCIÓN 4: DATOS DEL CLIENTE Y CAJERO ---
        Paragraph pCliente = new Paragraph();
        pCliente.setAlignment(Element.ALIGN_LEFT);
        
        String nombreCliente = (venta.getCliente() != null) ? venta.getCliente().getNombre() : "Cliente General";
        String dniCliente = (venta.getCliente() != null && venta.getCliente().getDni() != null) ? venta.getCliente().getDni() : "---";
        String vendedor = (venta.getUsuario() != null) ? venta.getUsuario().getNombre() : "Sistema";

        pCliente.add(new Phrase("Cliente: " + nombreCliente + "\n", fontNormal));
        pCliente.add(new Phrase("DNI: " + dniCliente + "\n", fontNormal)); // Solo mostramos DNI como pediste
        pCliente.add(new Phrase("Cajero: " + vendedor + "\n", fontNormal));
        
        document.add(pCliente);
        document.add(new Paragraph("---------------------------------------------", fontNormal));

        // --- SECCIÓN 5: DETALLE DE PRODUCTOS ---
        PdfPTable tabla = new PdfPTable(4); // Columnas: Cant, Desc, PU, Total
        tabla.setWidthPercentage(100);
        // Anchos relativos: Cantidad pequeña, Descripción grande, Precio y Total medianos
        tabla.setWidths(new float[]{1f, 4.5f, 2f, 2.5f}); 

        // Cabecera de tabla
        tabla.addCell(celdaSinBorde("Cant.", fontNegrita, Element.ALIGN_CENTER));
        tabla.addCell(celdaSinBorde("Descrip.", fontNegrita, Element.ALIGN_LEFT));
        tabla.addCell(celdaSinBorde("P.Unit", fontNegrita, Element.ALIGN_RIGHT));
        tabla.addCell(celdaSinBorde("Total", fontNegrita, Element.ALIGN_RIGHT));

        // Filas de productos
        List<DetalleVenta> detalles = venta.getDetalleVenta();
        if (detalles != null) {
            for (DetalleVenta detalle : detalles) {
                // Cantidad
                tabla.addCell(celdaSinBorde(String.valueOf(detalle.getCantidad()), fontNormal, Element.ALIGN_CENTER));
                
                // Descripción
                String prodNombre = (detalle.getProducto() != null) ? detalle.getProducto().getNombre() : "---";
                tabla.addCell(celdaSinBorde(prodNombre, fontNormal, Element.ALIGN_LEFT));
                
                // Precio Unitario (usamos getPrecioUnitario() de tu entidad)
                Double precio = (detalle.getPrecioUnitario() != null) ? detalle.getPrecioUnitario() : 0.0;
                tabla.addCell(celdaSinBorde(String.format("%.2f", precio), fontNormal, Element.ALIGN_RIGHT));
                
                // Subtotal
                Double subtotal = (detalle.getSubtotal() != null) ? detalle.getSubtotal() : 0.0;
                tabla.addCell(celdaSinBorde(String.format("%.2f", subtotal), fontNormal, Element.ALIGN_RIGHT));
            }
        }
        document.add(tabla);
        document.add(new Paragraph("---------------------------------------------", fontNormal));

        // --- SECCIÓN 6: TOTALES Y PAGO ---
        PdfPTable tablaTotales = new PdfPTable(2);
        tablaTotales.setWidthPercentage(100);
        tablaTotales.setWidths(new float[]{6f, 4f}); // Columna etiqueta vs Columna valor

        // Recuperamos valores seguros (evitando nulos)
        Double total = (venta.getMontoTotal() != null) ? venta.getMontoTotal() : 0.0;
        Double descuento = (venta.getDescuento() != null) ? venta.getDescuento() : 0.0;
        Double pagado = (venta.getMontoPago() != null) ? venta.getMontoPago() : 0.0;
        Double vuelto = (venta.getVuelto() != null) ? venta.getVuelto() : 0.0;
        String metodo = (venta.getMetodoPago() != null) ? venta.getMetodoPago() : "Efectivo";

        // Filas de totales
        agregarFilaTotal(tablaTotales, "Descuento:", "S/. " + String.format("%.2f", descuento), fontNormal);
        agregarFilaTotal(tablaTotales, "TOTAL A PAGAR:", "S/. " + String.format("%.2f", total), fontTitulo);
        
        document.add(tablaTotales);
        document.add(new Paragraph("\n")); // Pequeño espacio

        // Filas de información de pago
        PdfPTable tablaPago = new PdfPTable(2);
        tablaPago.setWidthPercentage(100);
        tablaPago.setWidths(new float[]{6f, 4f});
        
        agregarFilaTotal(tablaPago, "Método de Pago:", metodo, fontNormal);
        agregarFilaTotal(tablaPago, "Pago con:", "S/. " + String.format("%.2f", pagado), fontNormal);
        agregarFilaTotal(tablaPago, "Cambio / Vuelto:", "S/. " + String.format("%.2f", vuelto), fontNormal);
        
        document.add(tablaPago);

        // --- PIE DE PÁGINA ---
        Paragraph pFooter = new Paragraph("\n¡Gracias por su compra!\nConserve este ticket.", fontPie);
        pFooter.setAlignment(Element.ALIGN_CENTER);
        document.add(pFooter);

        document.close();
    }

    // --- MÉTODOS AUXILIARES ---

    // Crea una celda sin bordes para que la tabla se vea limpia en el ticket
    private PdfPCell celdaSinBorde(String texto, Font fuente, int alineacion) {
        PdfPCell cell = new PdfPCell(new Phrase(texto, fuente));
        cell.setBorder(Rectangle.NO_BORDER);
        cell.setHorizontalAlignment(alineacion);
        cell.setPadding(2);
        return cell;
    }

    // Agrega fila de totales (Etiqueta a la derecha, Valor a la derecha)
    private void agregarFilaTotal(PdfPTable tabla, String etiqueta, String valor, Font fuente) {
        PdfPCell c1 = new PdfPCell(new Phrase(etiqueta, fuente));
        c1.setBorder(Rectangle.NO_BORDER);
        c1.setHorizontalAlignment(Element.ALIGN_RIGHT);
        
        PdfPCell c2 = new PdfPCell(new Phrase(valor, fuente));
        c2.setBorder(Rectangle.NO_BORDER);
        c2.setHorizontalAlignment(Element.ALIGN_RIGHT);
        
        tabla.addCell(c1);
        tabla.addCell(c2);
    }
}