package com.giovi.demo.repository;

import com.giovi.demo.entity.Usuario;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;

@Repository
public interface UsuarioRepository extends JpaRepository<Usuario, Long> {
    // Devuelve un Optional para evitar NullPointerExceptions
    Optional<Usuario> findByUsername(String username);
}