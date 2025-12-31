package com.giovi.demo.controller;

import com.giovi.demo.entity.Producto;
import com.giovi.demo.entity.Usuario;
import com.giovi.demo.repository.CategoriaRepository;
import com.giovi.demo.repository.ProductoRepository;
import com.giovi.demo.repository.TiendaRepository;
import com.giovi.demo.repository.UsuarioRepository;
import com.giovi.demo.util.StockExcelExporter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.http.ResponseEntity; 

import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.security.Principal;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.ArrayList;
import java.text.Normalizer;
import java.util.List;
import java.util.regex.Pattern;
import java.util.Map;
import java.util.HashMap;

@Controller
@RequestMapping("/productos")
public class ProductoController {

    @Autowired private ProductoRepository productoRepository;
    @Autowired private TiendaRepository tiendaRepository;
    @Autowired private CategoriaRepository categoriaRepository;
    @Autowired private UsuarioRepository usuarioRepository;

    @GetMapping
    public String index(Model model) {
        model.addAttribute("listaProductos", productoRepository.findAll());
        model.addAttribute("listaTiendas", tiendaRepository.findAll());
        model.addAttribute("listaCategorias", categoriaRepository.findAll());
        model.addAttribute("producto", new Producto());
        return "productos/index";
    }

    @PostMapping("/guardar")
    public String guardar(Producto producto, RedirectAttributes redirectAttributes) {
        try {
            List<Producto> todos = productoRepository.findAll();
            String nombreNuevo = normalizarTexto(producto.getNombre());
            String codigoNuevo = normalizarTexto(producto.getCodigoBarras());
            Long tiendaIdSeleccionada = producto.getTienda().getId();

            boolean errorNombre = false;
            boolean errorCodigo = false;

            for (Producto p : todos) {
                if (p.getTienda().getId().equals(tiendaIdSeleccionada)) {
                    if (producto.getId() == null || !p.getId().equals(producto.getId())) {
                        if (normalizarTexto(p.getNombre()).equals(nombreNuevo)) errorNombre = true;
                        if (normalizarTexto(p.getCodigoBarras()).equals(codigoNuevo)) errorCodigo = true;
                    }
                }
            }

            if (errorNombre) {
                redirectAttributes.addFlashAttribute("mensaje", "duplicado_nombre");
                redirectAttributes.addFlashAttribute("tipo", "warning");
                return "redirect:/productos";
            }
            if (errorCodigo) {
                redirectAttributes.addFlashAttribute("mensaje", "duplicado_codigo");
                redirectAttributes.addFlashAttribute("tipo", "warning");
                return "redirect:/productos";
            }
            if (producto.getPrecio() < 0 || producto.getStock() < 0) {
                redirectAttributes.addFlashAttribute("mensaje", "negativo");
                redirectAttributes.addFlashAttribute("tipo", "warning");
                return "redirect:/productos";
            }

            if(producto.getId() == null) producto.setActivo(true);
            
            boolean esEdicion = (producto.getId() != null);
            productoRepository.save(producto);
            
            redirectAttributes.addFlashAttribute("mensaje", esEdicion ? "editado" : "guardado");
            redirectAttributes.addFlashAttribute("tipo", "success");

        } catch (Exception e) {
            e.printStackTrace();
            redirectAttributes.addFlashAttribute("mensaje", "error");
            redirectAttributes.addFlashAttribute("tipo", "error");
        }
        return "redirect:/productos";
    }

    @GetMapping("/eliminar/{id}")
    public String eliminar(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        try {
            Producto p = productoRepository.findById(id).orElse(null);
            if (p != null) {
                p.setActivo(false);
                productoRepository.save(p);
                redirectAttributes.addFlashAttribute("mensaje", "eliminado");
                redirectAttributes.addFlashAttribute("tipo", "warning");
            }
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("mensaje", "error");
            redirectAttributes.addFlashAttribute("tipo", "error");
        }
        return "redirect:/productos";
    }

    @GetMapping("/reactivar/{id}")
    public String reactivar(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        try {
            Producto p = productoRepository.findById(id).orElse(null);
            if (p != null) {
                p.setActivo(true);
                productoRepository.save(p);
                redirectAttributes.addFlashAttribute("mensaje", "reactivado");
                redirectAttributes.addFlashAttribute("tipo", "success");
            }
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("mensaje", "error");
            redirectAttributes.addFlashAttribute("tipo", "error");
        }
        return "redirect:/productos";
    }

    private String normalizarTexto(String input) {
        if (input == null) return "";
        String normalized = Normalizer.normalize(input, Normalizer.Form.NFD);
        String sinTildes = Pattern.compile("\\p{InCombiningDiacriticalMarks}+").matcher(normalized).replaceAll("");
        return sinTildes.toLowerCase().trim();
    }
    
    // --- API STOCK ---
    @GetMapping("/api/buscar")
    @ResponseBody
    public ResponseEntity<?> buscarPorCodigoYTienda(@RequestParam String codigo, @RequestParam Long tiendaId) {
        return productoRepository.findByCodigoBarrasAndTienda_IdAndActivoTrue(codigo, tiendaId)
                .map(producto -> ResponseEntity.ok(producto))
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/api/stock/actualizar")
    @ResponseBody
    public ResponseEntity<?> actualizarStock(@RequestParam Long id, @RequestParam Integer cantidad) {
        try {
            Producto p = productoRepository.findById(id).orElse(null);
            if (p == null) return ResponseEntity.notFound().build();

            p.setStock(p.getStock() + cantidad);
            productoRepository.save(p);
            
            Map<String, String> response = new HashMap<>();
            response.put("mensaje", "Stock actualizado correctamente");
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.internalServerError().build();
        }
    }

    // ==========================================
    // NUEVOS MÉTODOS BAJO STOCK (ACTIVOS ONLY)
    // ==========================================

    @GetMapping("/bajo-stock")
    public String bajoStock(Model model, Principal principal) {
        if (principal == null) return "redirect:/login";

        Usuario usuario = usuarioRepository.findByUsername(principal.getName()).orElse(null);
        if (usuario == null) return "redirect:/logout";

        List<Producto> productosBajoStock;

        if (usuario.getTienda() != null) {
            // Filtra: Tienda Correcta + Stock < 10 + Activo = true
            productosBajoStock = productoRepository.findByTiendaIdAndStockLessThanAndActivoTrue(
                usuario.getTienda().getId(), 
                10
            );
        } else {
            // Filtra: Stock < 10 + Activo = true (Global)
            productosBajoStock = productoRepository.findByStockLessThanAndActivoTrue(10);
        }

        model.addAttribute("listaProductos", productosBajoStock);
        return "productos/bajo_stock";
    }

   @GetMapping("/bajo-stock/exportar")
    public void exportarExcelBajoStock(HttpServletResponse response, Principal principal) throws IOException {
        if (principal == null) {
            response.sendRedirect("/login");
            return;
        }

        Usuario usuario = usuarioRepository.findByUsername(principal.getName()).orElse(null);
        List<Producto> listProductos = new ArrayList<>();
        String nombreTiendaReporte = "Todas las Tiendas (Central)"; // Valor por defecto

        if (usuario != null) {
            if (usuario.getTienda() != null) {
                // Es un vendedor de tienda específica
                listProductos = productoRepository.findByTiendaIdAndStockLessThanAndActivoTrue(usuario.getTienda().getId(), 10);
                nombreTiendaReporte = usuario.getTienda().getNombre();
            } else {
                // Es un administrador (ve todo)
                listProductos = productoRepository.findByStockLessThanAndActivoTrue(10);
            }
        }

        response.reset(); // Limpieza clave
        response.setContentType("application/octet-stream");
        DateFormat dateFormatter = new SimpleDateFormat("yyyy-MM-dd_HH-mm");
        String currentDateTime = dateFormatter.format(new Date());

        String headerKey = "Content-Disposition";
        String headerValue = "attachment; filename=Stock_Critico_" + currentDateTime + ".xlsx";
        response.setHeader(headerKey, headerValue);

        // Pasamos el nombre de la tienda al constructor
        StockExcelExporter excelExporter = new StockExcelExporter(listProductos, nombreTiendaReporte);
        excelExporter.export(response);
    }
}