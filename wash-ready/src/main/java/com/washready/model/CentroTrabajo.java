package com.washready.model;

import java.util.List;

import jakarta.persistence.*;

@Entity
@Table(name = "centro_trabajo", uniqueConstraints = {
        @UniqueConstraint(name = "uk_centro_empresa_nombre", columnNames = {"empresa_id", "nombre"})
})
public class CentroTrabajo {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "empresa_id", nullable = false, foreignKey = @ForeignKey(name = "fk_centro_empresa"))
    private Empresa empresa;

    @Column(name = "nombre", nullable = false, length = 150)
    private String nombre;

    @Column(name = "direccion", length = 255)
    private String direccion;

    @Column(name = "ciudad", length = 100)
    private String ciudad;

    @Column(name = "codigo_postal", length = 10)
    private String codigoPostal;

    @Column(name = "correo", length = 150)
    private String correo;

    @Column(name = "telefono", length = 30)
    private String telefono;

    @Column(name = "max_devices")
    private Integer maxDevices;

    @Column(name = "mostrar_logo_ticket")
    private Boolean mostrarLogoTicket;

    @Column(name = "transaccional", nullable = false, columnDefinition = "TINYINT(1) NOT NULL DEFAULT 1")
    private boolean transaccional = true;

    // 1:N → un CentroTrabajo tiene varios users
    @OneToMany(mappedBy = "centroTrabajo")
    private List<User> users;

    public CentroTrabajo() {}

    public CentroTrabajo(String nombre, String direccion, String ciudad, String codigoPostal, String correo, String telefono, Empresa empresa) {
        this.nombre = nombre;
        this.direccion = direccion;
        this.ciudad = ciudad;
        this.codigoPostal = codigoPostal;
        this.correo = correo;
        this.telefono = telefono;
        this.empresa = empresa;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Empresa getEmpresa() {
        return empresa;
    }

    public void setEmpresa(Empresa empresa) {
        this.empresa = empresa;
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

    public String getCiudad() {
        return ciudad;
    }

    public void setCiudad(String ciudad) {
        this.ciudad = ciudad;
    }

    public String getCodigoPostal() {
        return codigoPostal;
    }

    public void setCodigoPostal(String codigoPostal) {
        this.codigoPostal = codigoPostal;
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

    public Integer getMaxDevices() {
        return maxDevices;
    }

    public void setMaxDevices(Integer maxDevices) {
        this.maxDevices = maxDevices;
    }

    public Boolean getMostrarLogoTicket() {
        return mostrarLogoTicket;
    }

    public void setMostrarLogoTicket(Boolean mostrarLogoTicket) {
        this.mostrarLogoTicket = mostrarLogoTicket;
    }

    public boolean isTransaccional() {
        return transaccional;
    }

    public void setTransaccional(boolean transaccional) {
        this.transaccional = transaccional;
    }

    public List<User> getUsers() {
        return users;
    }

    public void setUsers(List<User> users) {
        this.users = users;
    }
    
}
