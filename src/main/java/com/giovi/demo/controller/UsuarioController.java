package com.giovi.demo.controller;

import com.giovi.demo.entity.Usuario;
import com.giovi.demo.repository.TiendaRepository;
import com.giovi.demo.repository.UsuarioRepository;
import jakarta.servlet.http.HttpServletRequest; 
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/usuarios")
public class UsuarioController {

    @Autowired private UsuarioRepository usuarioRepository;
    @Autowired private TiendaRepository tiendaRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    // Listado de usuarios
    @GetMapping
    public String index(Model model) {
        model.addAttribute("listaUsuarios", usuarioRepository.findAll());
        model.addAttribute("listaTiendas", tiendaRepository.findAll()); 
        model.addAttribute("usuario", new Usuario()); 
        return "usuarios/index";
    }

    // Guardar (Crear o Editar)
    @PostMapping("/guardar")
    public String guardar(@ModelAttribute Usuario usuario, 
                          RedirectAttributes redirectAttributes, 
                          HttpServletRequest request) {
        try {
            boolean esEdicion = (usuario.getId() != null);
            boolean forzarLogout = false; 

            // 1. VALIDACIÓN: Nombre y Apellido solo letras y espacios
            String regexLetras = "^[a-zA-ZáéíóúÁÉÍÓÚñÑ\\s]+$";
            if (!usuario.getNombre().matches(regexLetras) || !usuario.getApellido().matches(regexLetras)) {
                redirectAttributes.addFlashAttribute("mensaje", "texto_invalido");
                redirectAttributes.addFlashAttribute("tipo", "warning");
                return "redirect:/usuarios";
            }
            
            // 2. VALIDACIÓN: Usuario duplicado
            // CORRECCIÓN AQUÍ: Usamos .orElse(null) porque ahora devuelve Optional
            Usuario existente = usuarioRepository.findByUsername(usuario.getUsername()).orElse(null);
            
            if (existente != null && (usuario.getId() == null || !existente.getId().equals(usuario.getId()))) {
                redirectAttributes.addFlashAttribute("mensaje", "duplicado");
                redirectAttributes.addFlashAttribute("tipo", "warning");
                return "redirect:/usuarios";
            }

            if (esEdicion) {
                // --- LÓGICA DE EDICIÓN ---
                Usuario actual = usuarioRepository.findById(usuario.getId()).orElse(null);
                
                if (actual != null) {
                    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
                    String usuarioLogueado = auth.getName();

                    if (actual.getUsername().equals(usuarioLogueado)) {
                        if (!actual.getUsername().equals(usuario.getUsername())) forzarLogout = true;
                        if (usuario.getPassword() != null && !usuario.getPassword().isEmpty()) forzarLogout = true;
                        if (!actual.getRol().equals(usuario.getRol())) forzarLogout = true;
                    }

                    if (usuario.getPassword() == null || usuario.getPassword().isEmpty()) {
                        usuario.setPassword(actual.getPassword());
                    } else {
                        usuario.setPassword(passwordEncoder.encode(usuario.getPassword()));
                    }
                    
                    if(usuario.getActivo() == null) {
                        usuario.setActivo(actual.getActivo());
                    }
                }
            } else {
                // --- LÓGICA DE CREACIÓN (NUEVO) ---
                usuario.setPassword(passwordEncoder.encode(usuario.getPassword()));
                usuario.setActivo(true); 
            }

            usuarioRepository.save(usuario);

            if (forzarLogout) {
                request.logout();
                return "redirect:/login?logout";
            }

            redirectAttributes.addFlashAttribute("mensaje", esEdicion ? "editado" : "guardado");
            redirectAttributes.addFlashAttribute("tipo", "success");

        } catch (Exception e) {
            e.printStackTrace(); // Bueno para depurar en consola
            redirectAttributes.addFlashAttribute("mensaje", "error");
            redirectAttributes.addFlashAttribute("tipo", "error");
        }
        return "redirect:/usuarios";
    }

    @GetMapping("/eliminar/{id}")
    public String desactivar(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        try {
            Usuario u = usuarioRepository.findById(id).orElse(null);
            if (u != null) {
                u.setActivo(false);
                usuarioRepository.save(u);
                redirectAttributes.addFlashAttribute("mensaje", "desactivado");
                redirectAttributes.addFlashAttribute("tipo", "warning");
            }
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("mensaje", "error");
        }
        return "redirect:/usuarios";
    }

    @GetMapping("/reactivar/{id}")
    public String reactivar(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        try {
            Usuario u = usuarioRepository.findById(id).orElse(null);
            if (u != null) {
                u.setActivo(true);
                usuarioRepository.save(u);
                redirectAttributes.addFlashAttribute("mensaje", "reactivado");
                redirectAttributes.addFlashAttribute("tipo", "success");
            }
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("mensaje", "error");
        }
        return "redirect:/usuarios";
    }
}