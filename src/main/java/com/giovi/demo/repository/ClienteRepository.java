package com.giovi.demo.repository;

import com.giovi.demo.entity.Cliente;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface ClienteRepository extends JpaRepository<Cliente, Long> {
    
    Cliente findByDni(String dni);

    // Búsqueda para el autocompletado (por nombre o dni)
    List<Cliente> findByNombreContainingIgnoreCaseOrDniContaining(String nombre, String dni);
}