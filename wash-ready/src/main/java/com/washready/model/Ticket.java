package com.washready.model;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import jakarta.persistence.*;

@Entity
@Table(name = "ticket")
public class Ticket {

    /* ===== Estado  ===== */
    public enum Estado {
        PTE_PAGO("pte. de pago"),
        PAGADO("pagado"),
        CERRADO("cerrado"),
        ANULADO("anulado");

        private final String label;

        Estado(String label) {
            this.label = label;
        }

        @JsonValue
        public String getLabel() {
            return label;
        }

        @JsonCreator
        public static Estado fromJson(String value) {
            if (value == null)
                return null;
            String v = value.trim().toLowerCase();
            for (Estado e : values()) {
                if (e.label.equalsIgnoreCase(v) || e.name().equalsIgnoreCase(v))
                    return e;
            }
            throw new IllegalArgumentException("Estado inválido: " + value);
        }
    }

    /* ===== Método de pago ===== */
    public enum MetodoPago {
        TARJETA("tarjeta"),
        EFECTIVO("efectivo"),
        BIZUM("bizum"),
        BONO("bono"),
        TRANSFERENCIA("transferencia"),
        OTRO("otro");

        private final String label;

        MetodoPago(String label) {
            this.label = label;
        }

        @JsonValue
        public String getLabel() {
            return label;
        }

        @JsonCreator
        public static MetodoPago fromJson(String value) {
            if (value == null)
                return null;
            String v = value.trim().toLowerCase();
            for (MetodoPago m : values()) {
                if (m.label.equalsIgnoreCase(v) || m.name().equalsIgnoreCase(v))
                    return m;
            }
            throw new IllegalArgumentException("Método de pago inválido: " + value);
        }
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "estado", nullable = false, length = 20)
    private Estado estado;
    
   @Column(name = "fecha", nullable = false)
    private Instant fecha;

    @Column(name = "referencia", length = 32, unique = true)
    private String referencia;

    @Column(name = "plaza", length = 30)
    private String plaza;

    /* ===== NUEVO campo persistido ===== */
    @Column(name = "total_sin_iva", nullable = false, precision = 10, scale = 2)
    private BigDecimal totalSinIva;

    @Column(name = "iva", nullable = false, precision = 5, scale = 2)
    private BigDecimal iva;

    @Column(name = "total", nullable = false, precision = 10, scale = 2)
    private BigDecimal total;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "cliente_id", nullable = false)
    private Cliente cliente;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "vehiculo_id", nullable = false)
    private Vehiculo vehiculo;

    @Column(name = "tms", nullable = false)
    private LocalDateTime tms;

    @Enumerated(EnumType.STRING)
    @Column(name = "metodo_pago", length = 20)
    private MetodoPago metodoPago;

    @Column(name = "comentarios", length = 500)
    private String comentarios;

    @Column(name = "bono_motivo", length = 300)
    private String bonoMotivo;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "usuario_id", nullable = false)
    private User usuario;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "centro_id", nullable = false)
    private CentroTrabajo centro;
    
    @OneToMany(mappedBy = "ticket", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<TicketDetalle> detalles = new ArrayList<>();

    public void addDetalle(TicketDetalle d) {
        d.setTicket(this);
        detalles.add(d);
    }

    public void removeDetalle(TicketDetalle d) {
        detalles.remove(d);
        d.setTicket(null);
    }

    @PrePersist
    public void prePersist() {
        if (fecha == null)
            fecha = Instant.now();
        if (iva == null)
            iva = new BigDecimal("21.00");
        if (tms == null)
            tms = LocalDateTime.now();
        recomputeTotals();
    }

    @Override
    public String toString() {
        return "Ticket [id=" + id + ", estado=" + estado + ", fecha=" + fecha + ", referencia=" + referencia
                + ", plaza=" + plaza + ", totalSinIva=" + totalSinIva + ", iva=" + iva + ", total=" + total
                + ", cliente=" + cliente + ", vehiculo=" + vehiculo + ", tms=" + tms + ", metodoPago=" + metodoPago
                + ", comentarios=" + comentarios + ", bonoMotivo=" + bonoMotivo + ", usuario=" + usuario + ", centro="
                + centro + ", detalles=" + detalles + "]";
    }

    @PreUpdate
    public void preUpdate() {
        tms = LocalDateTime.now();
        if (iva == null)
            iva = new BigDecimal("21.00");
        recomputeTotals();
    }

    private void recomputeTotals() {
        if (detalles != null && !detalles.isEmpty()) {
            BigDecimal sum = BigDecimal.ZERO;
            for (TicketDetalle d : detalles) {
                if (d.getPrecio() != null && d.getCantidad() != null) {
                    sum = sum.add(d.getPrecio().multiply(BigDecimal.valueOf(d.getCantidad().longValue())));
                    
                }
            }
            this.total = sum.setScale(2, RoundingMode.HALF_UP);
            BigDecimal factor = iva.divide(new BigDecimal("100"), 4, RoundingMode.HALF_UP).add(BigDecimal.ONE);
            this.totalSinIva = total.divide(factor, 2, RoundingMode.HALF_UP);
        } else {
            if (total != null) {
                BigDecimal factor = iva.divide(new BigDecimal("100"), 4, RoundingMode.HALF_UP).add(BigDecimal.ONE);
                this.totalSinIva = total.divide(factor, 2, RoundingMode.HALF_UP);
            }
            else if (totalSinIva != null) {
                BigDecimal factor = iva.divide(new BigDecimal("100"), 4, RoundingMode.HALF_UP).add(BigDecimal.ONE);
                
                this.total = totalSinIva.multiply(factor).setScale(2, RoundingMode.HALF_UP);
            }   else {
                this.total = BigDecimal.ZERO.setScale(2);
                this.totalSinIva = BigDecimal.ZERO.setScale(2);
            }
        }
    }

    /* getters/setters */
    public Long getId() {
        return id;
    }

    public Instant getFecha() {
        return fecha;
    }

    public void setFecha(Instant fecha) {
        this.fecha = fecha;
    }

    public String getReferencia() {
        return referencia;
    }

    public void setReferencia(String referencia) {
        this.referencia = referencia;
    }

    public String getPlaza() {
        return plaza;
    }

    public void setPlaza(String plaza) {
        this.plaza = plaza;
    }

    public String getComentarios() {
        return comentarios;
    }

    public void setComentarios(String comentarios) {
        this.comentarios = comentarios;
    }

    public String getBonoMotivo() {
        return bonoMotivo;
    }

    public void setBonoMotivo(String bonoMotivo) {
        this.bonoMotivo = bonoMotivo;
    }

    public User getUsuario() {
        return usuario;
    }

    public void setUsuario(User usuario) {
        this.usuario = usuario;
    }

    public Cliente getCliente() {
        return cliente;
    }

    public void setCliente(Cliente cliente) {
        this.cliente = cliente;
    }

    public Vehiculo getVehiculo() {
        return vehiculo;
    }

    public void setVehiculo(Vehiculo vehiculo) {
        this.vehiculo = vehiculo;
    }

    public Estado getEstado() {
        return estado;
    }

    public void setEstado(Estado estado) {
        this.estado = estado;
    }

    public MetodoPago getMetodoPago() {
        return metodoPago;
    }

    public void setMetodoPago(MetodoPago metodoPago) {
        this.metodoPago = metodoPago;
    }

    public CentroTrabajo getCentro() {
        return centro;
    }

    public void setCentro(CentroTrabajo centro) {
        this.centro = centro;
    }

    public BigDecimal getTotalSinIva() {
        return totalSinIva;
    }

    public void setTotalSinIva(BigDecimal totalSinIva) {
        this.totalSinIva = totalSinIva;
    }

    public BigDecimal getIva() {
        return iva;
    }

    public void setIva(BigDecimal iva) {
        this.iva = iva;
    }

    public BigDecimal getTotal() {
        return total;
    }

    public void setTotal(BigDecimal total) {
        this.total = total;
    }

    public LocalDateTime getTms() {
        return tms;
    }

    public void setTms(LocalDateTime tms) {
        this.tms = tms;
    }

    public List<TicketDetalle> getDetalles() {
        return detalles;
    }

    public void setDetalles(List<TicketDetalle> detalles) {
        this.detalles.clear();
        if (detalles != null)
            detalles.forEach(this::addDetalle);
    }
    
}
