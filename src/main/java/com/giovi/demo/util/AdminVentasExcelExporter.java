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

public class AdminVentasExcelExporter {
    private XSSFWorkbook workbook;
    private XSSFSheet sheet;
    private List<Venta> listaVentas;
    private LocalDate fechaInicio;
    private LocalDate fechaFin;

    // Estilos
    private CellStyle styleHeader;
    private CellStyle styleData;
    private CellStyle styleCenter;
    private CellStyle styleCurrency;
    private CellStyle styleTotalLabel;
    private CellStyle styleTotalValue;
    private CellStyle styleTitle;
    private CellStyle styleSubTitle;

    public AdminVentasExcelExporter(List<Venta> listaVentas, LocalDate fechaInicio, LocalDate fechaFin) {
        this.listaVentas = listaVentas;
        this.fechaInicio = fechaInicio;
        this.fechaFin = fechaFin;
        workbook = new XSSFWorkbook();
    }

    private void initStyles() {
        DataFormat format = workbook.createDataFormat();

        // Título
        styleTitle = workbook.createCellStyle();
        XSSFFont fontTitle = workbook.createFont();
        fontTitle.setBold(true);
        fontTitle.setFontHeight(16);
        fontTitle.setColor(IndexedColors.DARK_BLUE.getIndex());
        styleTitle.setFont(fontTitle);
        styleTitle.setAlignment(HorizontalAlignment.CENTER);

        // Subtítulo
        styleSubTitle = workbook.createCellStyle();
        styleSubTitle.setAlignment(HorizontalAlignment.CENTER);
        XSSFFont fontSub = workbook.createFont();
        fontSub.setItalic(true);
        styleSubTitle.setFont(fontSub);

        // Header Tabla
        styleHeader = workbook.createCellStyle();
        XSSFFont fontHeader = workbook.createFont();
        fontHeader.setBold(true);
        fontHeader.setColor(IndexedColors.WHITE.getIndex());
        fontHeader.setFontHeight(11);
        styleHeader.setFont(fontHeader);
        styleHeader.setFillForegroundColor(IndexedColors.ROYAL_BLUE.getIndex());
        styleHeader.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        styleHeader.setAlignment(HorizontalAlignment.CENTER);
        styleHeader.setVerticalAlignment(VerticalAlignment.CENTER);
        styleHeader.setBorderBottom(BorderStyle.THIN);
        styleHeader.setBorderTop(BorderStyle.THIN);
        styleHeader.setBorderLeft(BorderStyle.THIN);
        styleHeader.setBorderRight(BorderStyle.THIN);

        // Datos
        styleData = workbook.createCellStyle();
        styleData.setBorderBottom(BorderStyle.THIN);
        styleData.setBorderLeft(BorderStyle.THIN);
        styleData.setBorderRight(BorderStyle.THIN);

        styleCenter = workbook.createCellStyle();
        styleCenter.cloneStyleFrom(styleData);
        styleCenter.setAlignment(HorizontalAlignment.CENTER);

        // Moneda
        styleCurrency = workbook.createCellStyle();
        styleCurrency.cloneStyleFrom(styleData);
        styleCurrency.setDataFormat(format.getFormat("#,##0.00")); 
        styleCurrency.setAlignment(HorizontalAlignment.RIGHT);

        // Totales
        styleTotalLabel = workbook.createCellStyle();
        XSSFFont fontTotal = workbook.createFont();
        fontTotal.setBold(true);
        styleTotalLabel.setFont(fontTotal);
        styleTotalLabel.setFillForegroundColor(IndexedColors.LEMON_CHIFFON.getIndex());
        styleTotalLabel.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        styleTotalLabel.setBorderTop(BorderStyle.MEDIUM);
        styleTotalLabel.setAlignment(HorizontalAlignment.RIGHT);

        styleTotalValue = workbook.createCellStyle();
        styleTotalValue.cloneStyleFrom(styleTotalLabel);
        styleTotalValue.setDataFormat(format.getFormat("#,##0.00"));
    }

    private void createCell(Row row, int columnCount, Object value, CellStyle style) {
        Cell cell = row.createCell(columnCount);
        if (value instanceof Long) cell.setCellValue((Long) value);
        else if (value instanceof Double) cell.setCellValue((Double) value);
        else cell.setCellValue((String) value);
        cell.setCellStyle(style);
    }

    private void writeHeaderLine() {
        sheet = workbook.createSheet("Historial General");

        // Fila 0: Título
        Row rowTitle = sheet.createRow(0);
        createCell(rowTitle, 0, "HISTORIAL DE VENTAS (ADMIN)", styleTitle);
        sheet.addMergedRegion(new CellRangeAddress(0, 0, 0, 6)); // Abarca 7 columnas

        // Fila 1: Fechas
        Row rowDate = sheet.createRow(1);
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("dd/MM/yyyy");
        String rango = "Del: " + fechaInicio.format(fmt) + " al " + fechaFin.format(fmt);
        createCell(rowDate, 0, rango, styleSubTitle);
        sheet.addMergedRegion(new CellRangeAddress(1, 1, 0, 6));

        // Fila 2: Cabeceras
        Row row = sheet.createRow(2);
        createCell(row, 0, "Nro. Ticket", styleHeader);
        createCell(row, 1, "Fecha", styleHeader);
        createCell(row, 2, "Tienda", styleHeader);
        createCell(row, 3, "Vendedor", styleHeader); // COLUMNA NUEVA
        createCell(row, 4, "Cliente", styleHeader);
        createCell(row, 5, "Método", styleHeader);
        createCell(row, 6, "Total (S/.)", styleHeader);
    }

    private void writeDataLines() {
        int rowCount = 3;
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
        double granTotal = 0.0;

        for (Venta venta : listaVentas) {
            Row row = sheet.createRow(rowCount++);
            int col = 0;

            String ticket = (venta.getNumeroVenta() != null) ? venta.getNumeroVenta() : "ID-" + venta.getId();
            createCell(row, col++, ticket, styleCenter);

            String fecha = (venta.getFecha() != null) ? venta.getFecha().format(formatter) : "";
            createCell(row, col++, fecha, styleCenter);

            String tienda = (venta.getTienda() != null) ? venta.getTienda().getNombre() : "Central";
            createCell(row, col++, tienda, styleData);

            // VENDEDOR
            String vendedor = (venta.getUsuario() != null) ? venta.getUsuario().getNombre() + " " + venta.getUsuario().getApellido() : "-";
            createCell(row, col++, vendedor, styleData);

            String cliente = (venta.getCliente() != null) ? venta.getCliente().getNombre() : "Público General";
            createCell(row, col++, cliente, styleData);

            String metodo = (venta.getMetodoPago() != null) ? venta.getMetodoPago() : "-";
            createCell(row, col++, metodo, styleCenter);

            Double total = (venta.getMontoTotal() != null) ? venta.getMontoTotal() : 0.0;
            createCell(row, col++, total, styleCurrency);

            granTotal += total;
        }

        // Fila Total
        Row rowTotal = sheet.createRow(rowCount);
        Cell cellLabel = rowTotal.createCell(5);
        cellLabel.setCellValue("TOTAL:");
        cellLabel.setCellStyle(styleTotalLabel);
        
        Cell cellValue = rowTotal.createCell(6);
        cellValue.setCellValue(granTotal);
        cellValue.setCellStyle(styleTotalValue);

        // Autoajuste
        for(int i=0; i<=6; i++) sheet.autoSizeColumn(i);
        
        if(rowCount > 3) sheet.setAutoFilter(new CellRangeAddress(2, rowCount - 1, 0, 6));
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