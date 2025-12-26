package com.giovi.demo.util;

import com.giovi.demo.entity.Producto;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;

public class StockExcelExporter {
    private List<Producto> listaProductos;

    public StockExcelExporter(List<Producto> listaProductos) {
        this.listaProductos = listaProductos;
    }

    private void writeHeaderLine(Sheet sheet) {
        Row row = sheet.createRow(0);
        CellStyle style = sheet.getWorkbook().createCellStyle();
        Font font = sheet.getWorkbook().createFont();
        font.setBold(true);
        font.setFontHeightInPoints((short) 14);
        style.setFont(font);

        createCell(row, 0, "Producto", style);
        createCell(row, 1, "Categoría", style);
        createCell(row, 2, "Stock Actual", style);
        createCell(row, 3, "Tienda/Sucursal", style);
    }

    private void createCell(Row row, int columnCount, Object value, CellStyle style) {
        sheet.autoSizeColumn(columnCount);
        Cell cell = row.createCell(columnCount);
        if (value instanceof Integer) {
            cell.setCellValue((Integer) value);
        } else if (value instanceof Boolean) {
            cell.setCellValue((Boolean) value);
        } else {
            cell.setCellValue((String) value);
        }
        cell.setCellStyle(style);
    }

    private void writeDataLines(Sheet sheet) {
        int rowCount = 1;
        CellStyle style = sheet.getWorkbook().createCellStyle();
        Font font = sheet.getWorkbook().createFont();
        font.setFontHeightInPoints((short) 12);
        style.setFont(font);

        for (Producto prod : listaProductos) {
            Row row = sheet.createRow(rowCount++);
            int columnCount = 0;

            createCell(row, columnCount++, prod.getNombre(), style);
            createCell(row, columnCount++, prod.getCategoria().getNombre(), style);
            createCell(row, columnCount++, prod.getStock(), style);
            // Validamos tienda por si es nula
            String tienda = (prod.getTienda() != null) ? prod.getTienda().getNombre() : "Sin Tienda";
            createCell(row, columnCount++, tienda, style);
        }
    }

    public void export(HttpServletResponse response) throws IOException {
        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("Stock Bajo");
            writeHeaderLine(sheet);
            writeDataLines(sheet);
            ServletOutputStream outputStream = response.getOutputStream();
            workbook.write(outputStream);
        }
    }
    
    private Sheet sheet; // Variable auxiliar para createCell (o pasala como parametro)
    // Nota: Para simplificar, en createCell usa 'row.getSheet()'
}