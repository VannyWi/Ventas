package com.giovi.demo.repository;

import com.giovi.demo.entity.Tienda;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.Optional;

public interface TiendaRepository extends JpaRepository<Tienda, Long> {
    
    // Método para obtener la tienda bloqueándola momentáneamente (Pessimistic Lock)
    // Esto evita que dos ventas obtengan el mismo número a la vez
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT t FROM Tienda t WHERE t.id = :id")
    Optional<Tienda> obtenerTiendaConBloqueo(@Param("id") Long id);
}