package com.giovi.demo.entity;

import jakarta.persistence.*;
import com.fasterxml.jackson.annotation.JsonIgnore;
import java.util.List;

@Entity
@Table(name = "tiendas")
public class Tienda {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String nombre; 
    
    private String direccion;

    // --- NUEVOS CAMPOS OBLIGATORIOS ---
    @Column(nullable = false)
    private String ruc;

    @Column(nullable = false)
    private String celular;
    // ----------------------------------

    // Relaciones para romper bucles infinitos
    @OneToMany(mappedBy = "tienda")
    @JsonIgnore 
    private List<Usuario> empleados;

    @OneToMany(mappedBy = "tienda")
    @JsonIgnore 
    private List<Producto> inventario;

    public Tienda() {
    }

    public Tienda(Long id, String nombre, String direccion, String ruc, String celular, List<Usuario> empleados, List<Producto> inventario) {
        this.id = id;
        this.nombre = nombre;
        this.direccion = direccion;
        this.ruc = ruc;
        this.celular = celular;
        this.empleados = empleados;
        this.inventario = inventario;
    }

    // Getters y Setters
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getNombre() {
        return nombre;
    }

    public void setNombre(String nombre) {
        this.nombre = nombre;
    }

    public String getDireccion() {
        return direccion;
    }

    public void setDireccion(String direccion) {
        this.direccion = direccion;
    }

    public String getRuc() {
        return ruc;
    }

    public void setRuc(String ruc) {
        this.ruc = ruc;
    }

    public String getCelular() {
        return celular;
    }

    public void setCelular(String celular) {
        this.celular = celular;
    }

    public List<Usuario> getEmpleados() {
        return empleados;
    }

    public void setEmpleados(List<Usuario> empleados) {
        this.empleados = empleados;
    }

    public List<Producto> getInventario() {
        return inventario;
    }

    public void setInventario(List<Producto> inventario) {
        this.inventario = inventario;
    }
}