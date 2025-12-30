package com.giovi.demo.controller;

import com.giovi.demo.entity.*;
import com.giovi.demo.repository.*;
import com.giovi.demo.service.VentaPdfService;
import com.giovi.demo.util.MisVentasExcelExporter;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional; // Importante
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

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
    
    @Autowired private VentaPdfService ventaPdfService;

    // --- 1. CARGA INICIAL (PANTALLA DE VENTA) ---
    @GetMapping("/crear")
    public String crearVenta(Model model, Principal principal) {
        if (principal == null) return "redirect:/login";
        
        model.addAttribute("venta", new Venta());
        model.addAttribute("listaProductos", new ArrayList<Producto>()); 
        return "ventas/crear";
    }

    // --- 2. BÚSQUEDA DE PRODUCTOS (AJAX) ---
    @GetMapping("/buscar-productos")
    public String buscarProductosAjax(@RequestParam String query, Model model, Principal principal) {
        if (query == null || query.trim().isEmpty()) {
            model.addAttribute("listaProductos", new ArrayList<>());
            return "ventas/crear :: listaProductosFragment";
        }

        if (principal != null) {
            Usuario vendedor = usuarioRepository.findByUsername(principal.getName()).orElse(null);
            if (vendedor != null && vendedor.getTienda() != null) {
                List<Producto> productos = productoRepository.buscarPorTerminoYTienda(query, vendedor.getTienda().getId());
                model.addAttribute("listaProductos", productos);
            }
        }
        return "ventas/crear :: listaProductosFragment";
    }

    // --- 3. BÚSQUEDA DE CLIENTE POR DNI (AJAX) ---
    @GetMapping("/buscar-cliente-dni")
    @ResponseBody
    public ResponseEntity<?> buscarClientePorDni(@RequestParam String dni) {
        Cliente cliente = clienteRepository.findByDni(dni);
        if (cliente != null) {
            return ResponseEntity.ok(cliente);
        } else {
            return ResponseEntity.notFound().build();
        }
    }

    // --- 4. API PARA GUARDAR NUEVO CLIENTE (AJAX) ---
    @PostMapping("/api/guardar-cliente")
    @ResponseBody
    public ResponseEntity<?> guardarClienteModal(@RequestBody Map<String, String> datos) {
        try {
            String dni = datos.get("dni");
            String nombre = datos.get("nombre");
            
            if (dni == null || dni.length() != 8 || !dni.matches("\\d+")) {
                return ResponseEntity.badRequest().body("El DNI debe tener exactamente 8 números.");
            }
            if (clienteRepository.findByDni(dni) != null) {
                return ResponseEntity.badRequest().body("El DNI " + dni + " ya se encuentra registrado.");
            }

            Cliente nuevo = new Cliente();
            nuevo.setDni(dni);
            nuevo.setNombre(nombre.toUpperCase()); 
            clienteRepository.save(nuevo);
            
            return ResponseEntity.ok(nuevo);
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.badRequest().body("Error interno al guardar el cliente.");
        }
    }

    // --- 5. GENERAR TICKET PDF (SOLUCIÓN ERROR 500) ---
    @GetMapping("/ticket/{id}")
    @Transactional(readOnly = true) // <--- ESTO SOLUCIONA LA CARGA DE DATOS (LAZY)
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

    // --- 6. GUARDAR VENTA ---
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
                if (!p.getTienda().getId().equals(vendedor.getTienda().getId())) {
                     throw new RuntimeException("ERROR_TIENDA");
                }

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
            double totalFinal = totalBruto - descuento;
            if (totalFinal < 0) totalFinal = 0;

            venta.setMontoTotal(totalFinal);
            venta.setFecha(LocalDateTime.now());
            venta.setUsuario(vendedor);
            venta.setTienda(vendedor.getTienda());

            if ("Efectivo".equals(venta.getMetodoPago())) {
                if (venta.getMontoPago() < totalFinal) {
                    throw new RuntimeException("PAGO_INSUFICIENTE");
                }
                venta.setVuelto(venta.getMontoPago() - totalFinal);
            } else {
                venta.setMontoPago(totalFinal);
                venta.setVuelto(0.0);
            }

            String PREFIJO = "TKT-";
            String ultimoCodigo = ventaRepository.obtenerUltimoNumeroVenta();
            int nuevoNumero = 1;

            if (ultimoCodigo != null && !ultimoCodigo.equals("000000")) {
                if (ultimoCodigo.startsWith(PREFIJO)) {
                    try {
                        String parteNumerica = ultimoCodigo.substring(PREFIJO.length());
                        nuevoNumero = Integer.parseInt(parteNumerica) + 1;
                    } catch (NumberFormatException e) {
                        nuevoNumero = 1;
                    }
                }
            }
            String codigoFinal = PREFIJO + String.format("%06d", nuevoNumero);
            venta.setNumeroVenta(codigoFinal);

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
   @GetMapping("/mis-ventas")
    public String misVentas(Model model, Principal principal,
                            @RequestParam(required = false) LocalDate fechaInicio,
                            @RequestParam(required = false) LocalDate fechaFin) {
        
        // 1. Validar Login
        if (principal == null) return "redirect:/login";
        Usuario usuario = usuarioRepository.findByUsername(principal.getName()).orElse(null);
        if (usuario == null) return "redirect:/logout";

        // 2. Filtro de Fechas
        if (fechaInicio == null) fechaInicio = LocalDate.now();
        if (fechaFin == null) fechaFin = LocalDate.now();

        LocalDateTime inicio = fechaInicio.atStartOfDay();
        LocalDateTime fin = fechaFin.atTime(LocalTime.MAX);

        // 3. Obtener Ventas
        List<Venta> misVentas = ventaRepository.findByUsuarioIdAndFechaBetweenOrderByFechaDesc(
            usuario.getId(), inicio, fin
        );

        // 4. Calcular KPIs y Total General
        Double mEfectivo = 0.0;
        Double mTarjeta = 0.0;
        Double mQr = 0.0;

        for (Venta v : misVentas) {
            String m = (v.getMetodoPago() != null) ? v.getMetodoPago().toLowerCase() : "";
            // Usamos montoTotal como acordamos
            Double valor = (v.getMontoTotal() != null) ? v.getMontoTotal() : 0.0;

            if (m.contains("efectivo") || m.contains("contado")) {
                mEfectivo += valor;
            } else if (m.contains("tarjeta") || m.contains("débito") || m.contains("crédito")) {
                mTarjeta += valor;
            } else {
                mQr += valor;
            }
        }
        
        // NUEVO: Suma total de todo
        Double mTotalGeneral = mEfectivo + mTarjeta + mQr;

        // 5. Enviar a la vista
        model.addAttribute("listaVentas", misVentas);
        model.addAttribute("fechaInicio", fechaInicio);
        model.addAttribute("fechaFin", fechaFin);
        
        model.addAttribute("montoEfectivo", mEfectivo);
        model.addAttribute("montoTarjeta", mTarjeta);
        model.addAttribute("montoQr", mQr);
        model.addAttribute("montoTotalGeneral", mTotalGeneral); // Variable nueva

        return "Ventas/mis_ventas";
    }

    // ==========================================
    // API DETALLES (POPUP)
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
    @GetMapping("/mis-ventas/exportar")
    public void exportarMisVentasExcel(HttpServletResponse response, Principal principal,
                                       @RequestParam(required = false) LocalDate fechaInicio,
                                       @RequestParam(required = false) LocalDate fechaFin) throws IOException {
        
        if (principal == null) {
            response.sendRedirect("/login");
            return;
        }
        Usuario usuario = usuarioRepository.findByUsername(principal.getName()).orElse(null);
        if (usuario == null) {
            response.sendRedirect("/logout");
            return;
        }

        // Manejo de Fechas
        if (fechaInicio == null) fechaInicio = LocalDate.now();
        if (fechaFin == null) fechaFin = LocalDate.now();

        LocalDateTime inicio = fechaInicio.atStartOfDay();
        LocalDateTime fin = fechaFin.atTime(LocalTime.MAX);

        // Obtener datos
        List<Venta> listaVentas = ventaRepository.findByUsuarioIdAndFechaBetweenOrderByFechaDesc(
            usuario.getId(), inicio, fin
        );

        // LIMPIEZA DE BUFFER (Evita archivo corrupto)
        response.reset();
        
        response.setContentType("application/octet-stream");
        String headerKey = "Content-Disposition";
        
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("dd-MM-yyyy");
        String fechaStr = fechaInicio.format(fmt) + "_al_" + fechaFin.format(fmt);
        String headerValue = "attachment; filename=Mis_Ventas_" + fechaStr + ".xlsx";
        
        response.setHeader(headerKey, headerValue);

        MisVentasExcelExporter excelExporter = new MisVentasExcelExporter(listaVentas, fechaInicio, fechaFin);
        excelExporter.export(response);
    }
}