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
    // 1. SECCIÓN ADMINISTRADOR
    // ==========================================
    @GetMapping("/homeadmin")
    public String homeAdmin(Model model) {
        LocalDateTime inicioHoy = LocalDate.now().atStartOfDay();
        LocalDateTime finHoy = LocalDate.now().atTime(LocalTime.MAX);

        // KPIs Admin
        Double totalVentas = ventaRepo.sumarVentasPorFecha(inicioHoy, finHoy);
        Long cantidadTickets = ventaRepo.contarVentasPorFecha(inicioHoy, finHoy);
        
        // Stock Bajo Global
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
    // 2. SECCIÓN VENDEDOR (REDISEÑADA)
    // ==========================================
    @GetMapping("/homevendedores")
    public String homeVendedores(Model model, Authentication auth,
                                 @RequestParam(value = "fechaInicio", required = false) String fechaInicioStr,
                                 @RequestParam(value = "fechaFin", required = false) String fechaFinStr) {
        
        String username = auth.getName();
        Optional<Usuario> usuarioOpt = usuarioRepo.findByUsername(username);
        if (usuarioOpt.isEmpty()) return "redirect:/login";
        Usuario vendedor = usuarioOpt.get();

        // 1. FECHAS FILTRO (Afecta a Gráfico y Tarjetas de Pago)
        // Por defecto: últimos 7 días
        LocalDate fFin = (fechaFinStr != null && !fechaFinStr.isEmpty()) ? LocalDate.parse(fechaFinStr) : LocalDate.now();
        LocalDate fInicio = (fechaInicioStr != null && !fechaInicioStr.isEmpty()) ? LocalDate.parse(fechaInicioStr) : fFin.minusDays(6);
        
        LocalDateTime inicioFiltro = fInicio.atStartOfDay();
        LocalDateTime finFiltro = fFin.atTime(LocalTime.MAX);

        // 2. KPIs SUPERIORES (Siempre HOY)
        LocalDateTime inicioHoy = LocalDate.now().atStartOfDay();
        LocalDateTime finHoy = LocalDate.now().atTime(LocalTime.MAX);
        
        Double misVentasHoy = ventaRepo.sumarVentasVendedorHoy(vendedor.getId(), inicioHoy, finHoy);
        Long misTicketsHoy = ventaRepo.contarVentasVendedorHoy(vendedor.getId(), inicioHoy, finHoy);

        List<Producto> stockBajoTienda = new ArrayList<>();
        if (vendedor.getTienda() != null) {
            stockBajoTienda = productoRepo.findByTiendaIdAndStockLessThanAndActivoTrue(vendedor.getTienda().getId(), 10);
        }

        // 3. TARJETAS MÉTODOS DE PAGO (Suma de DINERO filtrada por fecha)
        // Usamos 'sumarMetodosPagoVendedor' que definimos previamente en el repo
        List<Object[]> pagosData = ventaRepo.sumarMetodosPagoVendedor(vendedor.getId(), inicioFiltro, finFiltro);
        
        Double montoEfectivo = 0.0;
        Double montoTarjeta = 0.0;
        Double montoQr = 0.0;

        for (Object[] row : pagosData) {
            if (row[0] != null && row[1] != null) {
                String metodo = row[0].toString().toLowerCase();
                Double monto = (Double) row[1]; // Ahora es Double (Dinero), no Long (Cantidad)
                
                if (metodo.contains("efectivo") || metodo.contains("contado")) {
                    montoEfectivo += monto;
                } else if (metodo.contains("tarjeta") || metodo.contains("débito") || metodo.contains("crédito")) {
                    montoTarjeta += monto;
                } else {
                    montoQr += monto; // Yape, Plin, etc.
                }
            }
        }

        // 4. GRÁFICO BARRAS (Ventas diarias filtradas por fecha)
        List<Object[]> ventasRango = ventaRepo.encontrarVentasVendedorRango(vendedor.getId(), inicioFiltro, finFiltro);
        
        Map<String, Double> mapaVentasDia = new LinkedHashMap<>();
        LocalDate tempDate = fInicio;
        while (!tempDate.isAfter(fFin)) {
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

        // --- ENVIAR AL MODELO ---
        // KPIs Hoy
        model.addAttribute("misVentas", misVentasHoy != null ? misVentasHoy : 0.0);
        model.addAttribute("misTickets", misTicketsHoy != null ? misTicketsHoy : 0);
        model.addAttribute("alertaStock", stockBajoTienda.size());
        
        // Pagos Dinero (Filtrado)
        model.addAttribute("montoEfectivo", montoEfectivo);
        model.addAttribute("montoTarjeta", montoTarjeta);
        model.addAttribute("montoQr", montoQr);
        
        // Gráfico (Filtrado)
        model.addAttribute("chartLabels", mapaVentasDia.keySet());
        model.addAttribute("chartData", mapaVentasDia.values());

        // Fechas Inputs
        model.addAttribute("fechaInicio", fInicio.toString());
        model.addAttribute("fechaFin", fFin.toString());

        return "homevendedores";
    }

    // ==========================================
    // 3. APIS REST (PARA ADMIN)
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