
package com.giovi.demo.entity;

import jakarta.persistence.*;
import java.util.List;

@Entity
@Table(name = "clientes")
public class Cliente {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private String dni; 

    @Column(nullable = false)
    private String nombre;

    @OneToMany(mappedBy = "cliente")
    private List<Venta> compras;

    public Cliente() {
    }

    public Cliente(Long id, String dni, String nombre, List<Venta> compras) {
        this.id = id;
        this.dni = dni;
        this.nombre = nombre;
        this.compras = compras;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getDni() {
        return dni;
    }

    public void setDni(String dni) {
        this.dni = dni;
    }

    public String getNombre() {
        return nombre;
    }

    public void setNombre(String nombre) {
        this.nombre = nombre;
    }

    public List<Venta> getCompras() {
        return compras;
    }

    public void setCompras(List<Venta> compras) {
        this.compras = compras;
    }
    
    
}