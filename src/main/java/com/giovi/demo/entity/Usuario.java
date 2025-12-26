package com.giovi.demo.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "usuarios")
public class Usuario {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private String username;
    
    private String password;
    private String rol;
    private Boolean activo;

    // --- NUEVOS CAMPOS ---
    private String nombre;
    private String apellido;
    // ---------------------

    @ManyToOne
    @JoinColumn(name = "tienda_id")
    private Tienda tienda;

    public Usuario() {
    }

    public Usuario(Long id, String username, String password, String rol, Boolean activo, String nombre, String apellido, Tienda tienda) {
        this.id = id;
        this.username = username;
        this.password = password;
        this.rol = rol;
        this.activo = activo;
        this.nombre = nombre;
        this.apellido = apellido;
        this.tienda = tienda;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getRol() {
        return rol;
    }

    public void setRol(String rol) {
        this.rol = rol;
    }

    public Boolean getActivo() {
        return activo;
    }

    public void setActivo(Boolean activo) {
        this.activo = activo;
    }

    public String getNombre() {
        return nombre;
    }

    public void setNombre(String nombre) {
        this.nombre = nombre;
    }

    public String getApellido() {
        return apellido;
    }

    public void setApellido(String apellido) {
        this.apellido = apellido;
    }

    public Tienda getTienda() {
        return tienda;
    }

    public void setTienda(Tienda tienda) {
        this.tienda = tienda;
    }
    
}
    