package com.giovi.demo.controller;

import com.giovi.demo.entity.Usuario;
import com.giovi.demo.repository.UsuarioRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/perfil")
public class PerfilController {

    @Autowired private UsuarioRepository usuarioRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    @GetMapping
    public String verPerfil(Model model) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        // CORRECCIÓN: Usar .orElse(null)
        Usuario usuario = usuarioRepository.findByUsername(auth.getName()).orElse(null);
        model.addAttribute("usuario", usuario);
        return "perfil";
    }

    @PostMapping("/actualizar")
    public String actualizarPerfil(
            @RequestParam String currentPassword,
            @RequestParam String newUsername,
            @RequestParam(required = false) String newPassword,
            RedirectAttributes redirectAttributes,
            HttpServletRequest request) { 
        
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            // CORRECCIÓN: Usar .orElse(null)
            Usuario usuario = usuarioRepository.findByUsername(auth.getName()).orElse(null);
            
            if (usuario == null) {
                return "redirect:/login"; // Seguridad extra por si acaso
            }

            // 1. Validar contraseña actual
            if (!passwordEncoder.matches(currentPassword, usuario.getPassword())) {
                redirectAttributes.addFlashAttribute("mensaje", "error_password");
                return "redirect:/perfil";
            }

            boolean cambioCredenciales = false;

            // 2. Validar cambio de Username
            if (!usuario.getUsername().equals(newUsername)) {
                // CORRECCIÓN: Usar .isPresent() para ver si existe otro con ese nombre
                if (usuarioRepository.findByUsername(newUsername).isPresent()) {
                    redirectAttributes.addFlashAttribute("mensaje", "usuario_duplicado");
                    return "redirect:/perfil";
                }
                usuario.setUsername(newUsername);
                cambioCredenciales = true; 
            }

            // 3. Validar cambio de Password
            if (newPassword != null && !newPassword.trim().isEmpty()) {
                usuario.setPassword(passwordEncoder.encode(newPassword));
                cambioCredenciales = true; 
            }

            usuarioRepository.save(usuario);

            // 4. Si cambiaron credenciales, forzar logout
            if (cambioCredenciales) {
                request.logout(); 
                return "redirect:/login?logout"; 
            }

            redirectAttributes.addFlashAttribute("mensaje", "actualizado");
            
        } catch (Exception e) {
            e.printStackTrace();
            redirectAttributes.addFlashAttribute("mensaje", "error");
        }

        return "redirect:/perfil";
    }
}