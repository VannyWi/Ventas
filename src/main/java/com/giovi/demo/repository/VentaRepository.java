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
// 1. Suma total de ventas en un rango de fechas
    @Query("SELECT SUM(v.montoTotal) FROM Venta v WHERE v.fecha BETWEEN :inicio AND :fin")
    Double sumarVentasPorFecha(@Param("inicio") LocalDateTime inicio, @Param("fin") LocalDateTime fin);

    // 2. Contar cantidad de tickets en un rango
    @Query("SELECT COUNT(v) FROM Venta v WHERE v.fecha BETWEEN :inicio AND :fin")
    Long contarVentasPorFecha(@Param("inicio") LocalDateTime inicio, @Param("fin") LocalDateTime fin);

    // 3. Agrupar por método de pago (Global)
    @Query("SELECT v.metodoPago, COUNT(v) FROM Venta v WHERE v.fecha BETWEEN :inicio AND :fin GROUP BY v.metodoPago")
    List<Object[]> contarVentasPorMetodoPago(@Param("inicio") LocalDateTime inicio, @Param("fin") LocalDateTime fin);


    // --- CONSULTAS PARA EL VENDEDOR (ESPECÍFICAS POR USUARIO) ---

    // 4. Mis Ventas Hoy (Dinero)
    @Query("SELECT SUM(v.montoTotal) FROM Venta v WHERE v.usuario.id = :usuarioId AND v.fecha BETWEEN :inicio AND :fin")
    Double sumarVentasVendedorHoy(@Param("usuarioId") Long usuarioId, @Param("inicio") LocalDateTime inicio, @Param("fin") LocalDateTime fin);

    // 5. Mis Tickets Hoy (Cantidad)
    @Query("SELECT COUNT(v) FROM Venta v WHERE v.usuario.id = :usuarioId AND v.fecha BETWEEN :inicio AND :fin")
    Long contarVentasVendedorHoy(@Param("usuarioId") Long usuarioId, @Param("inicio") LocalDateTime inicio, @Param("fin") LocalDateTime fin);

    // 6. Mis Métodos de Pago
    @Query("SELECT v.metodoPago, COUNT(v) FROM Venta v WHERE v.usuario.id = :usuarioId AND v.fecha BETWEEN :inicio AND :fin GROUP BY v.metodoPago")
    List<Object[]> contarMetodosPagoVendedor(@Param("usuarioId") Long usuarioId, @Param("inicio") LocalDateTime inicio, @Param("fin") LocalDateTime fin);

    // 7. Mis Ventas por Fecha (Para gráfico de barras semanal)
    @Query("SELECT v.fecha, v.montoTotal FROM Venta v WHERE v.usuario.id = :usuarioId AND v.fecha BETWEEN :inicio AND :fin ORDER BY v.fecha ASC")
    List<Object[]> encontrarVentasVendedorRango(@Param("usuarioId") Long usuarioId, @Param("inicio") LocalDateTime inicio, @Param("fin") LocalDateTime fin);
}