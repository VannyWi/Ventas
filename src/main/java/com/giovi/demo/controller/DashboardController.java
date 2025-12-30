package com.giovi.demo.controller;

import com.giovi.demo.entity.Producto;
import com.giovi.demo.entity.Usuario;
import com.giovi.demo.repository.*;
import com.giovi.demo.util.StockExcelExporter;
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

    // --- API CARGA DINÁMICA USUARIOS ---
    @GetMapping("/api/usuarios-por-tienda")
    @ResponseBody
    public List<Map<String, Object>> getUsuariosPorTienda(@RequestParam(required = false) Long tiendaId) {
        List<Usuario> usuarios;
        if (tiendaId == null) {
            usuarios = usuarioRepo.findAll();
        } else {
            usuarios = usuarioRepo.findByTiendaId(tiendaId);
        }
        return usuarios.stream().map(u -> {
            Map<String, Object> map = new HashMap<>();
            map.put("id", u.getId());
            map.put("nombre", u.getNombre());
            return map;
        }).collect(Collectors.toList());
    }

    // ==========================================
    // 1. SECCIÓN ADMINISTRADOR
    // ==========================================
    
    @GetMapping("/homeadmin")
    public String homeAdmin(Model model,
                            @RequestParam(required = false) String fechaInicio,
                            @RequestParam(required = false) String fechaFin,
                            @RequestParam(required = false) Long tiendaId,
                            @RequestParam(required = false) Long usuarioId) {
        
        // Fechas Globales (HOY)
        LocalDateTime inicioHoy = LocalDate.now().atStartOfDay();
        LocalDateTime finHoy = LocalDate.now().atTime(LocalTime.MAX);

        // Fechas Históricas
        LocalDateTime inicioHistorico = LocalDateTime.of(2000, 1, 1, 0, 0); 
        LocalDateTime finHistorico = LocalDateTime.now().plusYears(100);    

        // Fechas Filtro
        LocalDate fInicio = (fechaInicio != null && !fechaInicio.isEmpty()) ? LocalDate.parse(fechaInicio) : LocalDate.now();
        LocalDate fFin = (fechaFin != null && !fechaFin.isEmpty()) ? LocalDate.parse(fechaFin) : LocalDate.now();
        LocalDateTime inicioFiltro = fInicio.atStartOfDay();
        LocalDateTime finFiltro = fFin.atTime(LocalTime.MAX);

        // KPIs
        Double totalVentas = ventaRepo.sumarVentasPorFecha(inicioHoy, finHoy);
        Long cantidadTickets = ventaRepo.contarVentasPorFecha(inicioHoy, finHoy);
        List<Producto> todoStockBajo = productoRepo.findByStockLessThanAndActivoTrue(10);
        
        model.addAttribute("totalVentas", totalVentas != null ? totalVentas : 0.0);
        model.addAttribute("cantidadTickets", cantidadTickets != null ? cantidadTickets : 0);
        model.addAttribute("alertasStock", todoStockBajo.size());

        // Gráficos Históricos
        List<Object[]> topProductos = detalleRepo.encontrarTopProductosFiltrado(inicioHistorico, finHistorico, null, null, PageRequest.of(0, 10));
        cargarDatosGrafico(model, topProductos, "topProdLabels", "topProdData");

        List<Object[]> categorias = detalleRepo.encontrarCategoriasFiltrado(inicioHistorico, finHistorico, null, null);
        cargarDatosGrafico(model, categorias, "catLabels", "catData");

        // Datos Filtrados
        List<Object[]> pagosData = ventaRepo.sumarVentasPorMetodoPagoFiltrado(inicioFiltro, finFiltro, tiendaId, usuarioId);
        Double mEfectivo = 0.0, mTarjeta = 0.0, mQr = 0.0;
        for (Object[] row : pagosData) {
            if(row[0] != null && row[1] != null) {
                String m = row[0].toString().toLowerCase();
                Double v = (Double) row[1];
                if (m.contains("efectivo") || m.contains("contado")) mEfectivo += v;
                else if (m.contains("tarjeta") || m.contains("débito")) mTarjeta += v;
                else mQr += v;
            }
        }
        model.addAttribute("montoEfectivo", mEfectivo);
        model.addAttribute("montoTarjeta", mTarjeta);
        model.addAttribute("montoQr", mQr);

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

        model.addAttribute("listaTiendas", tiendaRepo.findAll());
        if(tiendaId != null) {
            model.addAttribute("listaUsuarios", usuarioRepo.findByTiendaId(tiendaId));
        } else {
            model.addAttribute("listaUsuarios", usuarioRepo.findAll());
        }
        
        model.addAttribute("fechaInicio", fInicio.toString());
        model.addAttribute("fechaFin", fFin.toString());
        model.addAttribute("selTiendaId", tiendaId);
        model.addAttribute("selUsuarioId", usuarioId);

        return "homeadmin";
    }

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
    // 2. SECCIÓN VENDEDOR (FILTRO UNIFICADO)
    // ==========================================
    @GetMapping("/homevendedores")
    public String homeVendedores(Model model, Authentication auth,
                                 // UN SOLO FILTRO DE RANGO PARA TODO
                                 @RequestParam(value = "fechaInicio", required = false) String fechaInicioStr,
                                 @RequestParam(value = "fechaFin", required = false) String fechaFinStr) {
        
        String username = auth.getName();
        Optional<Usuario> usuarioOpt = usuarioRepo.findByUsername(username);
        if (usuarioOpt.isEmpty()) return "redirect:/login";
        Usuario vendedor = usuarioOpt.get();
        LocalDate hoy = LocalDate.now();

        // 1. CONFIGURACIÓN FECHAS (Default: HOY - HOY)
        LocalDate fInicio = (fechaInicioStr != null && !fechaInicioStr.isEmpty()) ? LocalDate.parse(fechaInicioStr) : hoy;
        LocalDate fFin = (fechaFinStr != null && !fechaFinStr.isEmpty()) ? LocalDate.parse(fechaFinStr) : hoy;
        
        LocalDateTime inicioFiltro = fInicio.atStartOfDay();
        LocalDateTime finFiltro = fFin.atTime(LocalTime.MAX);

        // 2. KPIs SUPERIORES (Siempre HOY - Fijos)
        Double misVentasHoy = ventaRepo.sumarVentasVendedorHoy(vendedor.getId(), hoy.atStartOfDay(), hoy.atTime(LocalTime.MAX));
        Long misTicketsHoy = ventaRepo.contarVentasVendedorHoy(vendedor.getId(), hoy.atStartOfDay(), hoy.atTime(LocalTime.MAX));
        List<Producto> stockBajoTienda = (vendedor.getTienda() != null) ? 
            productoRepo.findByTiendaIdAndStockLessThanAndActivoTrue(vendedor.getTienda().getId(), 10) : new ArrayList<>();

        // 3. PAGOS (Usan el rango seleccionado)
        List<Object[]> pagosData = ventaRepo.sumarMetodosPagoVendedor(vendedor.getId(), inicioFiltro, finFiltro);
        Double mEfectivo = 0.0, mTarjeta = 0.0, mQr = 0.0;
        for (Object[] row : pagosData) {
            if (row[0] != null) {
                String m = row[0].toString().toLowerCase();
                Double v = (Double) row[1];
                if (m.contains("efectivo") || m.contains("contado")) mEfectivo += v;
                else if (m.contains("tarjeta") || m.contains("débito")) mTarjeta += v;
                else mQr += v;
            }
        }

        // 4. GRÁFICO EVOLUCIÓN (Usa el rango seleccionado)
        List<Object[]> ventasRango = ventaRepo.encontrarVentasVendedorRango(vendedor.getId(), inicioFiltro, finFiltro);
        Map<String, Double> mapChart = new LinkedHashMap<>();
        LocalDate tmp = fInicio;
        while (!tmp.isAfter(fFin)) { 
            mapChart.put(tmp.format(DateTimeFormatter.ofPattern("dd/MM")), 0.0); 
            tmp = tmp.plusDays(1); 
        }
        for (Object[] r : ventasRango) {
            String key = ((LocalDateTime)r[0]).format(DateTimeFormatter.ofPattern("dd/MM"));
            if (mapChart.containsKey(key)) {
                mapChart.put(key, mapChart.get(key) + (Double)r[1]);
            }
        }

        model.addAttribute("misVentas", misVentasHoy != null ? misVentasHoy : 0.0);
        model.addAttribute("misTickets", misTicketsHoy != null ? misTicketsHoy : 0);
        model.addAttribute("alertaStock", stockBajoTienda.size());
        model.addAttribute("montoEfectivo", mEfectivo);
        model.addAttribute("montoTarjeta", mTarjeta);
        model.addAttribute("montoQr", mQr);
        model.addAttribute("chartLabels", mapChart.keySet());
        model.addAttribute("chartData", mapChart.values());
        
        // Devolvemos las fechas para mantener el input lleno
        model.addAttribute("fechaInicio", fInicio.toString());
        model.addAttribute("fechaFin", fFin.toString());

        return "homevendedores";
    }
}