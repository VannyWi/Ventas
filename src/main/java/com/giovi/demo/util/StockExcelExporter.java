package com.giovi.demo.util;

import com.giovi.demo.entity.Producto;
import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFFont;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import org.apache.poi.xssf.usermodel.XSSFSheet;

public class StockExcelExporter {
    private XSSFWorkbook workbook;
    private XSSFSheet sheet;
    private List<Producto> listaProductos;
    private String nombreTienda; // Nuevo campo

    // Estilos globales
    private CellStyle styleTitle;
    private CellStyle styleDate;
    private CellStyle styleHeader;
    private CellStyle styleData;
    private CellStyle styleCenter;
    private CellStyle styleCurrency;
    private CellStyle styleStockCritical;

    // Constructor actualizado para recibir el nombre de la tienda
    public StockExcelExporter(List<Producto> listaProductos, String nombreTienda) {
        this.listaProductos = listaProductos;
        this.nombreTienda = nombreTienda;
        workbook = new XSSFWorkbook();
    }

    private void initStyles() {
        DataFormat format = workbook.createDataFormat();

        // 1. Título
        styleTitle = workbook.createCellStyle();
        XSSFFont fontTitle = workbook.createFont();
        fontTitle.setBold(true);
        fontTitle.setFontHeight(16);
        fontTitle.setColor(IndexedColors.DARK_RED.getIndex());
        styleTitle.setFont(fontTitle);
        styleTitle.setAlignment(HorizontalAlignment.CENTER);

        // 2. Subtítulo (Fecha/Tienda)
        styleDate = workbook.createCellStyle();
        styleDate.setAlignment(HorizontalAlignment.CENTER);
        XSSFFont fontDate = workbook.createFont();
        fontDate.setItalic(true);
        fontDate.setFontHeight(11);
        styleDate.setFont(fontDate);

        // 3. Encabezados Tabla
        styleHeader = workbook.createCellStyle();
        XSSFFont fontHeader = workbook.createFont();
        fontHeader.setBold(true);
        fontHeader.setFontHeight(11);
        fontHeader.setColor(IndexedColors.WHITE.getIndex());
        styleHeader.setFont(fontHeader);
        styleHeader.setFillForegroundColor(IndexedColors.RED.getIndex());
        styleHeader.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        styleHeader.setAlignment(HorizontalAlignment.CENTER);
        styleHeader.setVerticalAlignment(VerticalAlignment.CENTER);
        styleHeader.setBorderBottom(BorderStyle.THIN);
        styleHeader.setBorderTop(BorderStyle.THIN);
        styleHeader.setBorderLeft(BorderStyle.THIN);
        styleHeader.setBorderRight(BorderStyle.THIN);

        // 4. Datos Normales
        styleData = workbook.createCellStyle();
        styleData.setBorderBottom(BorderStyle.THIN);
        styleData.setBorderLeft(BorderStyle.THIN);
        styleData.setBorderRight(BorderStyle.THIN);

        // 5. Datos Centrados
        styleCenter = workbook.createCellStyle();
        styleCenter.cloneStyleFrom(styleData);
        styleCenter.setAlignment(HorizontalAlignment.CENTER);

        // 6. Moneda
        styleCurrency = workbook.createCellStyle();
        styleCurrency.cloneStyleFrom(styleData);
        styleCurrency.setDataFormat(format.getFormat("#,##0.00")); 
        styleCurrency.setAlignment(HorizontalAlignment.RIGHT);

        // 7. Stock Crítico
        styleStockCritical = workbook.createCellStyle();
        styleStockCritical.cloneStyleFrom(styleCenter);
        XSSFFont fontCritical = workbook.createFont();
        fontCritical.setBold(true);
        fontCritical.setColor(IndexedColors.RED.getIndex());
        styleStockCritical.setFont(fontCritical);
    }

    private void createCell(Row row, int columnCount, Object value, CellStyle style) {
        Cell cell = row.createCell(columnCount);
        if (value instanceof Long) {
            cell.setCellValue((Long) value);
        } else if (value instanceof Integer) {
            cell.setCellValue((Integer) value);
        } else if (value instanceof Double) {
            cell.setCellValue((Double) value);
        } else {
            cell.setCellValue((String) value);
        }
        cell.setCellStyle(style);
    }

    private void writeHeaderLine() {
        sheet = workbook.createSheet("Stock Critico");

        // Fila 0: Título Principal
        Row rowTitle = sheet.createRow(0);
        createCell(rowTitle, 0, "REPORTE DE STOCK CRÍTICO", styleTitle);
        sheet.addMergedRegion(new CellRangeAddress(0, 0, 0, 5));

        // Fila 1: Nombre de la Tienda (NUEVO)
        Row rowTienda = sheet.createRow(1);
        createCell(rowTienda, 0, "Tienda Consultada: " + nombreTienda, styleDate);
        sheet.addMergedRegion(new CellRangeAddress(1, 1, 0, 5));

        // Fila 2: Fecha de Generación
        Row rowDate = sheet.createRow(2);
        String fecha = "Generado el: " + LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm"));
        createCell(rowDate, 0, fecha, styleDate);
        sheet.addMergedRegion(new CellRangeAddress(2, 2, 0, 5));

        // Fila 3: Encabezados de Tabla
        Row row = sheet.createRow(3);
        createCell(row, 0, "Código", styleHeader);
        createCell(row, 1, "Producto", styleHeader);
        createCell(row, 2, "Tienda", styleHeader);
        createCell(row, 3, "Categoría", styleHeader);
        createCell(row, 4, "Costo/Precio", styleHeader);
        createCell(row, 5, "Stock Actual", styleHeader);
    }

    private void writeDataLines() {
        int rowCount = 4; // Datos empiezan en la fila 4

        for (Producto p : listaProductos) {
            Row row = sheet.createRow(rowCount++);
            int col = 0;

            createCell(row, col++, p.getCodigoBarras(), styleCenter);
            createCell(row, col++, p.getNombre(), styleData);
            
            // Columna Tienda en la tabla
            String t = (p.getTienda() != null) ? p.getTienda().getNombre() : "Central";
            createCell(row, col++, t, styleData);
            
            String cat = (p.getCategoria() != null) ? p.getCategoria().getNombre() : "-";
            createCell(row, col++, cat, styleData);
            
            Double precio = (p.getPrecio() != null) ? p.getPrecio() : 0.0;
            createCell(row, col++, precio, styleCurrency);
            
            createCell(row, col++, p.getStock(), styleStockCritical);
        }

        for(int i=0; i<=5; i++) {
            sheet.autoSizeColumn(i);
        }
        
        if(rowCount > 4) {
            sheet.setAutoFilter(new CellRangeAddress(3, rowCount - 1, 0, 5));
        }
    }

    public void export(HttpServletResponse response) throws IOException {
        initStyles();
        writeHeaderLine();
        writeDataLines();

        ServletOutputStream outputStream = response.getOutputStream();
        workbook.write(outputStream);
        workbook.close();
        outputStream.close();
    }
}