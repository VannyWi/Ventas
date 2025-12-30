package com.giovi.demo.repository;

import com.giovi.demo.entity.DetalleVenta;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface DetalleVentaRepository extends JpaRepository<DetalleVenta, Long> {

    // 1. Top Productos (FILTRADO por Tienda/Usuario)
    @Query("SELECT d.producto.nombre, SUM(d.cantidad) as total " +
           "FROM DetalleVenta d JOIN d.venta v " + 
           "WHERE v.fecha BETWEEN :inicio AND :fin " +
           "AND (:tiendaId IS NULL OR v.usuario.tienda.id = :tiendaId) " +
           "AND (:usuarioId IS NULL OR v.usuario.id = :usuarioId) " +
           "GROUP BY d.producto.nombre " +
           "ORDER BY total DESC")
    List<Object[]> encontrarTopProductosFiltrado(
            @Param("inicio") LocalDateTime inicio, 
            @Param("fin") LocalDateTime fin,
            @Param("tiendaId") Long tiendaId,
            @Param("usuarioId") Long usuarioId,
            Pageable pageable);

    // 2. Categorías (FILTRADO por Tienda/Usuario)
    @Query("SELECT d.producto.categoria.nombre, SUM(d.subtotal) " +
           "FROM DetalleVenta d JOIN d.venta v " + 
           "WHERE v.fecha BETWEEN :inicio AND :fin " +
           "AND (:tiendaId IS NULL OR v.usuario.tienda.id = :tiendaId) " +
           "AND (:usuarioId IS NULL OR v.usuario.id = :usuarioId) " +
           "GROUP BY d.producto.categoria.nombre")
    List<Object[]> encontrarCategoriasFiltrado(
            @Param("inicio") LocalDateTime inicio, 
            @Param("fin") LocalDateTime fin,
            @Param("tiendaId") Long tiendaId,
            @Param("usuarioId") Long usuarioId);

    // --- MÉTODOS LEGACY (Para no romper nada anterior) ---
    @Query("SELECT d.producto.nombre, SUM(d.cantidad) as total FROM DetalleVenta d JOIN d.venta v WHERE v.fecha BETWEEN :inicio AND :fin GROUP BY d.producto.nombre ORDER BY total DESC")
    List<Object[]> encontrarTopProductosVendidos(@Param("inicio") LocalDateTime inicio, @Param("fin") LocalDateTime fin, Pageable pageable);
    
    @Query("SELECT d.producto.categoria.nombre, SUM(d.subtotal) FROM DetalleVenta d JOIN d.venta v WHERE v.fecha BETWEEN :inicio AND :fin GROUP BY d.producto.categoria.nombre")
    List<Object[]> encontrarVentasPorCategoria(@Param("inicio") LocalDateTime inicio, @Param("fin") LocalDateTime fin);
}