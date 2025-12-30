package com.giovi.demo.controller;

import com.giovi.demo.entity.Producto;
import com.giovi.demo.entity.Usuario;
import com.giovi.demo.entity.Venta;
import com.giovi.demo.repository.*;
import com.giovi.demo.util.StockExcelExporter;
import com.giovi.demo.util.VentasExcelExporter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.core.Authentication;
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
    @Autowired private UsuarioRepository usuarioRepo;

    // ==========================================
    // 1. SECCIÓN ADMINISTRADOR
    // ==========================================
    
    @GetMapping("/homeadmin")
    public String homeAdmin(Model model) {
        LocalDateTime inicioHoy = LocalDate.now().atStartOfDay();
        LocalDateTime finHoy = LocalDate.now().atTime(LocalTime.MAX);

        // KPIs
        Double totalVentas = ventaRepo.sumarVentasPorFecha(inicioHoy, finHoy);
        Long cantidadTickets = ventaRepo.contarVentasPorFecha(inicioHoy, finHoy);
        
        // Stock Bajo (Activos y < 10)
        List<Producto> todoStockBajo = productoRepo.findByStockLessThanAndActivoTrue(10);
        
        // Limitamos visualización a 20 para no saturar la tabla
        List<Producto> stockBajoVista = todoStockBajo.stream().limit(20).collect(Collectors.toList());
        
        model.addAttribute("totalVentas", totalVentas != null ? totalVentas : 0.0);
        model.addAttribute("cantidadTickets", cantidadTickets != null ? cantidadTickets : 0);
        model.addAttribute("listaStockBajo", stockBajoVista);
        model.addAttribute("totalStockBajo", todoStockBajo.size());
        model.addAttribute("mostrarVerMas", todoStockBajo.size() > 20);
        model.addAttribute("alertasStock", todoStockBajo.size());
        model.addAttribute("fechaHoy", LocalDate.now().toString());

        return "homeadmin";
    }

    // Exportar Stock Admin
    @GetMapping("/homeadmin/exportar-stock")
    public void exportarStockAdmin(HttpServletResponse response) throws IOException {
        prepararExcel(response, "stock_critico_global");
        List<Producto> listaProductos = productoRepo.findByStockLessThanAndActivoTrue(10);
        StockExcelExporter excelExporter = new StockExcelExporter(listaProductos);
        excelExporter.export(response);
    }

    // ==========================================
    // 2. SECCIÓN VENDEDOR
    // ==========================================
    
    @GetMapping("/homevendedores")
    public String homeVendedores(Model model, Authentication auth,
                                 @RequestParam(value = "fechaInicio", required = false) String fechaInicioStr,
                                 @RequestParam(value = "fechaFin", required = false) String fechaFinStr) {
        
        String username = auth.getName();
        Optional<Usuario> usuarioOpt = usuarioRepo.findByUsername(username);
        if (usuarioOpt.isEmpty()) return "redirect:/login";
        Usuario vendedor = usuarioOpt.get();

        // Fechas filtro (Tabla)
        LocalDate fInicio = (fechaInicioStr != null && !fechaInicioStr.isEmpty()) ? LocalDate.parse(fechaInicioStr) : LocalDate.now();
        LocalDate fFin = (fechaFinStr != null && !fechaFinStr.isEmpty()) ? LocalDate.parse(fechaFinStr) : LocalDate.now();
        
        LocalDateTime inicioFiltro = fInicio.atStartOfDay();
        LocalDateTime finFiltro = fFin.atTime(LocalTime.MAX);

        // Fechas Hoy (KPIs)
        LocalDateTime inicioHoy = LocalDate.now().atStartOfDay();
        LocalDateTime finHoy = LocalDate.now().atTime(LocalTime.MAX);
        
        Double misVentasHoy = ventaRepo.sumarVentasVendedorHoy(vendedor.getId(), inicioHoy, finHoy);
        Long misTicketsHoy = ventaRepo.contarVentasVendedorHoy(vendedor.getId(), inicioHoy, finHoy);

        // Lista Historial Ventas
        List<Venta> historialVentas = ventaRepo.findByUsuarioIdAndFechaBetweenOrderByFechaDesc(
                vendedor.getId(), inicioFiltro, finFiltro);

        // Lista Stock Bajo Tienda
        List<Producto> stockBajoTienda = new ArrayList<>();
        if (vendedor.getTienda() != null) {
            stockBajoTienda = productoRepo.findByTiendaIdAndStockLessThanAndActivoTrue(vendedor.getTienda().getId(), 10);
        }

        model.addAttribute("nombreVendedor", vendedor.getNombre());
        model.addAttribute("nombreTienda", vendedor.getTienda() != null ? vendedor.getTienda().getNombre() : "Sin Tienda");
        model.addAttribute("misVentas", misVentasHoy != null ? misVentasHoy : 0.0);
        model.addAttribute("misTickets", misTicketsHoy != null ? misTicketsHoy : 0);
        model.addAttribute("historialVentas", historialVentas);
        model.addAttribute("stockBajoTienda", stockBajoTienda);
        model.addAttribute("alertaStock", stockBajoTienda.size());
        model.addAttribute("fechaInicio", fInicio.toString());
        model.addAttribute("fechaFin", fFin.toString());
        model.addAttribute("fechaHoy", LocalDate.now().toString());

        return "homevendedores";
    }

    // Exportar Ventas Vendedor
    @GetMapping("/homevendedores/exportar-ventas")
    public void exportarVentasVendedor(
            Authentication auth,
            HttpServletResponse response,
            @RequestParam("fechaInicio") String inicioStr,
            @RequestParam("fechaFin") String finStr) throws IOException {
        
        String username = auth.getName();
        Usuario vendedor = usuarioRepo.findByUsername(username).orElse(null);
        if(vendedor == null) return;

        LocalDateTime inicio = LocalDate.parse(inicioStr).atStartOfDay();
        LocalDateTime fin = LocalDate.parse(finStr).atTime(LocalTime.MAX);

        prepararExcel(response, "mis_ventas_" + inicioStr + "_al_" + finStr);
        List<Venta> lista = ventaRepo.findByUsuarioIdAndFechaBetweenOrderByFechaDesc(vendedor.getId(), inicio, fin);
        VentasExcelExporter excelExporter = new VentasExcelExporter(lista);
        excelExporter.export(response);
    }

    // Exportar Stock Vendedor
    @GetMapping("/homevendedores/exportar-stock")
    public void exportarStockVendedor(Authentication auth, HttpServletResponse response) throws IOException {
        String username = auth.getName();
        Usuario vendedor = usuarioRepo.findByUsername(username).orElse(null);
        
        if (vendedor != null && vendedor.getTienda() != null) {
            prepararExcel(response, "stock_critico_tienda");
            List<Producto> lista = productoRepo.findByTiendaIdAndStockLessThanAndActivoTrue(vendedor.getTienda().getId(), 10);
            StockExcelExporter excelExporter = new StockExcelExporter(lista);
            excelExporter.export(response);
        }
    }

    // ==========================================
    // 3. APIS REST (PARA GRÁFICOS AJAX)
    // ==========================================

    @GetMapping("/api/grafico/top-productos")
    @ResponseBody
    public Map<String, Object> getTopProductos(@RequestParam("inicio") String inicioStr, @RequestParam("fin") String finStr) {
        LocalDateTime inicio = LocalDate.parse(inicioStr).atStartOfDay();
        LocalDateTime fin = LocalDate.parse(finStr).atTime(LocalTime.MAX);
        List<Object[]> datos = detalleRepo.encontrarTopProductosVendidos(inicio, fin, PageRequest.of(0, 5));
        return procesarDatos(datos);
    }

    @GetMapping("/api/grafico/categorias")
    @ResponseBody
    public Map<String, Object> getCategorias(@RequestParam("inicio") String inicioStr, @RequestParam("fin") String finStr) {
        LocalDateTime inicio = LocalDate.parse(inicioStr).atStartOfDay();
        LocalDateTime fin = LocalDate.parse(finStr).atTime(LocalTime.MAX);
        List<Object[]> datos = detalleRepo.encontrarVentasPorCategoria(inicio, fin);
        return procesarDatos(datos);
    }

    @GetMapping("/api/grafico/pagos")
    @ResponseBody
    public Map<String, Object> getPagos(@RequestParam("inicio") String inicioStr, @RequestParam("fin") String finStr) {
        LocalDateTime inicio = LocalDate.parse(inicioStr).atStartOfDay();
        LocalDateTime fin = LocalDate.parse(finStr).atTime(LocalTime.MAX);
        // Admin ve SUMA DE DINERO
        List<Object[]> datos = ventaRepo.sumarVentasPorMetodoPago(inicio, fin);
        return procesarDatos(datos);
    }

    @GetMapping("/api/vendedor/grafico/pagos")
    @ResponseBody
    public Map<String, Object> getVendedorPagos(Authentication auth, @RequestParam("inicio") String inicioStr, @RequestParam("fin") String finStr) {
        String username = auth.getName();
        Usuario vendedor = usuarioRepo.findByUsername(username).orElse(null);
        if(vendedor == null) return new HashMap<>();

        LocalDateTime inicio = LocalDate.parse(inicioStr).atStartOfDay();
        LocalDateTime fin = LocalDate.parse(finStr).atTime(LocalTime.MAX);
        // Vendedor ve CANTIDAD DE TICKETS (Según tu pedido)
        List<Object[]> datos = ventaRepo.contarMetodosPagoVendedor(vendedor.getId(), inicio, fin);
        return procesarDatos(datos);
    }

    // ==========================================
    // 4. MÉTODOS AUXILIARES (HELPERS)
    // ==========================================

    // Helper: Procesa List<Object[]> a JSON para Chart.js
    private Map<String, Object> procesarDatos(List<Object[]> datos) {
        List<String> labels = new ArrayList<>();
        List<Number> values = new ArrayList<>();
        
        if (datos != null && !datos.isEmpty()) {
            for (Object[] fila : datos) {
                if (fila[0] != null) {
                    labels.add(fila[0].toString());
                    // Asegurar que el valor numérico se maneje bien (Double o Long)
                    values.add(fila[1] != null ? (Number) fila[1] : 0);
                }
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

    // Helper: Configura cabecera para descarga Excel
    private void prepararExcel(HttpServletResponse response, String nombreArchivo) {
        response.setContentType("application/octet-stream");
        DateFormat dateFormatter = new SimpleDateFormat("yyyy-MM-dd_HH:mm:ss");
        String currentDateTime = dateFormatter.format(new Date());
        String headerKey = "Content-Disposition";
        String headerValue = "attachment; filename=" + nombreArchivo + "_" + currentDateTime + ".xlsx";
        response.setHeader(headerKey, headerValue);
    }
}