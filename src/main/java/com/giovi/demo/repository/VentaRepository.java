package com.giovi.demo.repository;

import com.giovi.demo.entity.Venta;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface VentaRepository extends JpaRepository<Venta, Long> {
    
    // Trae el código de venta más alto registrado en el sistema
    @Query("SELECT MAX(v.numeroVenta) FROM Venta v")
    String obtenerUltimoNumeroVenta();
}