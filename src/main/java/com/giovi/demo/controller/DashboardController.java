package com.giovi.demo.controller;

import com.giovi.demo.entity.Producto;
import com.giovi.demo.repository.DetalleVentaRepository;
import com.giovi.demo.repository.ProductoRepository;
import com.giovi.demo.repository.VentaRepository;
import com.giovi.demo.util.StockExcelExporter; // Importar tu exportador
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.*;
import java.util.stream.Collectors;

@Controller
public class DashboardController {

    @Autowired private VentaRepository ventaRepo;
    @Autowired private DetalleVentaRepository detalleRepo;
    @Autowired private ProductoRepository productoRepo;

    // --- PÁGINA PRINCIPAL ---
    @GetMapping("/homeadmin")
    public String homeAdmin(Model model) {
        // Fechas por defecto: HOY
        LocalDateTime inicioHoy = LocalDate.now().atStartOfDay();
        LocalDateTime finHoy = LocalDate.now().atTime(LocalTime.MAX);

        // 1. KPIs (Tarjetas) - Se mantienen igual
        Double totalVentas = ventaRepo.sumarVentasPorFecha(inicioHoy, finHoy);
        Long cantidadTickets = ventaRepo.contarVentasPorFecha(inicioHoy, finHoy);
        
        // 2. STOCK BAJO (Lógica nueva)
        List<Producto> todoStockBajo = productoRepo.findByStockLessThanAndEstadoTrue(10);
        
        // Mostrar solo los primeros 20
        List<Producto> stockBajoVista = todoStockBajo.stream().limit(20).collect(Collectors.toList());
        
        model.addAttribute("totalVentas", totalVentas != null ? totalVentas : 0.0);
        model.addAttribute("cantidadTickets", cantidadTickets != null ? cantidadTickets : 0);
        model.addAttribute("listaStockBajo", stockBajoVista);
        model.addAttribute("totalStockBajo", todoStockBajo.size()); // Para saber si mostrar "Ver más"
        model.addAttribute("mostrarVerMas", todoStockBajo.size() > 20);

        // Fecha de hoy para los inputs de los gráficos
        model.addAttribute("fechaHoy", LocalDate.now().toString());

        return "homeadmin";
    }

    // --- EXPORTAR EXCEL ---
    @GetMapping("/homeadmin/exportar-stock")
    public void exportarExcel(HttpServletResponse response) throws IOException {
        response.setContentType("application/octet-stream");
        DateFormat dateFormatter = new SimpleDateFormat("yyyy-MM-dd_HH:mm:ss");
        String currentDateTime = dateFormatter.format(new Date());

        String headerKey = "Content-Disposition";
        String headerValue = "attachment; filename=stock_bajo_" + currentDateTime + ".xlsx";
        response.setHeader(headerKey, headerValue);

        List<Producto> listaProductos = productoRepo.findByStockLessThanAndEstadoTrue(10);
        StockExcelExporter excelExporter = new StockExcelExporter(listaProductos);
        
        excelExporter.export(response);
    }

    // --- APIS PARA GRÁFICOS (AJAX) ---
    
    // 1. Datos Top Productos
    @GetMapping("/api/grafico/top-productos")
    @ResponseBody
    public Map<String, Object> getTopProductos(
            @RequestParam("inicio") String inicioStr, 
            @RequestParam("fin") String finStr) {
        
        LocalDateTime inicio = LocalDate.parse(inicioStr).atStartOfDay();
        LocalDateTime fin = LocalDate.parse(finStr).atTime(LocalTime.MAX);

        List<Object[]> datos = detalleRepo.encontrarTopProductosVendidos(inicio, fin, PageRequest.of(0, 5));
        return procesarDatos(datos);
    }

    // 2. Datos Categorías
    @GetMapping("/api/grafico/categorias")
    @ResponseBody
    public Map<String, Object> getCategorias(
            @RequestParam("inicio") String inicioStr, 
            @RequestParam("fin") String finStr) {
        
        LocalDateTime inicio = LocalDate.parse(inicioStr).atStartOfDay();
        LocalDateTime fin = LocalDate.parse(finStr).atTime(LocalTime.MAX);

        List<Object[]> datos = detalleRepo.encontrarVentasPorCategoria(inicio, fin);
        return procesarDatos(datos);
    }

    // 3. Datos Pagos (Ahora devuelve DINERO, no cantidad)
    @GetMapping("/api/grafico/pagos")
    @ResponseBody
    public Map<String, Object> getPagos(
            @RequestParam("inicio") String inicioStr, 
            @RequestParam("fin") String finStr) {
        
        LocalDateTime inicio = LocalDate.parse(inicioStr).atStartOfDay();
        LocalDateTime fin = LocalDate.parse(finStr).atTime(LocalTime.MAX);

        // Usamos el nuevo método 'sumarVentasPorMetodoPago'
        List<Object[]> datos = ventaRepo.sumarVentasPorMetodoPago(inicio, fin);
        return procesarDatos(datos);
    }

    // Auxiliar para JSON
    private Map<String, Object> procesarDatos(List<Object[]> datos) {
        List<String> labels = new ArrayList<>();
        List<Number> values = new ArrayList<>();
        
        if (datos != null && !datos.isEmpty()) {
            for (Object[] fila : datos) {
                labels.add(fila[0].toString());
                values.add((Number) fila[1]);
            }
        } else {
            labels.add("Sin datos");
            values.add(0);
        }
        
        Map<String, Object> respuesta = new HashMap<>();
        respuesta.put("labels", labels);
        respuesta.put("data", values);
        return respuesta;
    }
}