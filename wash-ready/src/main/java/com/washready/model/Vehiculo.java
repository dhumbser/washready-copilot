package com.washready.model;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;

import com.fasterxml.jackson.annotation.JsonIgnore;

import jakarta.persistence.*;

@Entity
@Table(name = "vehiculo",
       uniqueConstraints = @UniqueConstraint(name="uk_matricula", columnNames = "matricula"),
       indexes = {
         @Index(name="idx_vehiculo_matricula", columnList = "matricula")
       })
public class Vehiculo {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "matricula", length = 20, nullable = false)
    private String matricula;

    @Column(name = "marca", length = 80, nullable = false) 
    private String marca;

    @Column(name = "modelo", length = 120, nullable = false)
    private String modelo;

    @Column(name = "color", length = 50)
    private String color;
    
    @Column(name = "plaza", length = 30)
    private String plaza;

    /** Marca de tiempo (última modificación) */
    @Column(nullable = false)
    private Instant tms;

    /** clientes **/
    @ManyToMany
    @JoinTable(
        name = "cliente_vehiculo",
        joinColumns = @JoinColumn(name = "vehiculo_id"),
        inverseJoinColumns = @JoinColumn(name = "cliente_id")
    )
    @JsonIgnore // evita recursión; expón por DTO cuando haga falta
    private Set<Cliente> clientes = new HashSet<>();

    @PrePersist @PreUpdate
    public void touch() { this.tms = Instant.now(); }

    public Long getId() { return id; }
    public String getMatricula() { return matricula; }
    public void setMatricula(String matricula) { this.matricula = matricula; }
    public String getMarca() { return marca; }
    public void setMarca(String marca) { this.marca = marca; }
    public String getModelo() { return modelo; }
    public void setModelo(String modelo) { this.modelo = modelo; }
    public String getColor() { return color; }
    public void setColor(String color) { this.color = color; }
    public String getPlaza() { return plaza; }
    public void setPlaza(String plaza) { this.plaza = plaza; }
    public Instant getTms() { return tms; }
    public void setTms(Instant tms) { this.tms = tms; }
    public Set<Cliente> getClientes() { return clientes; }
    public void setClientes(Set<Cliente> clientes) { this.clientes = clientes; }

}
