package com.washready.model;

import java.time.Instant;

import jakarta.persistence.*;

@Entity
@Table(name = "cliente",
        uniqueConstraints = {
    @UniqueConstraint(name = "uk_cliente_telefono", columnNames = "telefono")
   },
       indexes = {
         @Index(name = "idx_cliente_nif", columnList = "nif"),
         @Index(name = "idx_cliente_correo", columnList = "correo")
       })
public class Cliente {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "nombre", nullable = false, length = 150)
    private String nombre;

    @Column(name = "apellido",length = 150)
    private String apellido;

    @Column(name = "nif", length = 20)
    private String nif;

    @Column(name = "correo", length = 150)
    private String correo;

    @Column(name = "telefono", length = 30)
    private String telefono;

    @Column(name = "direccion", length = 255)
    private String direccion;

    @Column(name = "codigo_postal", length = 10)
    private String codigoPostal;

    @Column(name = "no_deseado", nullable = false)
    private boolean noDeseado = false;

    /** Marca de tiempo (última modificación) */
    @Column(nullable = false)
    private Instant tms;

    @PrePersist @PreUpdate
    public void touch() { this.tms = Instant.now(); }

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

    public String getApellido() {
      return apellido;
    }

    public void setApellido(String apellido) {
      this.apellido = apellido;
    }

    public String getNif() {
      return nif;
    }

    public void setNif(String nif) {
      this.nif = nif;
    }

    public String getCorreo() {
      return correo;
    }

    public void setCorreo(String correo) {
      this.correo = correo;
    }

    public String getTelefono() {
      return telefono;
    }

    public void setTelefono(String telefono) {
      this.telefono = telefono;
    }

    public String getDireccion() {
      return direccion;
    }

    public void setDireccion(String direccion) {
      this.direccion = direccion;
    }

    public String getCodigoPostal() {
      return codigoPostal;
    }

    public void setCodigoPostal(String codigoPostal) {
      this.codigoPostal = codigoPostal;
    }

    public boolean isNoDeseado() {
      return noDeseado;
    }

    public void setNoDeseado(boolean noDeseado) {
      this.noDeseado = noDeseado;
    }

    public Instant getTms() {
      return tms;
    }

    public void setTms(Instant tms) {
      this.tms = tms;
    }
    
}
