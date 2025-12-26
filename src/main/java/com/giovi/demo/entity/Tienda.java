package com.giovi.demo.entity;

import jakarta.persistence.*;
import com.fasterxml.jackson.annotation.JsonIgnore; // <--- ESTA IMPORTACIÓN ES CRUCIAL
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

    // --- AQUÍ ESTÁ LA SOLUCIÓN ---
    // Agregamos @JsonIgnore para romper el bucle infinito con Usuarios
    @OneToMany(mappedBy = "tienda")
    @JsonIgnore 
    private List<Usuario> empleados;

    // Agregamos @JsonIgnore para romper el bucle infinito con Productos
    @OneToMany(mappedBy = "tienda")
    @JsonIgnore 
    private List<Producto> inventario;

    public Tienda() {
    }

    public Tienda(Long id, String nombre, String direccion, List<Usuario> empleados, List<Producto> inventario) {
        this.id = id;
        this.nombre = nombre;
        this.direccion = direccion;
        this.empleados = empleados;
        this.inventario = inventario;
    }

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