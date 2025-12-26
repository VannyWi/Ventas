package com.giovi.demo.controller;

import com.giovi.demo.entity.Cliente;
import com.giovi.demo.repository.ClienteRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/clientes")
public class ClienteController {

    @Autowired
    private ClienteRepository clienteRepository;

    // 1. LISTAR (y formulario vacío para crear)
    @GetMapping
    public String index(Model model) {
        model.addAttribute("listaClientes", clienteRepository.findAll());
        model.addAttribute("cliente", new Cliente()); // Formulario vacío
        return "clientes/index";
    }

    // 2. PREPARAR EDICIÓN (Carga los datos en el formulario de la misma página)
    @GetMapping("/editar/{id}")
    public String editar(@PathVariable Long id, Model model) {
        Cliente cliente = clienteRepository.findById(id).orElse(null);
        // Si no existe, volvemos al inicio
        if (cliente == null) {
            return "redirect:/clientes";
        }
        
        // Cargamos la lista y el cliente a editar en el modelo
        model.addAttribute("listaClientes", clienteRepository.findAll());
        model.addAttribute("cliente", cliente);
        return "clientes/index"; // Reutilizamos la vista index
    }

    // 3. GUARDAR (Crear o Actualizar)
    @PostMapping("/guardar")
    public String guardar(Cliente cliente, RedirectAttributes redirectAttributes) {
        try {
            // VALIDACIÓN DE DNI DUPLICADO
            Cliente existente = clienteRepository.findByDni(cliente.getDni());
            
            // Si existe un cliente con ese DNI y NO es el mismo que estamos editando...
            if (existente != null && (cliente.getId() == null || !existente.getId().equals(cliente.getId()))) {
                redirectAttributes.addFlashAttribute("mensaje", "duplicado");
                redirectAttributes.addFlashAttribute("tipo", "warning");
                return "redirect:/clientes";
            }
            if (!cliente.getDni().matches("\\d{8}")) {
                redirectAttributes.addFlashAttribute("mensaje", "dni_invalido");
                redirectAttributes.addFlashAttribute("tipo", "warning");
                return "redirect:/clientes";
            }
            // 2. Nombre solo letras y espacios
            if (!cliente.getNombre().matches("^[a-zA-ZáéíóúÁÉÍÓÚñÑ\\s]+$")) {
                redirectAttributes.addFlashAttribute("mensaje", "nombre_invalido");
                redirectAttributes.addFlashAttribute("tipo", "warning");
                return "redirect:/clientes";
            }

            boolean esEdicion = (cliente.getId() != null);
            
            clienteRepository.save(cliente);
            
            redirectAttributes.addFlashAttribute("mensaje", esEdicion ? "editado" : "guardado");
            redirectAttributes.addFlashAttribute("tipo", "success");

        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("mensaje", "error");
            redirectAttributes.addFlashAttribute("tipo", "error");
        }
        
        return "redirect:/clientes";
    }
}