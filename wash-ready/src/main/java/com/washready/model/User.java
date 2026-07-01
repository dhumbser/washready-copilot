package com.washready.model;

import java.time.LocalDateTime;

import jakarta.persistence.*;

@Entity
@Table(name = "user", uniqueConstraints = {
        @UniqueConstraint(name = "uk_user_usuario", columnNames = { "usuario" })
})
public class User {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "usuario", nullable = false, length = 50)
    private String usuario;

    @Column(name = "password", nullable = false, length = 100)
    private String password;

    @Column(name = "role", nullable = false, length = 30)
    private String role = "ROLE_USER";

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "centro_id", foreignKey = @ForeignKey(name = "fk_user_centro"))
    private CentroTrabajo centroTrabajo;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "empresa_id", foreignKey = @ForeignKey(name = "fk_user_empresa"))
    private Empresa empresa;

    @Column(name = "disabled_from")
    private LocalDateTime disabledFrom;

    public User() {
    }

    public User(String usuario, String password, String role, CentroTrabajo centroTrabajo) {
        this.usuario = usuario;
        this.password = password;
        this.role = role;
        this.centroTrabajo = centroTrabajo;
        this.empresa = (centroTrabajo != null ? centroTrabajo.getEmpresa() : null);
    }

    public User(String usuario, String password, String role, CentroTrabajo centroTrabajo, Empresa empresa) {
        this.usuario = usuario;
        this.password = password;
        this.role = role;
        this.centroTrabajo = centroTrabajo;
        this.empresa = empresa;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getUsuario() {
        return usuario;
    }

    public void setUsuario(String usuario) {
        this.usuario = usuario;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public CentroTrabajo getCentroTrabajo() {
        return centroTrabajo;
    }

    public void setCentroTrabajo(CentroTrabajo centroTrabajo) {
        this.centroTrabajo = centroTrabajo;
    }

    public Empresa getEmpresa() {
        return empresa;
    }

    public void setEmpresa(Empresa empresa) {
        this.empresa = empresa;
    }

    public LocalDateTime getDisabledFrom() {
        return disabledFrom;
    }

    public void setDisabledFrom(LocalDateTime disabledFrom) {
        this.disabledFrom = disabledFrom;
    }

    public boolean isDisabledAt(LocalDateTime dateTime) {
        return disabledFrom != null && !disabledFrom.isAfter(dateTime);
    }

    public boolean isDisabledNow() {
        return isDisabledAt(LocalDateTime.now());
    }
    
}
