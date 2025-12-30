package com.giovi.demo.repository;

import com.giovi.demo.entity.Venta;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface VentaRepository extends JpaRepository<Venta, Long> {

    // --- IMPORTANTE: MÉTODO RESTAURADO PARA QUE FUNCIONE VentaController ---
    @Query("SELECT MAX(v.numeroVenta) FROM Venta v")
    String obtenerUltimoNumeroVenta();
    // -----------------------------------------------------------------------

    // ==========================================
    // SECCIÓN ADMINISTRADOR (FILTROS GLOBALES)
    // ==========================================

    // 1. KPI: Ventas Totales (Solo rango de fechas)
    @Query("SELECT SUM(v.montoTotal) FROM Venta v WHERE v.fecha BETWEEN :inicio AND :fin")
    Double sumarVentasPorFecha(@Param("inicio") LocalDateTime inicio, @Param("fin") LocalDateTime fin);

    // 2. KPI: Cantidad de Tickets (Solo rango de fechas)
    @Query("SELECT COUNT(v) FROM Venta v WHERE v.fecha BETWEEN :inicio AND :fin")
    Long contarVentasPorFecha(@Param("inicio") LocalDateTime inicio, @Param("fin") LocalDateTime fin);

    // 3. TARJETAS PAGOS: Suma de Dinero (Filtrado por Fecha, Tienda y Usuario)
    @Query("SELECT v.metodoPago, SUM(v.montoTotal) FROM Venta v " +
           "WHERE v.fecha BETWEEN :inicio AND :fin " +
           "AND (:tiendaId IS NULL OR v.usuario.tienda.id = :tiendaId) " +
           "AND (:usuarioId IS NULL OR v.usuario.id = :usuarioId) " +
           "GROUP BY v.metodoPago")
    List<Object[]> sumarVentasPorMetodoPagoFiltrado(
            @Param("inicio") LocalDateTime inicio, 
            @Param("fin") LocalDateTime fin,
            @Param("tiendaId") Long tiendaId,
            @Param("usuarioId") Long usuarioId);

    // 4. GRÁFICO: Ganancias por Vendedor y Tienda
    @Query("SELECT v.usuario.nombre, v.usuario.tienda.nombre, SUM(v.montoTotal) FROM Venta v " +
           "WHERE v.fecha BETWEEN :inicio AND :fin " +
           "AND (:tiendaId IS NULL OR v.usuario.tienda.id = :tiendaId) " +
           "AND (:usuarioId IS NULL OR v.usuario.id = :usuarioId) " +
           "GROUP BY v.usuario.nombre, v.usuario.tienda.nombre " +
           "ORDER BY SUM(v.montoTotal) DESC")
    List<Object[]> encontrarVentasPorVendedorYTienda(
            @Param("inicio") LocalDateTime inicio, 
            @Param("fin") LocalDateTime fin,
            @Param("tiendaId") Long tiendaId,
            @Param("usuarioId") Long usuarioId);

    // ==========================================
    // SECCIÓN VENDEDOR (ESPECÍFICO USUARIO)
    // ==========================================

    @Query("SELECT SUM(v.montoTotal) FROM Venta v WHERE v.usuario.id = :usuarioId AND v.fecha BETWEEN :inicio AND :fin")
    Double sumarVentasVendedorHoy(@Param("usuarioId") Long usuarioId, @Param("inicio") LocalDateTime inicio, @Param("fin") LocalDateTime fin);

    @Query("SELECT COUNT(v) FROM Venta v WHERE v.usuario.id = :usuarioId AND v.fecha BETWEEN :inicio AND :fin")
    Long contarVentasVendedorHoy(@Param("usuarioId") Long usuarioId, @Param("inicio") LocalDateTime inicio, @Param("fin") LocalDateTime fin);

    // Suma de dinero por método de pago para el vendedor
    @Query("SELECT v.metodoPago, SUM(v.montoTotal) FROM Venta v WHERE v.usuario.id = :usuarioId AND v.fecha BETWEEN :inicio AND :fin GROUP BY v.metodoPago")
    List<Object[]> sumarMetodosPagoVendedor(@Param("usuarioId") Long usuarioId, @Param("inicio") LocalDateTime inicio, @Param("fin") LocalDateTime fin);

    // Conteo de transacciones
    @Query("SELECT v.metodoPago, COUNT(v) FROM Venta v WHERE v.usuario.id = :usuarioId AND v.fecha BETWEEN :inicio AND :fin GROUP BY v.metodoPago")
    List<Object[]> contarMetodosPagoVendedor(@Param("usuarioId") Long usuarioId, @Param("inicio") LocalDateTime inicio, @Param("fin") LocalDateTime fin);

    // Evolución diaria para el vendedor
    @Query("SELECT v.fecha, v.montoTotal FROM Venta v WHERE v.usuario.id = :usuarioId AND v.fecha BETWEEN :inicio AND :fin ORDER BY v.fecha ASC")
    List<Object[]> encontrarVentasVendedorRango(@Param("usuarioId") Long usuarioId, @Param("inicio") LocalDateTime inicio, @Param("fin") LocalDateTime fin);

    // Historial de ventas (Lista)
    List<Venta> findByUsuarioIdAndFechaBetweenOrderByFechaDesc(Long usuarioId, LocalDateTime inicio, LocalDateTime fin);
}