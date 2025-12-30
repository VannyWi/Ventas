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
    @Autowired private TiendaRepository tiendaRepo;

    // ==========================================
    // 1. SECCIÓN ADMINISTRADOR (CON FILTROS)
    // ==========================================
    @GetMapping("/homeadmin")
    public String homeAdmin(Model model,
                            @RequestParam(required = false) String fechaInicio,
                            @RequestParam(required = false) String fechaFin,
                            @RequestParam(required = false) Long tiendaId,
                            @RequestParam(required = false) Long usuarioId) {
        
        // --- 1. KPIs GLOBALES (Siempre muestran HOY general) ---
        LocalDateTime inicioHoy = LocalDate.now().atStartOfDay();
        LocalDateTime finHoy = LocalDate.now().atTime(LocalTime.MAX);

        Double totalVentas = ventaRepo.sumarVentasPorFecha(inicioHoy, finHoy);
        Long cantidadTickets = ventaRepo.contarVentasPorFecha(inicioHoy, finHoy);
        List<Producto> todoStockBajo = productoRepo.findByStockLessThanAndActivoTrue(10);
        
        model.addAttribute("totalVentas", totalVentas != null ? totalVentas : 0.0);
        model.addAttribute("cantidadTickets", cantidadTickets != null ? cantidadTickets : 0);
        model.addAttribute("alertasStock", todoStockBajo.size());

        // --- 2. PREPARAR FILTROS (Para Gráficos y Tarjetas de Pago) ---
        // Por defecto: HOY
        LocalDate fInicio = (fechaInicio != null && !fechaInicio.isEmpty()) ? LocalDate.parse(fechaInicio) : LocalDate.now();
        LocalDate fFin = (fechaFin != null && !fechaFin.isEmpty()) ? LocalDate.parse(fechaFin) : LocalDate.now();
        
        LocalDateTime inicioFiltro = fInicio.atStartOfDay();
        LocalDateTime finFiltro = fFin.atTime(LocalTime.MAX);

        // --- 3. TARJETAS PAGOS (Filtradas) ---
        List<Object[]> pagosData = ventaRepo.sumarVentasPorMetodoPagoFiltrado(inicioFiltro, finFiltro, tiendaId, usuarioId);
        
        Double montoEfectivo = 0.0;
        Double montoTarjeta = 0.0;
        Double montoQr = 0.0;

        for (Object[] row : pagosData) {
            if(row[0] != null && row[1] != null) {
                String metodo = row[0].toString().toLowerCase();
                Double monto = (Double) row[1];
                if (metodo.contains("efectivo") || metodo.contains("contado")) montoEfectivo += monto;
                else if (metodo.contains("tarjeta") || metodo.contains("débito")) montoTarjeta += monto;
                else montoQr += monto;
            }
        }
        
        model.addAttribute("montoEfectivo", montoEfectivo);
        model.addAttribute("montoTarjeta", montoTarjeta);
        model.addAttribute("montoQr", montoQr);

        // --- 4. GRÁFICOS (Filtrados) ---
        
        // Top 10 Productos
        List<Object[]> topProductos = detalleRepo.encontrarTopProductosFiltrado(inicioFiltro, finFiltro, tiendaId, usuarioId, PageRequest.of(0, 10));
        cargarDatosGrafico(model, topProductos, "topProdLabels", "topProdData");

        // Categorías
        List<Object[]> categorias = detalleRepo.encontrarCategoriasFiltrado(inicioFiltro, finFiltro, tiendaId, usuarioId);
        cargarDatosGrafico(model, categorias, "catLabels", "catData");

        // Ganancias por Vendedor
        List<Object[]> vendedoresData = ventaRepo.encontrarVentasPorVendedorYTienda(inicioFiltro, finFiltro, tiendaId, usuarioId);
        List<String> vendLabels = new ArrayList<>();
        List<Double> vendData = new ArrayList<>();
        if(vendedoresData != null) {
            for(Object[] row : vendedoresData) {
                vendLabels.add(row[0].toString() + " (" + row[1].toString() + ")"); 
                vendData.add((Double) row[2]);
            }
        }
        model.addAttribute("vendLabels", vendLabels);
        model.addAttribute("vendData", vendData);

        // --- 5. COMBOS Y ESTADO ---
        model.addAttribute("listaTiendas", tiendaRepo.findAll());
        model.addAttribute("listaUsuarios", usuarioRepo.findAll());
        
        // Devolver selección para mantener el filtro visualmente
        model.addAttribute("fechaInicio", fInicio.toString());
        model.addAttribute("fechaFin", fFin.toString());
        model.addAttribute("selTiendaId", tiendaId);
        model.addAttribute("selUsuarioId", usuarioId);

        return "homeadmin";
    }

    // Método auxiliar para procesar datos de gráficos
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
        }
        model.addAttribute(labelAttr, labels);
        model.addAttribute(dataAttr, values);
    }

    // ==========================================
    // 2. SECCIÓN VENDEDOR (TU CÓDIGO EXISTENTE)
    // ==========================================
    @GetMapping("/homevendedores")
    public String homeVendedores(Model model, Authentication auth,
                                 @RequestParam(value = "fechaInicioPago", required = false) String fechaInicioPagoStr,
                                 @RequestParam(value = "fechaFinPago", required = false) String fechaFinPagoStr,
                                 @RequestParam(value = "fechaInicioChart", required = false) String fechaInicioChartStr,
                                 @RequestParam(value = "fechaFinChart", required = false) String fechaFinChartStr) {
        
        String username = auth.getName();
        Optional<Usuario> usuarioOpt = usuarioRepo.findByUsername(username);
        if (usuarioOpt.isEmpty()) return "redirect:/login";
        Usuario vendedor = usuarioOpt.get();
        LocalDate hoy = LocalDate.now();

        LocalDate fInicioPago = (fechaInicioPagoStr != null && !fechaInicioPagoStr.isEmpty()) ? LocalDate.parse(fechaInicioPagoStr) : hoy;
        LocalDate fFinPago = (fechaFinPagoStr != null && !fechaFinPagoStr.isEmpty()) ? LocalDate.parse(fechaFinPagoStr) : hoy;
        LocalDate fFinChart = (fechaFinChartStr != null && !fechaFinChartStr.isEmpty()) ? LocalDate.parse(fechaFinChartStr) : hoy;
        LocalDate fInicioChart = (fechaInicioChartStr != null && !fechaInicioChartStr.isEmpty()) ? LocalDate.parse(fechaInicioChartStr) : hoy.minusDays(6);

        // KPIs Hoy
        Double misVentasHoy = ventaRepo.sumarVentasVendedorHoy(vendedor.getId(), hoy.atStartOfDay(), hoy.atTime(LocalTime.MAX));
        Long misTicketsHoy = ventaRepo.contarVentasVendedorHoy(vendedor.getId(), hoy.atStartOfDay(), hoy.atTime(LocalTime.MAX));
        List<Producto> stockBajoTienda = (vendedor.getTienda() != null) ? 
            productoRepo.findByTiendaIdAndStockLessThanAndActivoTrue(vendedor.getTienda().getId(), 10) : new ArrayList<>();

        // Pagos (Rango)
        List<Object[]> pagosData = ventaRepo.sumarMetodosPagoVendedor(vendedor.getId(), fInicioPago.atStartOfDay(), fFinPago.atTime(LocalTime.MAX));
        Double mEfectivo = 0.0, mTarjeta = 0.0, mQr = 0.0;
        for (Object[] row : pagosData) {
            String m = row[0].toString().toLowerCase();
            Double v = (Double) row[1];
            if (m.contains("efectivo")) mEfectivo += v;
            else if (m.contains("tarjeta")) mTarjeta += v;
            else mQr += v;
        }

        // Gráfico (Rango)
        List<Object[]> ventasRango = ventaRepo.encontrarVentasVendedorRango(vendedor.getId(), fInicioChart.atStartOfDay(), fFinChart.atTime(LocalTime.MAX));
        Map<String, Double> mapChart = new LinkedHashMap<>();
        LocalDate tmp = fInicioChart;
        while (!tmp.isAfter(fFinChart)) { mapChart.put(tmp.format(DateTimeFormatter.ofPattern("dd/MM")), 0.0); tmp = tmp.plusDays(1); }
        for (Object[] r : ventasRango) mapChart.put(((LocalDateTime)r[0]).format(DateTimeFormatter.ofPattern("dd/MM")), mapChart.getOrDefault(((LocalDateTime)r[0]).format(DateTimeFormatter.ofPattern("dd/MM")), 0.0) + (Double)r[1]);

        model.addAttribute("misVentas", misVentasHoy != null ? misVentasHoy : 0.0);
        model.addAttribute("misTickets", misTicketsHoy != null ? misTicketsHoy : 0);
        model.addAttribute("alertaStock", stockBajoTienda.size());
        model.addAttribute("montoEfectivo", mEfectivo);
        model.addAttribute("montoTarjeta", mTarjeta);
        model.addAttribute("montoQr", mQr);
        model.addAttribute("chartLabels", mapChart.keySet());
        model.addAttribute("chartData", mapChart.values());
        
        model.addAttribute("fechaInicioPago", fInicioPago.toString());
        model.addAttribute("fechaFinPago", fFinPago.toString());
        model.addAttribute("fechaInicioChart", fInicioChart.toString());
        model.addAttribute("fechaFinChart", fFinChart.toString());

        return "homevendedores";
    }
}