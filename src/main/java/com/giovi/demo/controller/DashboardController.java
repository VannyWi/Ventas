package com.giovi.demo.controller;

import com.giovi.demo.entity.Producto;
import com.giovi.demo.repository.DetalleVentaRepository;
import com.giovi.demo.repository.ProductoRepository;
import com.giovi.demo.repository.VentaRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

@Controller
public class DashboardController {

    @Autowired private VentaRepository ventaRepo;
    @Autowired private DetalleVentaRepository detalleRepo;
    @Autowired private ProductoRepository productoRepo;

    @GetMapping("/homeadmin")
    public String homeAdmin(Model model) {
        // Definir rango de tiempo: HOY (00:00 - 23:59)
        LocalDateTime inicioDia = LocalDate.now().atStartOfDay();
        LocalDateTime finDia = LocalDate.now().atTime(LocalTime.MAX);

        // --- TARJETAS INFORMATIVAS (KPIs) ---
        Double totalVentas = ventaRepo.sumarVentasPorFecha(inicioDia, finDia);
        Long cantidadTickets = ventaRepo.contarVentasPorFecha(inicioDia, finDia);
        List<Producto> stockBajo = productoRepo.findByStockLessThan(10); // Umbral de alerta: 10 unidades

        model.addAttribute("totalVentas", totalVentas != null ? totalVentas : 0.0);
        model.addAttribute("cantidadTickets", cantidadTickets != null ? cantidadTickets : 0);
        model.addAttribute("listaStockBajo", stockBajo);
        model.addAttribute("alertasStock", stockBajo.size());

        // --- DATOS PARA GRÁFICOS (CHART.JS) ---
        
        // 1. Gráfico: Top 5 Productos (Barras)
        List<Object[]> topProductos = detalleRepo.encontrarTopProductosVendidos(PageRequest.of(0, 5));
        List<String> topProdLabels = new ArrayList<>();
        List<Long> topProdData = new ArrayList<>();
        for (Object[] row : topProductos) {
            topProdLabels.add((String) row[0]);
            topProdData.add((Long) row[1]);
        }
        model.addAttribute("topProdLabels", topProdLabels);
        model.addAttribute("topProdData", topProdData);

        // 2. Gráfico: Métodos de Pago (Pastel)
        List<Object[]> metodosPago = ventaRepo.contarVentasPorMetodoPago(inicioDia, finDia);
        List<String> pagoLabels = new ArrayList<>();
        List<Long> pagoData = new ArrayList<>();
        for (Object[] row : metodosPago) {
            pagoLabels.add((String) row[0]);
            pagoData.add((Long) row[1]);
        }
        model.addAttribute("pagoLabels", pagoLabels);
        model.addAttribute("pagoData", pagoData);

        // 3. Gráfico: Ventas por Categoría (Dona)
        List<Object[]> categorias = detalleRepo.encontrarVentasPorCategoria(inicioDia, finDia);
        List<String> catLabels = new ArrayList<>();
        List<Double> catData = new ArrayList<>();
        for (Object[] row : categorias) {
            catLabels.add((String) row[0]);
            catData.add((Double) row[1]);
        }
        model.addAttribute("catLabels", catLabels);
        model.addAttribute("catData", catData);

        return "homeadmin";
    }
}