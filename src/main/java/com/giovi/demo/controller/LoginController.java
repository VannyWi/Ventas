package com.giovi.demo.controller;

import com.giovi.demo.entity.Usuario;
import com.giovi.demo.repository.TiendaRepository;
import com.giovi.demo.repository.UsuarioRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class LoginController {

    @Autowired
    private UsuarioRepository usuarioRepository;
    
    @Autowired
    private TiendaRepository tiendaRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    // --- VISTA LOGIN + REGISTRO ---
    @GetMapping("/login")
    public String login(Model model) {
        // Pasamos un objeto vacío para mapear el formulario
        model.addAttribute("usuario", new Usuario());
        // Cargar las tiendas para el Select del registro
        model.addAttribute("listaTiendas", tiendaRepository.findAll()); 
        return "login";
    }

    // --- PROCESAR REGISTRO ---
    @PostMapping("/registrar")
    public String registrar(@ModelAttribute Usuario usuario, RedirectAttributes redirectAttributes) {
        try {
            // 1. Validar si el usuario YA existe usando .isPresent() (por el Optional del repo)
            if (usuarioRepository.findByUsername(usuario.getUsername()).isPresent()) {
                redirectAttributes.addFlashAttribute("error", "El nombre de usuario ya está en uso. Intente con otro.");
                return "redirect:/login"; // Redirige para recargar y mostrar alerta
            }

            // 2. Encriptar contraseña (REGLA DE NEGOCIO CRÍTICA)
            String passEncriptada = passwordEncoder.encode(usuario.getPassword());
            usuario.setPassword(passEncriptada);

            // 3. Configurar valores por defecto
            usuario.setRol("VENDEDOR"); // Rol fijo para registros públicos
            usuario.setActivo(false);   // Requiere activación del admin

            // 4. Guardar en Base de Datos
            // Nota: Spring mapea automáticamente el objeto 'Tienda' desde el <select> del HTML
            usuarioRepository.save(usuario);
            
            // 5. Mensaje de éxito
            redirectAttributes.addFlashAttribute("mensaje", "Cuenta creada exitosamente. Espere activación del Administrador.");
            
        } catch (Exception e) {
            e.printStackTrace(); // Útil para ver errores en consola
            redirectAttributes.addFlashAttribute("error", "Ocurrió un error al registrar. Verifique los datos o la conexión.");
        }
        return "redirect:/login";
    }
    
    // --- RUTAS PROTEGIDAS (Ejemplos) ---
    @GetMapping("/homeadmin")
    public String homeAdmin() {
        return "homeadmin";
    }

    @GetMapping("/homevendedores")
    public String homeVendedores() {
        return "homevendedores";
    }
}