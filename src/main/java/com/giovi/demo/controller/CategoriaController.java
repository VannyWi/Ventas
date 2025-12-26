package com.giovi.demo.controller;

import com.giovi.demo.entity.Categoria;
import com.giovi.demo.repository.CategoriaRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.text.Normalizer;
import java.util.List;
import java.util.regex.Pattern;

@Controller
@RequestMapping("/categorias")
public class CategoriaController {

    @Autowired
    private CategoriaRepository categoriaRepository;

    @GetMapping
    public String index(Model model) {
        model.addAttribute("listaCategorias", categoriaRepository.findAll());
        model.addAttribute("categoria", new Categoria());
        return "categorias/index";
    }

    @PostMapping("/guardar")
    public String guardar(Categoria categoria, RedirectAttributes redirectAttributes) {
        try {
            // 1. OBTENER TODAS LAS CATEGORÍAS PARA COMPARAR EN JAVA
            // (Hacemos esto para poder normalizar tildes y diéresis manualmente)
            List<Categoria> todas = categoriaRepository.findAll();
            
            String nombreNuevo = normalizarTexto(categoria.getNombre());
            boolean duplicado = false;

            for (Categoria c : todas) {
                // Si el nombre "limpio" es igual...
                if (normalizarTexto(c.getNombre()).equals(nombreNuevo)) {
                    // ... y NO es la misma categoría que estamos editando (mismo ID)
                    if (categoria.getId() == null || !c.getId().equals(categoria.getId())) {
                        duplicado = true;
                        break;
                    }
                }
            }

            if (duplicado) {
                redirectAttributes.addFlashAttribute("mensaje", "duplicado");
                redirectAttributes.addFlashAttribute("tipo", "warning");
                return "redirect:/categorias";
            }

            // 2. GUARDAR SI NO HUBO DUPLICADOS
            boolean esEdicion = (categoria.getId() != null);
            categoriaRepository.save(categoria);
            
            redirectAttributes.addFlashAttribute("mensaje", esEdicion ? "editado" : "guardado");
            redirectAttributes.addFlashAttribute("tipo", "success");
            
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("mensaje", "error");
            redirectAttributes.addFlashAttribute("tipo", "error");
        }
        
        return "redirect:/categorias";
    }

    @GetMapping("/eliminar/{id}")
    public String eliminar(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        try {
            categoriaRepository.deleteById(id);
            redirectAttributes.addFlashAttribute("mensaje", "eliminado");
            redirectAttributes.addFlashAttribute("tipo", "success");
        } catch (DataIntegrityViolationException e) {
            // Error cuando la categoría ya se usó en un producto
            redirectAttributes.addFlashAttribute("mensaje", "tiene_productos");
            redirectAttributes.addFlashAttribute("tipo", "error");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("mensaje", "error");
            redirectAttributes.addFlashAttribute("tipo", "error");
        }
        return "redirect:/categorias";
    }

    // --- FUNCIÓN MÁGICA PARA QUITAR TILDES Y DIÉRESIS ---
    // Convierte "Cigüeña" -> "ciguena", "Lácteos" -> "lacteos"
    private String normalizarTexto(String input) {
        if (input == null) return "";
        // 1. Separa las tildes de las letras (Normalización NFD)
        String normalized = Normalizer.normalize(input, Normalizer.Form.NFD);
        // 2. Elimina las marcas de tildes con una expresión regular
        String sinTildes = Pattern.compile("\\p{InCombiningDiacriticalMarks}+").matcher(normalized).replaceAll("");
        // 3. Convierte a minúsculas y quita espacios extra
        return sinTildes.toLowerCase().trim();
    }
}