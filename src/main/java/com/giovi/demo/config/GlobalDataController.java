package com.giovi.demo.config;

import com.giovi.demo.entity.Usuario;
import com.giovi.demo.repository.UsuarioRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.ui.Model;

@ControllerAdvice
public class GlobalDataController {

    @Autowired
    private UsuarioRepository usuarioRepository;

    @ModelAttribute
    public void addGlobalAttributes(Model model) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        
        // Si el usuario está autenticado y no es "anonymousUser"
        if (auth != null && auth.isAuthenticated() && !auth.getPrincipal().equals("anonymousUser")) {
            String username = auth.getName();
            
            // CORRECCIÓN: Usamos .orElse(null) porque findByUsername devuelve un Optional
            Usuario usuario = usuarioRepository.findByUsername(username).orElse(null);
            
            if (usuario != null) {
                // "userLog" será el objeto disponible en todo el HTML
                model.addAttribute("userLog", usuario); 
            }
        }
    }
}