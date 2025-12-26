package com.giovi.demo.repository;

import com.giovi.demo.entity.DetalleVenta;
import org.springframework.data.domain.Pageable; // IMPORTANTE: No olvidar este import
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface DetalleVentaRepository extends JpaRepository<DetalleVenta, Long> {

   @Query("SELECT d.producto.nombre, SUM(d.cantidad) as total " +
           "FROM DetalleVenta d JOIN d.venta v " + 
           "WHERE v.fecha BETWEEN :inicio AND :fin " +
           "GROUP BY d.producto.nombre " +
           "ORDER BY total DESC")
    List<Object[]> encontrarTopProductosVendidos(@Param("inicio") LocalDateTime inicio, 
                                                 @Param("fin") LocalDateTime fin, 
                                                 Pageable pageable);

    // Consulta para categorías por fecha
    @Query("SELECT d.producto.categoria.nombre, SUM(d.subtotal) " +
           "FROM DetalleVenta d " +
           "WHERE d.venta.fecha BETWEEN :inicio AND :fin " +
           "GROUP BY d.producto.categoria.nombre")
    List<Object[]> encontrarVentasPorCategoria(@Param("inicio") LocalDateTime inicio, 
                                               @Param("fin") LocalDateTime fin);
}