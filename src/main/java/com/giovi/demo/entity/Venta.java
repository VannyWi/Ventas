package com.giovi.demo.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "ventas")
public class Venta {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // FECHA ÚNICA (Reemplaza a fechaHora para evitar duplicados)
    private LocalDateTime fecha; 

    private Double montoTotal; // Total final de la venta (Reemplaza a 'total')
    
    // CAMPOS DE PAGO Y DESCUENTO
    private Double descuento;
    private String metodoPago; // "Efectivo", "Yape", "Tarjeta"
    private Double montoPago;  // Cuánto entregó el cliente
    private Double vuelto;     // Cambio entregado

    // TU NÚMERO DE SERIE (Lo mantenemos)
    private String numeroVenta;

    @ManyToOne
    @JoinColumn(name = "usuario_id")
    private Usuario usuario;

    @ManyToOne
    @JoinColumn(name = "cliente_id")
    private Cliente cliente;

    @ManyToOne
    @JoinColumn(name = "tienda_id")
    private Tienda tienda;

    // RELACIÓN CON DETALLES
    // Usamos 'mappedBy="venta"' para que DetalleVenta sea quien mande la relación
    @OneToMany(mappedBy = "venta", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<DetalleVenta> detalleVenta = new ArrayList<>();

    public Venta() {
        this.fecha = LocalDateTime.now();
        this.descuento = 0.0;
        this.montoPago = 0.0;
        this.vuelto = 0.0;
        this.montoTotal = 0.0;
    }

    // --- GETTERS Y SETTERS ---

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public LocalDateTime getFecha() { return fecha; }
    public void setFecha(LocalDateTime fecha) { this.fecha = fecha; }

    public Double getMontoTotal() { return montoTotal; }
    public void setMontoTotal(Double montoTotal) { this.montoTotal = montoTotal; }

    public Double getDescuento() { return descuento; }
    public void setDescuento(Double descuento) { this.descuento = descuento; }

    public String getMetodoPago() { return metodoPago; }
    public void setMetodoPago(String metodoPago) { this.metodoPago = metodoPago; }

    public Double getMontoPago() { return montoPago; }
    public void setMontoPago(Double montoPago) { this.montoPago = montoPago; }

    public Double getVuelto() { return vuelto; }
    public void setVuelto(Double vuelto) { this.vuelto = vuelto; }

    public String getNumeroVenta() { return numeroVenta; }
    public void setNumeroVenta(String numeroVenta) { this.numeroVenta = numeroVenta; }

    public Usuario getUsuario() { return usuario; }
    public void setUsuario(Usuario usuario) { this.usuario = usuario; }

    public Cliente getCliente() { return cliente; }
    public void setCliente(Cliente cliente) { this.cliente = cliente; }

    public Tienda getTienda() { return tienda; }
    public void setTienda(Tienda tienda) { this.tienda = tienda; }

    public List<DetalleVenta> getDetalleVenta() { return detalleVenta; }
    public void setDetalleVenta(List<DetalleVenta> detalleVenta) { this.detalleVenta = detalleVenta; }

    // Método auxiliar para agregar detalles y mantener la relación bidireccional
    public void agregarDetalle(DetalleVenta detalle) {
        detalleVenta.add(detalle);
        detalle.setVenta(this);
    }

    @PrePersist
    public void antesDeGuardar() {
        if (this.fecha == null) {
            this.fecha = LocalDateTime.now();
        }
        // Si no hay número de venta, podrías generar uno temporal aquí o manejarlo en el servicio
        if (this.numeroVenta == null) {
            this.numeroVenta = "TEMP-" + System.currentTimeMillis(); 
        }
    }
}