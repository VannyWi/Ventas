package com.giovi.demo.service;

import com.giovi.demo.entity.Usuario;
import com.giovi.demo.repository.UsuarioRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
public class UserDetailsServiceImpl implements UserDetailsService {

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        // Buscamos el usuario y lanzamos excepción si no existe (usando el Optional)
        Usuario usuario = usuarioRepository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("Usuario no encontrado: " + username));

        // Validación extra: Si el usuario no está activo, podrías impedir el login aquí
        // o dejar que Spring Security maneje la autenticación básica primero.
        
        return User.builder()
                .username(usuario.getUsername())
                .password(usuario.getPassword())
                .roles(usuario.getRol()) // Spring espera el rol sin el prefijo ROLE_ si usas .roles()
                .disabled(!usuario.getActivo()) // Integra la validación de 'activo'
                .build();
    }
}