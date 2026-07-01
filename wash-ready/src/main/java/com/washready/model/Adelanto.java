package com.washready.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import jakarta.persistence.*;

@Entity
@Table(name = "adelanto")
public class Adelanto {

    public enum Estado { PENDIENTE, ACEPTADO, RECHAZADO, CANCELADO }

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name="centro_id")
    private CentroTrabajo centro;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name="solicitado_por", nullable = false)
    private User user;

    @Column(name = "operario_nif", nullable = false, length = 16)
    private String operarioNif;

    @Column(name = "operario_nombre", nullable = false, length = 120)
    private String operarioNombre;

    @Column(name = "operario_apellido", nullable = false, length = 120)
    private String operarioApellido;

    @Column(name="importe", nullable = false, precision = 10, scale = 2)
    private BigDecimal importe;

    @Enumerated(EnumType.STRING)
    @Column(name="estado", nullable = false, length = 20)
    private Estado estado;

    @Column(name = "decision_token", length = 120)
    private String decisionToken;

    @Column(name = "decision_expira_at")
    private LocalDateTime decisionExpiraAt;

    @Column(name="creado_at", nullable = false)
    private LocalDateTime creadoAt;

    @Column(name="decidido_at")
    private LocalDateTime decididoAt;

    @Column(name = "motivo_rechazo", length = 300)
    private String motivoRechazo;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public CentroTrabajo getCentro() {
        return centro;
    }

    public void setCentro(CentroTrabajo centro) {
        this.centro = centro;
    }

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
    }

    public String getOperarioNif() {
        return operarioNif;
    }

    public void setOperarioNif(String operarioNif) {
        this.operarioNif = operarioNif;
    }

    public String getOperarioNombre() {
        return operarioNombre;
    }

    public void setOperarioNombre(String operarioNombre) {
        this.operarioNombre = operarioNombre;
    }

    public String getOperarioApellido() {
        return operarioApellido;
    }

    public void setOperarioApellido(String operarioApellido) {
        this.operarioApellido = operarioApellido;
    }

    public BigDecimal getImporte() {
        return importe;
    }

    public void setImporte(BigDecimal importe) {
        this.importe = importe;
    }

    public Estado getEstado() {
        return estado;
    }

    public void setEstado(Estado estado) {
        this.estado = estado;
    }

    public String getDecisionToken() {
        return decisionToken;
    }

    public void setDecisionToken(String decisionToken) {
        this.decisionToken = decisionToken;
    }

    public LocalDateTime getDecisionExpiraAt() {
        return decisionExpiraAt;
    }

    public void setDecisionExpiraAt(LocalDateTime decisionExpiraAt) {
        this.decisionExpiraAt = decisionExpiraAt;
    }

    public LocalDateTime getCreadoAt() {
        return creadoAt;
    }

    public void setCreadoAt(LocalDateTime creadoAt) {
        this.creadoAt = creadoAt;
    }

    public LocalDateTime getDecididoAt() {
        return decididoAt;
    }

    public void setDecididoAt(LocalDateTime decididoAt) {
        this.decididoAt = decididoAt;
    }

    public String getMotivoRechazo() {
        return motivoRechazo;
    }

    public void setMotivoRechazo(String motivoRechazo) {
        this.motivoRechazo = motivoRechazo;
    }

}
