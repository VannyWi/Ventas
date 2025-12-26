package com.giovi.demo.repository;

import com.giovi.demo.entity.Producto;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Repository;
@Repository
public interface ProductoRepository extends JpaRepository<Producto, Long> {

    Optional<Producto> findByCodigoBarrasAndTienda_IdAndActivoTrue(String codigoBarras, Long tiendaId);

    @Query("SELECT p FROM Producto p WHERE p.tienda.id = :tiendaId AND p.activo = true AND (LOWER(p.nombre) LIKE LOWER(CONCAT('%', :termino, '%')) OR p.codigoBarras = :termino)")
    List<Producto> buscarPorTerminoYTienda(@Param("termino") String termino, @Param("tiendaId") Long tiendaId);
    
    // --- CAMBIO AQUÍ: Agregamos 'AND p.activo = true' a la validación ---
    @Transactional
    @Modifying
    @Query("UPDATE Producto p SET p.stock = p.stock - :cantidad WHERE p.id = :id AND p.stock >= :cantidad AND p.activo = true")
    int reducirStock(@Param("id") Long id, @Param("cantidad") Integer cantidad);
    
    List<Producto> findByStockLessThan(Integer cantidadCritica);
    List<Producto> findByStockLessThanAndActivoTrue(Integer cantidadCritica);
}