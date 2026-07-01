package com.washready.model;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;

import com.fasterxml.jackson.annotation.JsonIgnore;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;

@Entity
@Table(name = "ticket_detalle")
public class TicketDetalle {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "ticket_id", nullable = false)
    private Ticket ticket;

    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "servicio_id", foreignKey = @ForeignKey(name = "fk_det_servicio"))
    private Servicio servicio;

    @NotBlank
    @Column(name = "descripcion_servicio", nullable = false, length = 255)
    private String descripcionServicio;

    @Column(name = "cantidad", nullable = false)
    private Integer cantidad;

    @Column(name = "precio", nullable = false, precision = 10, scale = 2)
    private BigDecimal precio; // precio unitario en el momento de la venta

    @Column(name = "tms", nullable = false)
    private LocalDateTime tms;

    @PrePersist
    public void prePersist() {
        if (tms == null)
            tms = LocalDateTime.now();

        if ((descripcionServicio == null || descripcionServicio.isBlank()) && servicio != null) {
            descripcionServicio = servicio.getDescripcion();
        }


        if (descripcionServicio == null || descripcionServicio.isBlank()) {
            throw new IllegalStateException("TicketDetalle.descripcionServicio es obligatoria");
        }

    }

    @PreUpdate
    public void preUpdate() {
        tms = LocalDateTime.now();
    }

    // getters / setters
    public Long getId() {
        return id;
    }

    public Ticket getTicket() {
        return ticket;
    }

    public void setTicket(Ticket ticket) {
        this.ticket = ticket;
    }

    public Servicio getServicio() {
        return servicio;
    }

    public void setServicio(Servicio servicio) {
        this.servicio = servicio;
    }

    public Integer getCantidad() {
        return cantidad;
    }

    public void setCantidad(Integer cantidad) {
        this.cantidad = cantidad;
    }

    public BigDecimal getPrecio() {
        return precio;
    }

    public void setPrecio(BigDecimal precio) {
        this.precio = precio;
    }

    @JsonIgnore
    public BigDecimal getPrecioParaTicket() {
        BigDecimal base = precio != null
                ? precio
                : (servicio != null && servicio.getImporte() != null ? servicio.getImporte() : BigDecimal.ZERO);

        if (servicio != null && servicio.isImporteCeroEnTicket()) {
            return BigDecimal.ZERO.setScale(2);
        }

        return base.setScale(2, RoundingMode.HALF_UP);
    }

    public LocalDateTime getTms() {
        return tms;
    }

    public void setTms(LocalDateTime tms) {
        this.tms = tms;
    }

    public String getDescripcionServicio() {
        return descripcionServicio;
    }

    public void setDescripcionServicio(String descripcionServicio) {
        this.descripcionServicio = descripcionServicio;
    }

}
