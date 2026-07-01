package com.washready.model;

import java.time.LocalDateTime;

import jakarta.persistence.*;

@Entity
@Table(name = "cliente_no_deseado_solicitud")
public class ClienteNoDeseadoSolicitud {

    public enum Estado { PENDIENTE, APROBADA, RECHAZADA, EXPIRADA }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "cliente_id", nullable = false, foreignKey = @ForeignKey(name = "fk_cnd_cliente"))
    private Cliente cliente;

    @Column(name = "centro_id", nullable = false)
    private Long centroId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "estado", nullable = false, length = 16)
    private Estado estado = Estado.PENDIENTE;

    @Column(name = "motivo", nullable = false, length = 300)
    private String motivo;

    @Column(name = "token", nullable = false, unique = true, length = 64)
    private String token;

    @Column(name = "creado_at", nullable = false)
    private LocalDateTime creadoAt;

    @Column(name = "expira_at", nullable = false)
    private LocalDateTime expiraAt;

    @Column(name = "resuelto_at")
    private LocalDateTime resueltoAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Cliente getCliente() { return cliente; }
    public void setCliente(Cliente cliente) { this.cliente = cliente; }

    public Long getCentroId() { return centroId; }
    public void setCentroId(Long centroId) { this.centroId = centroId; }

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }

    public Estado getEstado() { return estado; }
    public void setEstado(Estado estado) { this.estado = estado; }

    public String getMotivo() { return motivo; }
    public void setMotivo(String motivo) { this.motivo = motivo; }

    public String getToken() { return token; }
    public void setToken(String token) { this.token = token; }

    public LocalDateTime getCreadoAt() { return creadoAt; }
    public void setCreadoAt(LocalDateTime creadoAt) { this.creadoAt = creadoAt; }

    public LocalDateTime getExpiraAt() { return expiraAt; }
    public void setExpiraAt(LocalDateTime expiraAt) { this.expiraAt = expiraAt; }

    public LocalDateTime getResueltoAt() { return resueltoAt; }
    public void setResueltoAt(LocalDateTime resueltoAt) { this.resueltoAt = resueltoAt; }

}
