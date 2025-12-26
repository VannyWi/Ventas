package com.giovi.demo.controller;

import com.giovi.demo.entity.Producto;
import com.giovi.demo.entity.Usuario;
import com.giovi.demo.repository.DetalleVentaRepository;
import com.giovi.demo.repository.ProductoRepository;
import com.giovi.demo.repository.UsuarioRepository;
import com.giovi.demo.repository.VentaRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Controller
public class DashboardController {

    @Autowired private VentaRepository ventaRepo;
    @Autowired private DetalleVentaRepository detalleRepo;
    @Autowired private ProductoRepository productoRepo;
    @Autowired private UsuarioRepository usuarioRepo;

    // --- DASHBOARD ADMIN ---
    @GetMapping("/homeadmin")
    public String homeAdmin(Model model) {
        LocalDateTime inicioHoy = LocalDate.now().atStartOfDay();
        LocalDateTime finHoy = LocalDate.now().atTime(LocalTime.MAX);

        // 1. KPIs
        Double totalVentas = ventaRepo.sumarVentasPorFecha(inicioHoy, finHoy);
        Long cantidadTickets = ventaRepo.contarVentasPorFecha(inicioHoy, finHoy);
        List<Producto> stockBajo = productoRepo.findByStockLessThan(10);

        model.addAttribute("totalVentas", totalVentas != null ? totalVentas : 0.0);
        model.addAttribute("cantidadTickets", cantidadTickets != null ? cantidadTickets : 0);
        model.addAttribute("listaStockBajo", stockBajo);
        model.addAttribute("alertasStock", stockBajo.size());

        // 2. Gráficos
        List<Object[]> topProductos = detalleRepo.encontrarTopProductosVendidos(PageRequest.of(0, 5));
        cargarDatosGrafico(model, topProductos, "topProdLabels", "topProdData");

        List<Object[]> pagos = ventaRepo.contarVentasPorMetodoPago(inicioHoy, finHoy);
        cargarDatosGrafico(model, pagos, "pagoLabels", "pagoData");

        List<Object[]> categorias = detalleRepo.encontrarVentasPorCategoria(inicioHoy, finHoy);
        cargarDatosGrafico(model, categorias, "catLabels", "catData");

        return "homeadmin";
    }

    // --- DASHBOARD VENDEDOR ---
    @GetMapping("/homevendedores")
    public String homeVendedores(Model model, Authentication auth) {
        String username = auth.getName();
        
        // CORRECCIÓN AQUÍ: Manejo de Optional
        Optional<Usuario> usuarioOpt = usuarioRepo.findByUsername(username);
        
        if (usuarioOpt.isEmpty()) {
            return "redirect:/login"; // Si no encuentra al usuario, volver al login
        }
        
        Usuario vendedor = usuarioOpt.get(); // Obtenemos el objeto Usuario real

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
        List<Object[]> misPagos = ventaRepo.contarMetodosPagoVendedor(vendedor.getId(), inicioHoy, finHoy);
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

    private void cargarDatosGrafico(Model model, List<Object[]> datos, String labelAttr, String dataAttr) {
        List<String> labels = new ArrayList<>();
        List<Number> values = new ArrayList<>();
        
        if (datos != null && !datos.isEmpty()) {
            for (Object[] fila : datos) {
                if(fila[0] != null && fila[1] != null) {
                    labels.add(fila[0].toString());
                    values.add((Number) fila[1]);
                }
            }
        } else {
            labels.add("Sin datos");
            values.add(0);
        }
        
        model.addAttribute(labelAttr, labels);
        model.addAttribute(dataAttr, values);
    }
}