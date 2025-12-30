package com.giovi.demo.controller;

import com.giovi.demo.entity.Producto;
import com.giovi.demo.entity.Usuario;
import com.giovi.demo.entity.Venta;
import com.giovi.demo.repository.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Controller
public class DashboardController {

    @Autowired private VentaRepository ventaRepo;
    @Autowired private DetalleVentaRepository detalleRepo;
    @Autowired private ProductoRepository productoRepo;
    @Autowired private UsuarioRepository usuarioRepo;

    // ==========================================
    // 1. SECCIÓN ADMINISTRADOR (Sin cambios)
    // ==========================================
    @GetMapping("/homeadmin")
    public String homeAdmin(Model model) {
        LocalDateTime inicioHoy = LocalDate.now().atStartOfDay();
        LocalDateTime finHoy = LocalDate.now().atTime(LocalTime.MAX);

        Double totalVentas = ventaRepo.sumarVentasPorFecha(inicioHoy, finHoy);
        Long cantidadTickets = ventaRepo.contarVentasPorFecha(inicioHoy, finHoy);
        List<Producto> todoStockBajo = productoRepo.findByStockLessThanAndActivoTrue(10);
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

    // ==========================================
    // 2. SECCIÓN VENDEDOR (ACTUALIZADO)
    // ==========================================
    @GetMapping("/homevendedores")
    public String homeVendedores(Model model, Authentication auth,
                                 // Filtro 1: Tarjetas de Pago (AHORA ES UN RANGO)
                                 @RequestParam(value = "fechaInicioPago", required = false) String fechaInicioPagoStr,
                                 @RequestParam(value = "fechaFinPago", required = false) String fechaFinPagoStr,
                                 // Filtro 2: Gráfico (Rango)
                                 @RequestParam(value = "fechaInicioChart", required = false) String fechaInicioChartStr,
                                 @RequestParam(value = "fechaFinChart", required = false) String fechaFinChartStr) {
        
        String username = auth.getName();
        Optional<Usuario> usuarioOpt = usuarioRepo.findByUsername(username);
        if (usuarioOpt.isEmpty()) return "redirect:/login";
        Usuario vendedor = usuarioOpt.get();

        LocalDate hoy = LocalDate.now();

        // A. Configuración Fechas PAGOS (Default: HOY - HOY)
        LocalDate fInicioPago = (fechaInicioPagoStr != null && !fechaInicioPagoStr.isEmpty()) ? LocalDate.parse(fechaInicioPagoStr) : hoy;
        LocalDate fFinPago = (fechaFinPagoStr != null && !fechaFinPagoStr.isEmpty()) ? LocalDate.parse(fechaFinPagoStr) : hoy;
        
        // B. Configuración Fechas GRÁFICO (Default: Últimos 7 días)
        LocalDate fFinChart = (fechaFinChartStr != null && !fechaFinChartStr.isEmpty()) ? LocalDate.parse(fechaFinChartStr) : hoy;
        LocalDate fInicioChart = (fechaInicioChartStr != null && !fechaInicioChartStr.isEmpty()) ? LocalDate.parse(fechaInicioChartStr) : hoy.minusDays(6);

        // --- LÓGICA DE DATOS ---

        // 1. KPIs SUPERIORES (Siempre fijos a "Hoy" real)
        LocalDateTime startToday = hoy.atStartOfDay();
        LocalDateTime endToday = hoy.atTime(LocalTime.MAX);
        Double misVentasHoy = ventaRepo.sumarVentasVendedorHoy(vendedor.getId(), startToday, endToday);
        Long misTicketsHoy = ventaRepo.contarVentasVendedorHoy(vendedor.getId(), startToday, endToday);

        // Stock Bajo
        List<Producto> stockBajoTienda = new ArrayList<>();
        if (vendedor.getTienda() != null) {
            stockBajoTienda = productoRepo.findByTiendaIdAndStockLessThanAndActivoTrue(vendedor.getTienda().getId(), 10);
        }

        // 2. TARJETAS MÉTODOS DE PAGO (Usa el rango fInicioPago - fFinPago)
        LocalDateTime inicioPago = fInicioPago.atStartOfDay();
        LocalDateTime finPago = fFinPago.atTime(LocalTime.MAX);
        
        List<Object[]> pagosData = ventaRepo.sumarMetodosPagoVendedor(vendedor.getId(), inicioPago, finPago);
        
        Double montoEfectivo = 0.0;
        Double montoTarjeta = 0.0;
        Double montoQr = 0.0;

        for (Object[] row : pagosData) {
            if (row[0] != null && row[1] != null) {
                String metodo = row[0].toString().toLowerCase();
                Double monto = (Double) row[1];
                
                if (metodo.contains("efectivo") || metodo.contains("contado")) {
                    montoEfectivo += monto;
                } else if (metodo.contains("tarjeta") || metodo.contains("débito") || metodo.contains("crédito")) {
                    montoTarjeta += monto;
                } else {
                    montoQr += monto; // Yape/Plin/Otros
                }
            }
        }

        // 3. GRÁFICO BARRAS (Usa el rango fInicioChart - fFinChart)
        LocalDateTime inicioChart = fInicioChart.atStartOfDay();
        LocalDateTime finChart = fFinChart.atTime(LocalTime.MAX);
        
        List<Object[]> ventasRango = ventaRepo.encontrarVentasVendedorRango(vendedor.getId(), inicioChart, finChart);
        
        Map<String, Double> mapaVentasDia = new LinkedHashMap<>();
        LocalDate tempDate = fInicioChart;
        while (!tempDate.isAfter(fFinChart)) {
            mapaVentasDia.put(tempDate.format(DateTimeFormatter.ofPattern("dd/MM")), 0.0);
            tempDate = tempDate.plusDays(1);
        }
        
        for (Object[] row : ventasRango) {
            LocalDateTime fecha = (LocalDateTime) row[0];
            Double monto = (Double) row[1];
            String key = fecha.format(DateTimeFormatter.ofPattern("dd/MM"));
            if (mapaVentasDia.containsKey(key)) {
                mapaVentasDia.put(key, mapaVentasDia.get(key) + monto);
            }
        }

        // --- ATRIBUTOS MODELO ---
        model.addAttribute("misVentas", misVentasHoy != null ? misVentasHoy : 0.0);
        model.addAttribute("misTickets", misTicketsHoy != null ? misTicketsHoy : 0);
        model.addAttribute("alertaStock", stockBajoTienda.size());
        
        model.addAttribute("montoEfectivo", montoEfectivo);
        model.addAttribute("montoTarjeta", montoTarjeta);
        model.addAttribute("montoQr", montoQr);
        
        model.addAttribute("chartLabels", mapaVentasDia.keySet());
        model.addAttribute("chartData", mapaVentasDia.values());

        // Devolver fechas para mantener los inputs llenos
        model.addAttribute("fechaInicioPago", fInicioPago.toString());
        model.addAttribute("fechaFinPago", fFinPago.toString());
        
        model.addAttribute("fechaInicioChart", fInicioChart.toString());
        model.addAttribute("fechaFinChart", fFinChart.toString());

        return "homevendedores";
    }

    // ==========================================
    // 3. APIS REST (ADMIN - AJAX)
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
        List<Object[]> datos = ventaRepo.sumarVentasPorMetodoPago(inicio, fin);
        return procesarDatos(datos);
    }

    // Helper
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
}