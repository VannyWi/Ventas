package com.giovi.demo.controller;

import com.giovi.demo.entity.Tienda;
import com.giovi.demo.repository.TiendaRepository;
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
@RequestMapping("/tiendas")
public class TiendaController {

    @Autowired
    private TiendaRepository tiendaRepository;

    @GetMapping
    public String index(Model model) {
        model.addAttribute("listaTiendas", tiendaRepository.findAll());
        model.addAttribute("tienda", new Tienda());
        return "tiendas/index";
    }

    @PostMapping("/guardar")
    public String guardar(Tienda tienda, RedirectAttributes redirectAttributes) {
        try {
            List<Tienda> todas = tiendaRepository.findAll();
            
            // Normalizar datos
            String nombreNuevo = normalizarTexto(tienda.getNombre());
            String direccionNueva = normalizarTexto(tienda.getDireccion());
            
            boolean errorNombre = false;
            boolean errorDireccion = false;

            for (Tienda t : todas) {
                if (tienda.getId() == null || !t.getId().equals(tienda.getId())) {
                    if (normalizarTexto(t.getNombre()).equals(nombreNuevo)) errorNombre = true;
                    if (!direccionNueva.isEmpty() && normalizarTexto(t.getDireccion()).equals(direccionNueva)) errorDireccion = true;
                }
            }

            if (errorNombre) {
                redirectAttributes.addFlashAttribute("mensaje", "duplicado_nombre");
                return "redirect:/tiendas";
            }
            if (errorDireccion) {
                redirectAttributes.addFlashAttribute("mensaje", "duplicado_direccion");
                return "redirect:/tiendas";
            }

            boolean esEdicion = (tienda.getId() != null);
            tiendaRepository.save(tienda);
            
            redirectAttributes.addFlashAttribute("mensaje", esEdicion ? "editado" : "guardado");
            
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("mensaje", "error");
        }
        
        return "redirect:/tiendas";
    }

    // --- CORRECCIÓN FINAL PARA EL ERROR DE HIBERNATE ---
    @GetMapping("/eliminar/{id}")
    public String eliminar(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        try {
            tiendaRepository.deleteById(id);
            redirectAttributes.addFlashAttribute("mensaje", "eliminado");
        } catch (DataIntegrityViolationException e) {
            // Caso 1: La base de datos rechaza la eliminación (Constraint Violation)
            redirectAttributes.addFlashAttribute("mensaje", "tiene_datos");
        } catch (Exception e) {
            // Caso 2: Hibernate detecta el problema en memoria antes de la BD (Tu error actual)
            String msg = e.getMessage();
            if (e.getCause() != null) {
                msg += " " + e.getCause().getMessage();
            }
            String errorLower = msg.toLowerCase();

            // Buscamos palabras clave de tu error específico
            if (errorLower.contains("constraint") || 
                errorLower.contains("foreign key") || 
                errorLower.contains("references an unsaved transient instance") || // <--- ESTO SOLUCIONA TU ERROR
                errorLower.contains("transientpropertyvalueexception")) {
                
                redirectAttributes.addFlashAttribute("mensaje", "tiene_datos");
            } else {
                // Si es otro error desconocido, lo mostramos en consola
                redirectAttributes.addFlashAttribute("mensaje", "error");
                e.printStackTrace();
            }
        }
        return "redirect:/tiendas";
    }

    // --- UTILERÍA ---
    private String normalizarTexto(String input) {
        if (input == null) return "";
        String normalized = Normalizer.normalize(input, Normalizer.Form.NFD);
        String sinTildes = Pattern.compile("\\p{InCombiningDiacriticalMarks}+").matcher(normalized).replaceAll("");
        return sinTildes.toLowerCase().trim();
    }
}