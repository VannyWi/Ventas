package com.giovi.demo.controller;

import com.giovi.demo.entity.*;
import com.giovi.demo.repository.*;
import com.giovi.demo.service.VentaPdfService;
import com.giovi.demo.util.AdminVentasExcelExporter;
import com.giovi.demo.util.MisVentasExcelExporter;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.security.Principal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Controller
@RequestMapping("/ventas")
public class VentaController {

    @Autowired private VentaRepository ventaRepository;
    @Autowired private ProductoRepository productoRepository;
    @Autowired private ClienteRepository clienteRepository;
    @Autowired private UsuarioRepository usuarioRepository;
    @Autowired private TiendaRepository tiendaRepository;
    @Autowired private DetalleVentaRepository detalleVentaRepository;
    
    @Autowired private VentaPdfService ventaPdfService;

    // ==========================================
    // 1. CREAR VENTA
    // ==========================================
    @GetMapping("/crear")
    public String crearVenta(Model model, Principal principal) {
        if (principal == null) return "redirect:/login";
        model.addAttribute("venta", new Venta());
        model.addAttribute("listaProductos", new ArrayList<Producto>()); 
        return "Ventas/crear";
    }

    @GetMapping("/buscar-productos")
    public String buscarProductosAjax(@RequestParam String query, Model model, Principal principal) {
        if (query == null || query.trim().isEmpty()) {
            model.addAttribute("listaProductos", new ArrayList<>());
            return "Ventas/crear :: listaProductosFragment";
        }
        if (principal != null) {
            Usuario vendedor = usuarioRepository.findByUsername(principal.getName()).orElse(null);
            if (vendedor != null && vendedor.getTienda() != null) {
                List<Producto> productos = productoRepository.buscarPorTerminoYTienda(query, vendedor.getTienda().getId());
                model.addAttribute("listaProductos", productos);
            }
        }
        return "Ventas/crear :: listaProductosFragment";
    }

    @GetMapping("/buscar-cliente-dni")
    @ResponseBody
    public ResponseEntity<?> buscarClientePorDni(@RequestParam String dni) {
        Cliente cliente = clienteRepository.findByDni(dni);
        return (cliente != null) ? ResponseEntity.ok(cliente) : ResponseEntity.notFound().build();
    }

    @PostMapping("/api/guardar-cliente")
    @ResponseBody
    public ResponseEntity<?> guardarClienteModal(@RequestBody Map<String, String> datos) {
        try {
            String dni = datos.get("dni");
            String nombre = datos.get("nombre");
            if (dni == null || dni.length() != 8 || !dni.matches("\\d+")) return ResponseEntity.badRequest().body("DNI inválido.");
            if (clienteRepository.findByDni(dni) != null) return ResponseEntity.badRequest().body("El DNI ya existe.");

            Cliente nuevo = new Cliente();
            nuevo.setDni(dni);
            nuevo.setNombre(nombre.toUpperCase()); 
            clienteRepository.save(nuevo);
            return ResponseEntity.ok(nuevo);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Error al guardar cliente.");
        }
    }

    @GetMapping("/ticket/{id}")
    @Transactional(readOnly = true)
    public void generarTicket(@PathVariable Long id, HttpServletResponse response) throws IOException {
        Venta venta = ventaRepository.findById(id).orElse(null);
        if(venta != null) {
            response.setContentType("application/pdf");
            String headerKey = "Content-Disposition";
            String headerValue = "inline; filename=ticket_" + venta.getNumeroVenta() + ".pdf";
            response.setHeader(headerKey, headerValue);
            ventaPdfService.exportar(response, venta);
        }
    }

    @PostMapping("/guardar")
    @Transactional 
    public String guardarVenta(@ModelAttribute Venta venta, 
                               @RequestParam(name = "cliente_dni") String clienteDni,
                               @RequestParam(name = "item_id", required = false) List<Long> itemIds,
                               @RequestParam(name = "item_cantidad", required = false) List<Integer> itemCantidades,
                               Principal principal,
                               RedirectAttributes redirectAttributes) {
        try {
            Usuario vendedor = usuarioRepository.findByUsername(principal.getName()).orElse(null);
            if (vendedor == null) return "redirect:/login";

            if (itemIds == null || itemIds.isEmpty()) {
                redirectAttributes.addFlashAttribute("mensaje", "error_sin_productos");
                return "redirect:/ventas/crear";
            }

            Cliente cliente = clienteRepository.findByDni(clienteDni);
            if (cliente == null) {
                redirectAttributes.addFlashAttribute("mensaje", "error_cliente_no_existe");
                return "redirect:/ventas/crear";
            }
            venta.setCliente(cliente);

            tiendaRepository.obtenerTiendaConBloqueo(vendedor.getTienda().getId());

            double totalBruto = 0.0;
            if(venta.getDetalleVenta() == null) venta.setDetalleVenta(new ArrayList<>());

            for (int i = 0; i < itemIds.size(); i++) {
                Long prodId = itemIds.get(i);
                Integer cantidad = itemCantidades.get(i);
                
                int filasActualizadas = productoRepository.reducirStock(prodId, cantidad);
                if (filasActualizadas == 0) {
                    Producto pError = productoRepository.findById(prodId).orElse(null);
                    if (pError == null || !pError.getActivo()) {
                        throw new RuntimeException("PRODUCTO_INACTIVO:" + (pError != null ? pError.getNombre() : "Desconocido"));
                    } else {
                        throw new RuntimeException("STOCK_AGOTADO:" + pError.getNombre());
                    }
                }

                Producto p = productoRepository.findById(prodId).get();
                if (!p.getTienda().getId().equals(vendedor.getTienda().getId())) throw new RuntimeException("ERROR_TIENDA");

                double subtotal = p.getPrecio() * cantidad;
                totalBruto += subtotal;
                
                DetalleVenta detalle = new DetalleVenta();
                detalle.setProducto(p);
                detalle.setCantidad(cantidad);
                detalle.setPrecioUnitario(p.getPrecio());
                detalle.setSubtotal(subtotal);
                detalle.setVenta(venta);
                venta.getDetalleVenta().add(detalle);
            }

            double descuento = (venta.getDescuento() != null) ? venta.getDescuento() : 0.0;
            double totalFinal = Math.max(0, totalBruto - descuento);

            venta.setMontoTotal(totalFinal);
            venta.setFecha(LocalDateTime.now());
            venta.setUsuario(vendedor);
            venta.setTienda(vendedor.getTienda());

            if ("Efectivo".equals(venta.getMetodoPago())) {
                if (venta.getMontoPago() < totalFinal) throw new RuntimeException("PAGO_INSUFICIENTE");
                venta.setVuelto(venta.getMontoPago() - totalFinal);
            } else {
                venta.setMontoPago(totalFinal);
                venta.setVuelto(0.0);
            }

            String PREFIJO = "TKT-";
            String ultimoCodigo = ventaRepository.obtenerUltimoNumeroVenta();
            int nuevoNumero = 1;
            if (ultimoCodigo != null && !ultimoCodigo.equals("000000") && ultimoCodigo.startsWith(PREFIJO)) {
                try {
                    nuevoNumero = Integer.parseInt(ultimoCodigo.substring(PREFIJO.length())) + 1;
                } catch (NumberFormatException e) { nuevoNumero = 1; }
            }
            venta.setNumeroVenta(PREFIJO + String.format("%06d", nuevoNumero));

            Venta ventaGuardada = ventaRepository.save(venta);
            redirectAttributes.addFlashAttribute("mensaje", "exito");
            return "redirect:/ventas/crear?exito=true&idVenta=" + ventaGuardada.getId();
            
        } catch (RuntimeException e) {
            String msg = e.getMessage();
            if (msg.startsWith("STOCK_AGOTADO")) {
                redirectAttributes.addFlashAttribute("mensaje", "error_stock_real");
                redirectAttributes.addFlashAttribute("productoError", msg.split(":")[1]);
            } else if (msg.startsWith("PRODUCTO_INACTIVO")) {
                redirectAttributes.addFlashAttribute("mensaje", "error_inactivo");
                redirectAttributes.addFlashAttribute("productoError", msg.split(":")[1]);
            } else if (msg.equals("PAGO_INSUFICIENTE")) {
                redirectAttributes.addFlashAttribute("mensaje", "error_pago");
            } else if (msg.equals("ERROR_TIENDA")) {
                redirectAttributes.addFlashAttribute("mensaje", "error_tienda");
            } else {
                e.printStackTrace();
                redirectAttributes.addFlashAttribute("mensaje", "error_grave");
            }
            return "redirect:/ventas/crear";
        }
    }

    // ==========================================
    // 7. MIS VENTAS (VENDEDOR)
    // ==========================================
    @GetMapping("/mis-ventas")
    public String misVentas(Model model, Principal principal,
                            @RequestParam(required = false) LocalDate fechaInicio,
                            @RequestParam(required = false) LocalDate fechaFin) {
        
        if (principal == null) return "redirect:/login";
        Usuario usuario = usuarioRepository.findByUsername(principal.getName()).orElse(null);
        if (usuario == null) return "redirect:/logout";

        // Por defecto: Hoy
        if (fechaInicio == null) fechaInicio = LocalDate.now();
        if (fechaFin == null) fechaFin = LocalDate.now();

        LocalDateTime inicio = fechaInicio.atStartOfDay();
        LocalDateTime fin = fechaFin.atTime(LocalTime.MAX);

        List<Venta> misVentas = ventaRepository.findByUsuarioIdAndFechaBetweenOrderByFechaDesc(
            usuario.getId(), inicio, fin
        );

        model.addAttribute("listaVentas", misVentas);
        model.addAttribute("fechaInicio", fechaInicio);
        model.addAttribute("fechaFin", fechaFin);

        return "Ventas/mis_ventas";
    }

    @GetMapping("/mis-ventas/exportar")
    public void exportarMisVentasExcel(HttpServletResponse response, Principal principal,
                                       @RequestParam(required = false) LocalDate fechaInicio,
                                       @RequestParam(required = false) LocalDate fechaFin) throws IOException {
        
        if (principal == null) return;
        Usuario usuario = usuarioRepository.findByUsername(principal.getName()).orElse(null);
        if (usuario == null) return;

        if (fechaInicio == null) fechaInicio = LocalDate.now();
        if (fechaFin == null) fechaFin = LocalDate.now();

        LocalDateTime inicio = fechaInicio.atStartOfDay();
        LocalDateTime fin = fechaFin.atTime(LocalTime.MAX);

        List<Venta> listaVentas = ventaRepository.findByUsuarioIdAndFechaBetweenOrderByFechaDesc(
            usuario.getId(), inicio, fin
        );

        // --- SOLUCIÓN ERROR EXCEL ---
        // Generamos en memoria (RAM) para evitar conflictos de "response committed"
        MisVentasExcelExporter excelExporter = new MisVentasExcelExporter(listaVentas, fechaInicio, fechaFin);
        ByteArrayOutputStream outStream = new ByteArrayOutputStream();
        excelExporter.generate(outStream); // Usamos el nuevo método generate
        byte[] content = outStream.toByteArray();

        // Configurar respuesta
        response.setContentType("application/octet-stream");
        String fechaStr = fechaInicio.format(DateTimeFormatter.ofPattern("dd-MM-yyyy")) + "_al_" + fechaFin.format(DateTimeFormatter.ofPattern("dd-MM-yyyy"));
        response.setHeader("Content-Disposition", "attachment; filename=Mis_Ventas_" + fechaStr + ".xlsx");
        response.setContentLength(content.length);

        // Escribir al cliente
        response.getOutputStream().write(content);
        response.getOutputStream().flush();
    }

    // ==========================================
    // 8. HISTORIAL ADMIN (GLOBAL)
    // ==========================================
    @GetMapping("/admin/historial-ventas")
    public String historialVentasAdmin(Model model, Principal principal,
                                       @RequestParam(required = false) LocalDate fechaInicio,
                                       @RequestParam(required = false) LocalDate fechaFin,
                                       @RequestParam(required = false) Long tiendaId,
                                       @RequestParam(required = false) Long usuarioId) {
        
        if (principal == null) return "redirect:/login";

        // Por defecto: Solo Hoy (SOLICITADO)
        if (fechaInicio == null) fechaInicio = LocalDate.now(); 
        if (fechaFin == null) fechaFin = LocalDate.now();

        LocalDateTime inicio = fechaInicio.atStartOfDay();
        LocalDateTime fin = fechaFin.atTime(LocalTime.MAX);

        // Listas para filtros
        model.addAttribute("listaTiendas", tiendaRepository.findAll());
        if (tiendaId != null && tiendaId > 0) {
            model.addAttribute("listaUsuarios", usuarioRepository.findByTiendaId(tiendaId)); 
        } else {
            model.addAttribute("listaUsuarios", usuarioRepository.findAll());
        }

        Long filtroTienda = (tiendaId != null && tiendaId > 0) ? tiendaId : null;
        Long filtroUsuario = (usuarioId != null && usuarioId > 0) ? usuarioId : null;

        // Búsqueda
        List<Venta> listaVentas = ventaRepository.filtrarVentasAdmin(inicio, fin, filtroTienda, filtroUsuario);

        // Calcular Totales para Resumen Financiero
        calcularResumenFinanciero(model, listaVentas);

        model.addAttribute("listaVentas", listaVentas);
        model.addAttribute("fechaInicio", fechaInicio);
        model.addAttribute("fechaFin", fechaFin);
        model.addAttribute("tiendaSeleccionadaId", tiendaId);
        model.addAttribute("usuarioSeleccionadoId", usuarioId);

        return "Ventas/historial_admin";
    }

    @GetMapping("/admin/historial-ventas/exportar")
    public void exportarHistorialAdmin(HttpServletResponse response,
                                       @RequestParam(required = false) LocalDate fechaInicio,
                                       @RequestParam(required = false) LocalDate fechaFin,
                                       @RequestParam(required = false) Long tiendaId,
                                       @RequestParam(required = false) Long usuarioId) throws IOException {
        
        if (fechaInicio == null) fechaInicio = LocalDate.now();
        if (fechaFin == null) fechaFin = LocalDate.now();

        LocalDateTime inicio = fechaInicio.atStartOfDay();
        LocalDateTime fin = fechaFin.atTime(LocalTime.MAX);
        Long filtroTienda = (tiendaId != null && tiendaId > 0) ? tiendaId : null;
        Long filtroUsuario = (usuarioId != null && usuarioId > 0) ? usuarioId : null;

        List<Venta> listaVentas = ventaRepository.filtrarVentasAdmin(inicio, fin, filtroTienda, filtroUsuario);

        // --- SOLUCIÓN ERROR EXCEL (BUFFER) ---
        AdminVentasExcelExporter exporter = new AdminVentasExcelExporter(listaVentas, fechaInicio, fechaFin);
        ByteArrayOutputStream outStream = new ByteArrayOutputStream();
        exporter.generate(outStream); // Usamos generate
        byte[] content = outStream.toByteArray();

        response.setContentType("application/octet-stream");
        String fechaStr = fechaInicio.format(DateTimeFormatter.ofPattern("dd-MM-yyyy"));
        response.setHeader("Content-Disposition", "attachment; filename=Historial_Admin_" + fechaStr + ".xlsx");
        response.setContentLength(content.length);

        response.getOutputStream().write(content);
        response.getOutputStream().flush();
    }

    // ==========================================
    // 9. API DETALLE (POPUP) - JSON
    // ==========================================
    @GetMapping("/api/detalle/{id}")
    @ResponseBody
    public ResponseEntity<?> obtenerDetalleVenta(@PathVariable Long id) {
        return ventaRepository.findById(id).map(venta -> {
            List<Map<String, Object>> detalles = venta.getDetalleVenta().stream().map(d -> {
                Map<String, Object> map = new HashMap<>();
                String prodNombre = (d.getProducto() != null) ? d.getProducto().getNombre() : "Eliminado";
                String prodCodigo = (d.getProducto() != null) ? d.getProducto().getCodigoBarras() : "---";
                map.put("producto", prodNombre);
                map.put("codigo", prodCodigo);
                map.put("cantidad", d.getCantidad());
                map.put("precioUnitario", d.getPrecioUnitario());
                Double subtotal = (d.getSubtotal() != null) ? d.getSubtotal() : 0.0;
                map.put("subtotal", subtotal);
                return map;
            }).collect(Collectors.toList());
            return ResponseEntity.ok(detalles);
        }).orElse(ResponseEntity.notFound().build());
    }

    // Método auxiliar para Resumen Financiero
    private void calcularResumenFinanciero(Model model, List<Venta> ventas) {
        Double mEfectivo = 0.0;
        Double mTarjeta = 0.0;
        Double mQr = 0.0;

        for (Venta v : ventas) {
            String m = (v.getMetodoPago() != null) ? v.getMetodoPago().toLowerCase() : "";
            Double valor = (v.getMontoTotal() != null) ? v.getMontoTotal() : 0.0;

            if (m.contains("efectivo") || m.contains("contado")) {
                mEfectivo += valor;
            } else if (m.contains("tarjeta") || m.contains("débito") || m.contains("crédito")) {
                mTarjeta += valor;
            } else {
                mQr += valor;
            }
        }
        model.addAttribute("montoEfectivo", mEfectivo);
        model.addAttribute("montoTarjeta", mTarjeta);
        model.addAttribute("montoQr", mQr);
        model.addAttribute("montoTotalGeneral", mEfectivo + mTarjeta + mQr);
    }
}