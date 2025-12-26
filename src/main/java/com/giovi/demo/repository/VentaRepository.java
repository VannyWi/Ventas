package com.giovi.demo.repository;

import com.giovi.demo.entity.Venta;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface VentaRepository extends JpaRepository<Venta, Long> {
    
    // Trae el código de venta más alto registrado en el sistema
    @Query("SELECT MAX(v.numeroVenta) FROM Venta v")
    String obtenerUltimoNumeroVenta();
    // 1. Total Dinero Hoy
    @Query("SELECT SUM(v.montoTotal) FROM Venta v WHERE v.fecha BETWEEN :inicio AND :fin")
    Double sumarVentasPorFecha(@Param("inicio") LocalDateTime inicio, @Param("fin") LocalDateTime fin);

    // 2. Cantidad Tickets Hoy
    @Query("SELECT COUNT(v) FROM Venta v WHERE v.fecha BETWEEN :inicio AND :fin")
    Long contarVentasPorFecha(@Param("inicio") LocalDateTime inicio, @Param("fin") LocalDateTime fin);

    // 3. Ventas por Método de Pago (Para Gráfico Pastel)
    // Retorna una lista de arrays [Metodo, Cantidad]
    @Query("SELECT v.metodoPago, COUNT(v) FROM Venta v WHERE v.fecha BETWEEN :inicio AND :fin GROUP BY v.metodoPago")
    List<Object[]> contarVentasPorMetodoPago(@Param("inicio") LocalDateTime inicio, @Param("fin") LocalDateTime fin);
}