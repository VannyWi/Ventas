package com.giovi.demo.repository;

import com.giovi.demo.entity.Categoria;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CategoriaRepository extends JpaRepository<Categoria, Long> {
    
    // Buscar si existe el nombre (ignorando mayúsculas/minúsculas)
    boolean existsByNombreIgnoreCase(String nombre);
    
    // Buscar el objeto completo por nombre (para validaciones de edición)
    Categoria findByNombreIgnoreCase(String nombre);
}