package com.giovi.demo.controller;

import com.giovi.demo.entity.*;
import com.giovi.demo.repository.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.security.Principal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Controller
@RequestMapping("/ventas")
public class VentaController {

    @Autowired private VentaRepository ventaRepository;
    @Autowired private ProductoRepository productoRepository;
    @Autowired private ClienteRepository clienteRepository;
    @Autowired private UsuarioRepository usuarioRepository;
    @Autowired private TiendaRepository tiendaRepository; // Necesario para el bloqueo de seguridad

    // --- 1. CARGA INICIAL (PANTALLA DE VENTA) ---
    @GetMapping("/crear")
    public String crearVenta(Model model, Principal principal) {
        if (principal == null) return "redirect:/login";
        
        // Inicializamos la venta y la lista de productos vacía
        model.addAttribute("venta", new Venta());
        model.addAttribute("listaProductos", new ArrayList<Producto>()); 
        
        return "ventas/crear";
    }

    // --- 2. BÚSQUEDA DE PRODUCTOS (AJAX) ---
    @GetMapping("/buscar-productos")
    public String buscarProductosAjax(@RequestParam String query, Model model, Principal principal) {
        // Validación: Si la búsqueda está vacía, retornamos lista vacía
        if (query == null || query.trim().isEmpty()) {
            model.addAttribute("listaProductos", new ArrayList<>());
            return "ventas/crear :: listaProductosFragment";
        }

        if (principal != null) {
            Usuario vendedor = usuarioRepository.findByUsername(principal.getName()).orElse(null);
            if (vendedor != null && vendedor.getTienda() != null) {
                // Busca productos por nombre/código, filtrando por tienda y estado activo
                List<Producto> productos = productoRepository.buscarPorTerminoYTienda(query, vendedor.getTienda().getId());
                model.addAttribute("listaProductos", productos);
            }
        }
        // Retorna solo el fragmento HTML de la tabla
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

    // --- 4. API PARA GUARDAR NUEVO CLIENTE DESDE MODAL (AJAX) ---
    @PostMapping("/api/guardar-cliente")
    @ResponseBody
    public ResponseEntity<?> guardarClienteModal(@RequestBody Map<String, String> datos) {
        try {
            String dni = datos.get("dni");
            String nombre = datos.get("nombre");
            
            // Validaciones de seguridad para datos limpios
            if (dni == null || dni.length() != 8 || !dni.matches("\\d+")) {
                return ResponseEntity.badRequest().body("El DNI debe tener exactamente 8 números.");
            }
            if (clienteRepository.findByDni(dni) != null) {
                return ResponseEntity.badRequest().body("El DNI " + dni + " ya se encuentra registrado.");
            }

            Cliente nuevo = new Cliente();
            nuevo.setDni(dni);
            nuevo.setNombre(nombre.toUpperCase()); // Guardar siempre en mayúsculas
            clienteRepository.save(nuevo);
            
            return ResponseEntity.ok(nuevo);
            
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.badRequest().body("Error interno al guardar el cliente.");
        }
    }

    // --- 5. GUARDAR VENTA (LÓGICA PRINCIPAL) ---
    @PostMapping("/guardar")
    @Transactional // IMPORTANTE: Si algo falla, se revierte todo (stock, número, venta)
    public String guardarVenta(@ModelAttribute Venta venta, 
                               @RequestParam(name = "cliente_dni") String clienteDni,
                               @RequestParam(name = "item_id", required = false) List<Long> itemIds,
                               @RequestParam(name = "item_cantidad", required = false) List<Integer> itemCantidades,
                               Principal principal,
                               RedirectAttributes redirectAttributes) {
        try {
            // A. Validar Usuario
            Usuario vendedor = usuarioRepository.findByUsername(principal.getName()).orElse(null);
            if (vendedor == null) return "redirect:/login";

            // B. Validar Carrito Vacío
            if (itemIds == null || itemIds.isEmpty()) {
                redirectAttributes.addFlashAttribute("mensaje", "error_sin_productos");
                return "redirect:/ventas/crear";
            }

            // C. Validar Cliente (Debe existir obligatoriamente)
            Cliente cliente = clienteRepository.findByDni(clienteDni);
            if (cliente == null) {
                redirectAttributes.addFlashAttribute("mensaje", "error_cliente_no_existe");
                return "redirect:/ventas/crear";
            }
            venta.setCliente(cliente);

            // --- BLOQUEO PESIMISTA: Evita duplicidad de números de venta ---
            // Bloqueamos la tienda momentáneamente para serializar la generación del ticket
            tiendaRepository.obtenerTiendaConBloqueo(vendedor.getTienda().getId());

            double totalBruto = 0.0;
            if(venta.getDetalleVenta() == null) venta.setDetalleVenta(new ArrayList<>());

            // D. Procesar Productos (Validación Atómica de Stock y Estado)
            for (int i = 0; i < itemIds.size(); i++) {
                Long prodId = itemIds.get(i);
                Integer cantidad = itemCantidades.get(i);
                
                // Intento restar stock en la BD verificando que (stock >= cantidad) Y (activo = true)
                int filasActualizadas = productoRepository.reducirStock(prodId, cantidad);
                
                if (filasActualizadas == 0) {
                    // Si falló, averiguamos la causa para mostrar la alerta correcta
                    Producto pError = productoRepository.findById(prodId).orElse(null);
                    
                    if (pError == null || !pError.getActivo()) {
                        throw new RuntimeException("PRODUCTO_INACTIVO:" + (pError != null ? pError.getNombre() : "Desconocido"));
                    } else {
                        throw new RuntimeException("STOCK_AGOTADO:" + pError.getNombre());
                    }
                }

                // Recuperamos el producto ya actualizado para agregarlo al detalle
                Producto p = productoRepository.findById(prodId).get();
                
                // Seguridad extra: ¿Es de mi tienda?
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

            // E. Calcular Totales
            double descuento = (venta.getDescuento() != null) ? venta.getDescuento() : 0.0;
            double totalFinal = totalBruto - descuento;
            if (totalFinal < 0) totalFinal = 0;

            venta.setMontoTotal(totalFinal);
            venta.setFecha(LocalDateTime.now());
            venta.setUsuario(vendedor);
            venta.setTienda(vendedor.getTienda());

            // F. Validar Pago
            if ("Efectivo".equals(venta.getMetodoPago())) {
                if (venta.getMontoPago() < totalFinal) {
                    throw new RuntimeException("PAGO_INSUFICIENTE");
                }
                venta.setVuelto(venta.getMontoPago() - totalFinal);
            } else {
                venta.setMontoPago(totalFinal);
                venta.setVuelto(0.0);
            }

            // --- G. GENERAR NÚMERO CORRELATIVO (TKT-000001) ---
            String PREFIJO = "TKT-"; // Puedes cambiarlo a "BOL-", "FAC-", etc.
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
            // Formatear: TKT + 6 dígitos (rellena con ceros)
            String codigoFinal = PREFIJO + String.format("%06d", nuevoNumero);
            venta.setNumeroVenta(codigoFinal);

            // H. Guardar Final
            ventaRepository.save(venta);
            redirectAttributes.addFlashAttribute("mensaje", "exito");
            
        } catch (RuntimeException e) {
            String msg = e.getMessage();
            // Mapeo de errores para SweetAlert
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
        }
        return "redirect:/ventas/crear";
    }
}