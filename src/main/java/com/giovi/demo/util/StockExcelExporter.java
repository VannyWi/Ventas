package com.giovi.demo.util;

import com.giovi.demo.entity.Producto;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress; // Necesario para los filtros
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;

public class StockExcelExporter {
    private List<Producto> listaProductos;
    private XSSFWorkbook workbook;
    private Sheet sheet;

    public StockExcelExporter(List<Producto> listaProductos) {
        this.listaProductos = listaProductos;
        workbook = new XSSFWorkbook(); // Inicializamos el workbook aquí
    }

    private void writeHeaderLine(CellStyle style) {
        Row row = sheet.createRow(0);

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
        } else if (value instanceof Double) {
            cell.setCellValue((Double) value);
        } else {
            cell.setCellValue((String) value);
        }
        cell.setCellStyle(style);
    }

    private void writeDataLines(CellStyle style) {
        int rowCount = 1;

        for (Producto prod : listaProductos) {
            Row row = sheet.createRow(rowCount++);
            int columnCount = 0;

            createCell(row, columnCount++, prod.getNombre(), style);
            
            // Validación de nulos
            String cat = (prod.getCategoria() != null) ? prod.getCategoria().getNombre() : "-";
            createCell(row, columnCount++, cat, style);
            
            createCell(row, columnCount++, prod.getStock(), style);
            
            String tienda = (prod.getTienda() != null) ? prod.getTienda().getNombre() : "Sin Tienda";
            createCell(row, columnCount++, tienda, style);
        }
    }

    public void export(HttpServletResponse response) throws IOException {
        sheet = workbook.createSheet("Stock Bajo");

        // 1. Crear Estilo para la Cabecera (Fondo azul, letra blanca, negrita, bordes)
        CellStyle headerStyle = workbook.createCellStyle();
        Font headerFont = workbook.createFont();
        headerFont.setBold(true);
        headerFont.setFontHeightInPoints((short) 14);
        headerFont.setColor(IndexedColors.WHITE.getIndex()); // Letra Blanca
        headerStyle.setFont(headerFont);
        
        headerStyle.setFillForegroundColor(IndexedColors.DARK_TEAL.getIndex()); // Fondo Azul Oscuro/Verde azulado
        headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        headerStyle.setBorderBottom(BorderStyle.THIN);
        headerStyle.setBorderTop(BorderStyle.THIN);
        headerStyle.setBorderRight(BorderStyle.THIN);
        headerStyle.setBorderLeft(BorderStyle.THIN);

        // 2. Crear Estilo para los Datos (Bordes negros simples)
        CellStyle dataStyle = workbook.createCellStyle();
        Font dataFont = workbook.createFont();
        dataFont.setFontHeightInPoints((short) 12);
        dataStyle.setFont(dataFont);
        dataStyle.setBorderBottom(BorderStyle.THIN);
        dataStyle.setBorderTop(BorderStyle.THIN);
        dataStyle.setBorderRight(BorderStyle.THIN);
        dataStyle.setBorderLeft(BorderStyle.THIN);

        // 3. Escribir datos
        writeHeaderLine(headerStyle);
        writeDataLines(dataStyle);

        // 4. Agregar Filtros Automáticos
        // Los parámetros son: (PrimeraFila, ÚltimaFila, PrimeraColumna, ÚltimaColumna)
        // Usamos sheet.getLastRowNum() para que el filtro cubra hasta la última fila de datos
        if (listaProductos.size() > 0) {
            sheet.setAutoFilter(new CellRangeAddress(0, 0, 0, 3));
        }

        ServletOutputStream outputStream = response.getOutputStream();
        workbook.write(outputStream);
        workbook.close();
        outputStream.close();
    }
}