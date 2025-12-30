package com.giovi.demo.util;

import com.giovi.demo.entity.Venta;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.format.DateTimeFormatter;
import java.util.List;

public class VentasExcelExporter {
    private List<Venta> listaVentas;

    public VentasExcelExporter(List<Venta> listaVentas) {
        this.listaVentas = listaVentas;
    }

    private void writeHeaderLine(Sheet sheet) {
        Row row = sheet.createRow(0);
        CellStyle style = sheet.getWorkbook().createCellStyle();
        Font font = sheet.getWorkbook().createFont();
        font.setBold(true);
        font.setFontHeightInPoints((short) 12);
        style.setFont(font);

        String[] headers = {"Ticket", "Fecha", "Cliente", "Método Pago", "Total (S/.)"};
        for (int i = 0; i < headers.length; i++) {
            Cell cell = row.createCell(i);
            cell.setCellValue(headers[i]);
            cell.setCellStyle(style);
            sheet.autoSizeColumn(i);
        }
    }

    private void writeDataLines(Sheet sheet) {
        int rowCount = 1;
        CellStyle style = sheet.getWorkbook().createCellStyle();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

        for (Venta venta : listaVentas) {
            Row row = sheet.createRow(rowCount++);
            
            // Ticket
            String ticket = venta.getNumeroVenta() != null ? venta.getNumeroVenta() : String.valueOf(venta.getId());
            row.createCell(0).setCellValue(ticket);
            
            // Fecha
            String fecha = venta.getFecha() != null ? venta.getFecha().format(formatter) : "-";
            row.createCell(1).setCellValue(fecha);
            
            // Cliente
            String cliente = (venta.getCliente() != null) ? venta.getCliente().getNombre() : "Público General";
            row.createCell(2).setCellValue(cliente);
            
            // Método Pago
            row.createCell(3).setCellValue(venta.getMetodoPago());
            
            // Total
            row.createCell(4).setCellValue(venta.getMontoTotal());
        }
    }

    public void export(HttpServletResponse response) throws IOException {
        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("Mis Ventas");
            writeHeaderLine(sheet);
            writeDataLines(sheet);
            ServletOutputStream outputStream = response.getOutputStream();
            workbook.write(outputStream);
        }
    }
}