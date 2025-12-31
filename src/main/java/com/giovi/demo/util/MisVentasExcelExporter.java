package com.giovi.demo.util;

import com.giovi.demo.entity.Venta;
import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFFont;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import org.apache.poi.xssf.usermodel.XSSFSheet;

public class MisVentasExcelExporter {
    private XSSFWorkbook workbook;
    private XSSFSheet sheet;
    private List<Venta> listaVentas;
    private LocalDate fechaInicio;
    private LocalDate fechaFin;

    // Estilos globales
    private CellStyle styleTitle;
    private CellStyle styleSubTitle;
    private CellStyle styleHeader;
    private CellStyle styleData;
    private CellStyle styleCenter;
    private CellStyle styleCurrency;
    private CellStyle styleTotalLabel;
    private CellStyle styleTotalValue;

    public MisVentasExcelExporter(List<Venta> listaVentas, LocalDate fechaInicio, LocalDate fechaFin) {
        this.listaVentas = listaVentas;
        this.fechaInicio = fechaInicio;
        this.fechaFin = fechaFin;
        workbook = new XSSFWorkbook();
    }

    // Inicialización de estilos corregida para evitar corrupción
    private void initStyles() {
        // Objeto formato auxiliar
        DataFormat format = workbook.createDataFormat();

        // 1. Título
        styleTitle = workbook.createCellStyle();
        XSSFFont fontTitle = workbook.createFont();
        fontTitle.setBold(true);
        fontTitle.setFontHeight(16);
        fontTitle.setColor(IndexedColors.DARK_BLUE.getIndex());
        styleTitle.setFont(fontTitle);
        styleTitle.setAlignment(HorizontalAlignment.CENTER);

        // 2. Subtítulo (Fechas)
        styleSubTitle = workbook.createCellStyle();
        XSSFFont fontSub = workbook.createFont();
        fontSub.setFontHeight(11);
        fontSub.setItalic(true);
        styleSubTitle.setFont(fontSub);
        styleSubTitle.setAlignment(HorizontalAlignment.CENTER);

        // 3. Encabezados
        styleHeader = workbook.createCellStyle();
        XSSFFont fontHeader = workbook.createFont();
        fontHeader.setBold(true);
        fontHeader.setFontHeight(12);
        fontHeader.setColor(IndexedColors.WHITE.getIndex());
        styleHeader.setFont(fontHeader);
        styleHeader.setFillForegroundColor(IndexedColors.ROYAL_BLUE.getIndex());
        styleHeader.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        styleHeader.setAlignment(HorizontalAlignment.CENTER);
        styleHeader.setVerticalAlignment(VerticalAlignment.CENTER);
        styleHeader.setBorderBottom(BorderStyle.THIN);
        styleHeader.setBorderTop(BorderStyle.THIN);
        styleHeader.setBorderRight(BorderStyle.THIN);
        styleHeader.setBorderLeft(BorderStyle.THIN);

        // 4. Datos Normales
        styleData = workbook.createCellStyle();
        XSSFFont fontData = workbook.createFont();
        fontData.setFontHeight(11);
        styleData.setFont(fontData);
        styleData.setBorderBottom(BorderStyle.THIN);
        styleData.setBorderRight(BorderStyle.THIN);
        styleData.setBorderLeft(BorderStyle.THIN);

        // 5. Datos Centrados
        styleCenter = workbook.createCellStyle();
        styleCenter.cloneStyleFrom(styleData);
        styleCenter.setAlignment(HorizontalAlignment.CENTER);

        // 6. Moneda (SOLUCIÓN AL ERROR DE ESTILOS)
        // Usamos formato numérico estándar. El "S/." ya está en el título de la columna.
        styleCurrency = workbook.createCellStyle();
        styleCurrency.cloneStyleFrom(styleData);
        styleCurrency.setDataFormat(format.getFormat("#,##0.00")); 
        styleCurrency.setAlignment(HorizontalAlignment.RIGHT);

        // 7. Fila Total - Etiqueta
        styleTotalLabel = workbook.createCellStyle();
        XSSFFont fontTotal = workbook.createFont();
        fontTotal.setBold(true);
        fontTotal.setFontHeight(12);
        styleTotalLabel.setFont(fontTotal);
        styleTotalLabel.setFillForegroundColor(IndexedColors.LEMON_CHIFFON.getIndex());
        styleTotalLabel.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        styleTotalLabel.setBorderTop(BorderStyle.MEDIUM);
        styleTotalLabel.setAlignment(HorizontalAlignment.RIGHT);

        // 8. Fila Total - Valor
        styleTotalValue = workbook.createCellStyle();
        styleTotalValue.cloneStyleFrom(styleTotalLabel);
        styleTotalValue.setDataFormat(format.getFormat("#,##0.00"));
    }

    private void createCell(Row row, int columnCount, Object value, CellStyle style) {
        Cell cell = row.createCell(columnCount);
        if (value instanceof Long) {
            cell.setCellValue((Long) value);
        } else if (value instanceof Double) {
            cell.setCellValue((Double) value);
        } else if (value instanceof Boolean) {
            cell.setCellValue((Boolean) value);
        } else {
            cell.setCellValue((String) value);
        }
        cell.setCellStyle(style);
    }

    private void writeHeaderLine() {
        sheet = workbook.createSheet("Mis Ventas");

        // Fila 0: Título Principal
        Row rowTitle = sheet.createRow(0);
        createCell(rowTitle, 0, "REPORTE DE MIS VENTAS", styleTitle);
        sheet.addMergedRegion(new CellRangeAddress(0, 0, 0, 5));

        // Fila 1: Rango de Fechas
        Row rowDate = sheet.createRow(1);
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("dd/MM/yyyy");
        String rango = "Rango de Filtrado: " + fechaInicio.format(fmt) + " al " + fechaFin.format(fmt);
        createCell(rowDate, 0, rango, styleSubTitle);
        sheet.addMergedRegion(new CellRangeAddress(1, 1, 0, 5));

        // Fila 2: Cabeceras de Tabla
        Row row = sheet.createRow(2);
        createCell(row, 0, "Nro. Ticket", styleHeader);
        createCell(row, 1, "Fecha y Hora", styleHeader);
        createCell(row, 2, "Tienda", styleHeader);
        createCell(row, 3, "Cliente", styleHeader);
        createCell(row, 4, "Método Pago", styleHeader);
        createCell(row, 5, "Total (S/.)", styleHeader); // Indicamos la moneda aquí
    }

    private void writeDataLines() {
        int rowCount = 3;
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
        double granTotal = 0.0;

        for (Venta venta : listaVentas) {
            Row row = sheet.createRow(rowCount++);
            int col = 0;

            // 1. Ticket
            String ticket = (venta.getNumeroVenta() != null) ? venta.getNumeroVenta() : "ID-" + venta.getId();
            createCell(row, col++, ticket, styleCenter);

            // 2. Fecha
            String fecha = (venta.getFecha() != null) ? venta.getFecha().format(formatter) : "";
            createCell(row, col++, fecha, styleCenter);

            // 3. Tienda
            String tienda = (venta.getTienda() != null) ? venta.getTienda().getNombre() : "Central";
            createCell(row, col++, tienda, styleData);

            // 4. Cliente
            String cliente = (venta.getCliente() != null) ? venta.getCliente().getNombre() : "Público General";
            createCell(row, col++, cliente, styleData);

            // 5. Método
            String metodo = (venta.getMetodoPago() != null) ? venta.getMetodoPago() : "-";
            createCell(row, col++, metodo, styleCenter);

            // 6. Total
            Double total = (venta.getMontoTotal() != null) ? venta.getMontoTotal() : 0.0;
            createCell(row, col++, total, styleCurrency);

            granTotal += total;
        }

        // Fila de Total
        Row rowTotal = sheet.createRow(rowCount);
        
        // Etiqueta
        Cell cellLabel = rowTotal.createCell(4);
        cellLabel.setCellValue("TOTAL GENERAL:");
        cellLabel.setCellStyle(styleTotalLabel);
        
        // Valor
        Cell cellValue = rowTotal.createCell(5);
        cellValue.setCellValue(granTotal);
        cellValue.setCellStyle(styleTotalValue);

        // Autoajustar columnas
        for (int i = 0; i <= 5; i++) {
            sheet.autoSizeColumn(i);
        }
        
        // Activar Filtros (solo si hay datos)
        if(rowCount > 3) {
            sheet.setAutoFilter(new CellRangeAddress(2, rowCount - 1, 0, 5));
        }
    }

    public void export(HttpServletResponse response) throws IOException {
        initStyles(); // Crear estilos
        writeHeaderLine(); // Crear cabecera
        writeDataLines(); // Crear datos

        ServletOutputStream outputStream = response.getOutputStream();
        workbook.write(outputStream);
        workbook.close();
        outputStream.close();
    }
    // AGREGAR ESTO EN: MisVentasExcelExporter.java Y AdminVentasExcelExporter.java
public void generate(java.io.OutputStream outputStream) throws java.io.IOException {
    initStyles();
    writeHeaderLine();
    writeDataLines();
    workbook.write(outputStream);
    workbook.close();
}
}