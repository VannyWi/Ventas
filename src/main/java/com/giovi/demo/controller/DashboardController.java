package com.giovi.demo.controller;

import com.giovi.demo.entity.Producto;
import com.giovi.demo.entity.Usuario; // Agregado por si acaso
import com.giovi.demo.repository.DetalleVentaRepository;
import com.giovi.demo.repository.ProductoRepository;
import com.giovi.demo.repository.UsuarioRepository; // Agregado
import com.giovi.demo.repository.VentaRepository;
import com.giovi.demo.util.StockExcelExporter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.core.Authentication; // Agregado
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
import java.time.format.DateTimeFormatter; // Agregado
import java.util.*;
import java.util.stream.Collectors;

@Controller
public class DashboardController {

    @Autowired private VentaRepository ventaRepo;
    @Autowired private DetalleVentaRepository detalleRepo;
    @Autowired private ProductoRepository productoRepo;
    @Autowired private UsuarioRepository usuarioRepo; // Agregado para el vendedor

    // --- PÁGINA PRINCIPAL ADMIN ---
    @GetMapping("/homeadmin")
    public String homeAdmin(Model model) {
        LocalDateTime inicioHoy = LocalDate.now().atStartOfDay();
        LocalDateTime finHoy = LocalDate.now().atTime(LocalTime.MAX);

        // 1. KPIs
        Double totalVentas = ventaRepo.sumarVentasPorFecha(inicioHoy, finHoy);
        Long cantidadTickets = ventaRepo.contarVentasPorFecha(inicioHoy, finHoy);
        
        // 2. STOCK BAJO (CORREGIDO: findByStockLessThanAndActivoTrue)
        List<Producto> todoStockBajo = productoRepo.findByStockLessThanAndActivoTrue(10);
        
        // Mostrar solo 20
        List<Producto> stockBajoVista = todoStockBajo.stream().limit(20).collect(Collectors.toList());
        
        model.addAttribute("totalVentas", totalVentas != null ? totalVentas : 0.0);
        model.addAttribute("cantidadTickets", cantidadTickets != null ? cantidadTickets : 0);
        model.addAttribute("listaStockBajo", stockBajoVista);
        model.addAttribute("totalStockBajo", todoStockBajo.size());
        model.addAttribute("mostrarVerMas", todoStockBajo.size() > 20);
        model.addAttribute("alertasStock", todoStockBajo.size()); // KPI Stock

        model.addAttribute("fechaHoy", LocalDate.now().toString());

        return "homeadmin";
    }

    // --- PÁGINA PRINCIPAL VENDEDOR ---
    @GetMapping("/homevendedores")
    public String homeVendedores(Model model, Authentication auth) {
        String username = auth.getName();
        Optional<Usuario> usuarioOpt = usuarioRepo.findByUsername(username);
        
        if (usuarioOpt.isEmpty()) return "redirect:/login";
        Usuario vendedor = usuarioOpt.get();

        LocalDateTime inicioHoy = LocalDate.now().atStartOfDay();
        LocalDateTime finHoy = LocalDate.now().atTime(LocalTime.MAX);
        LocalDateTime inicioSemana = LocalDate.now().minusDays(6).atStartOfDay(); 

        // KPIs Personales
        Double misVentas = ventaRepo.sumarVentasVendedorHoy(vendedor.getId(), inicioHoy, finHoy);
        Long misTickets = ventaRepo.contarVentasVendedorHoy(vendedor.getId(), inicioHoy, finHoy);

        model.addAttribute("nombreVendedor", vendedor.getNombre());
        model.addAttribute("misVentas", misVentas != null ? misVentas : 0.0);
        model.addAttribute("misTickets", misTickets != null ? misTickets : 0);

        // Gráficos Vendedor
        List<Object[]> misPagos = ventaRepo.sumarMetodosPagoVendedor(vendedor.getId(), inicioHoy, finHoy);
        cargarDatosGrafico(model, misPagos, "miPagoLabels", "miPagoData");

        List<Object[]> ventasSemana = ventaRepo.encontrarVentasVendedorRango(vendedor.getId(), inicioSemana, finHoy);
        
        Map<String, Double> ventasPorDia = new LinkedHashMap<>();
        for (int i = 6; i >= 0; i--) {
            String fecha = LocalDate.now().minusDays(i).format(DateTimeFormatter.ofPattern("dd/MM"));
            ventasPorDia.put(fecha, 0.0);
        }
        
        if(ventasSemana != null) {
            for (Object[] fila : ventasSemana) {
                LocalDateTime fechaVenta = (LocalDateTime) fila[0];
                Double monto = (Double) fila[1];
                String key = fechaVenta.format(DateTimeFormatter.ofPattern("dd/MM"));
                if (ventasPorDia.containsKey(key)) {
                    ventasPorDia.put(key, ventasPorDia.get(key) + monto);
                }
            }
        }

        model.addAttribute("semanaLabels", ventasPorDia.keySet());
        model.addAttribute("semanaData", ventasPorDia.values());

        return "homevendedores";
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

        // CORREGIDO AQUÍ TAMBIÉN
        List<Producto> listaProductos = productoRepo.findByStockLessThanAndActivoTrue(10);
        
        StockExcelExporter excelExporter = new StockExcelExporter(listaProductos);
        excelExporter.export(response);
    }

    // --- APIS PARA GRÁFICOS (AJAX) ---
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
        List<Object[]> datos = ventaRepo.sumarVentasPorMetodoPago(inicio, fin);
        return procesarDatos(datos);
    }

    // Helper para APIs
    private Map<String, Object> procesarDatos(List<Object[]> datos) {
        List<String> labels = new ArrayList<>();
        List<Number> values = new ArrayList<>();
        if (datos != null && !datos.isEmpty()) {
            for (Object[] fila : datos) {
                if (fila[0] != null) {
                    labels.add(fila[0].toString());
                    values.add(fila[1] != null ? (Number) fila[1] : 0);
                }
            }
        } else {
            labels.add("Sin datos"); values.add(0);
        }
        Map<String, Object> respuesta = new HashMap<>();
        respuesta.put("labels", labels);
        respuesta.put("data", values);
        return respuesta;
    }

    // Helper para Vistas (Thymeleaf)
    private void cargarDatosGrafico(Model model, List<Object[]> datos, String labelAttr, String dataAttr) {
        List<String> labels = new ArrayList<>();
        List<Number> values = new ArrayList<>();
        if (datos != null && !datos.isEmpty()) {
            for (Object[] fila : datos) {
                if(fila[0] != null) {
                    labels.add(fila[0].toString());
                    values.add(fila[1] != null ? (Number) fila[1] : 0);
                }
            }
        } else {
            labels.add("Sin datos"); values.add(0);
        }
        model.addAttribute(labelAttr, labels);
        model.addAttribute(dataAttr, values);
    }
}