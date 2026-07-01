package com.washready.model;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

import jakarta.persistence.*;

@Entity
@Table(name = "servicio")
public class Servicio {

    public enum Tipo { TIPO_1, TIPO_2, TIPO_3, GENERAL }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "descripcion", nullable = false, length = 200)
    private String descripcion;

    @Column(name = "importe", nullable = false, precision = 10, scale = 2)
    private BigDecimal importe;

    /** Stock opcional (puede ser null si no aplica) */
    @Column(name = "stock")
    private Integer stock;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo", nullable = false, length = 20)
    private Tipo tipo;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
        name = "servicio_centro",
        joinColumns = @JoinColumn(name = "servicio_id", foreignKey = @ForeignKey(name = "fk_servcentro_servicio")),
        inverseJoinColumns = @JoinColumn(name = "centro_id", foreignKey = @ForeignKey(name = "fk_servcentro_centro"))
    )
    @JsonIgnore // evitar ciclos y sobrecarga; expondremos centroIds en su lugar
    private Set<CentroTrabajo> centros = new LinkedHashSet<>();

    @Column(name = "editable", nullable = false)
    private boolean editable = false;

    @Column(name = "importe_cero_en_ticket", nullable = false)
    private boolean importeCeroEnTicket = false;

    @Column(name = "disponible_todos_centros", nullable = false)
    private boolean disponibleTodosCentros = false;

    // ===== Campos transitorios para intercambio JSON =====

    /** Nueva forma recomendada: lista de IDs de centros (N:N). */
    @Transient
    private List<Long> centroIds;

    /** Compatibilidad: single centroId (primer centro si hay varios). */
    @Transient
    private Long centroIdCompat;

    public Servicio() {}

    public Servicio(String descripcion, BigDecimal importe, Integer stock, Tipo tipo) {
        this.descripcion = descripcion;
        this.importe = importe;
        this.stock = stock;
        this.tipo = tipo;
    }

    public Long getId() { return id; }

    public String getDescripcion() { return descripcion; }
    public void setDescripcion(String descripcion) { this.descripcion = descripcion; }

    public BigDecimal getImporte() { return importe; }
    public void setImporte(BigDecimal importe) { this.importe = importe; }

    public Integer getStock() { return stock; }
    public void setStock(Integer stock) { this.stock = stock; }

    public Tipo getTipo() { return tipo; }
    public void setTipo(Tipo tipo) { this.tipo = tipo; }

    public boolean isEditable() { return editable; }
    public void setEditable(boolean editable) { this.editable = editable; }

    public boolean isImporteCeroEnTicket() { return importeCeroEnTicket; }
    public void setImporteCeroEnTicket(boolean importeCeroEnTicket) { this.importeCeroEnTicket = importeCeroEnTicket; }

    public boolean isDisponibleTodosCentros() { return disponibleTodosCentros; }
    public void setDisponibleTodosCentros(boolean disponibleTodosCentros) { this.disponibleTodosCentros = disponibleTodosCentros; }

    @JsonIgnore
    public Set<CentroTrabajo> getCentros() { return centros; }
    public void setCentros(Set<CentroTrabajo> centros) {
        this.centros = (centros != null ? centros : new LinkedHashSet<>());
    }

    // Exponer/aceptar centroIds en JSON
    @JsonProperty("centroIds")
    public List<Long> getCentroIds() {
        if (centroIds != null) return centroIds;
        if (centros == null) return List.of();
        return centros.stream()
                .map(CentroTrabajo::getId)
                .filter(Objects::nonNull)
                .toList();
    }
    @JsonProperty("centroIds")
    public void setCentroIds(List<Long> centroIds) {
        // Solo guardamos en transitorio; el Service resolverá y poblará "centros"
        this.centroIds = (centroIds != null ? new ArrayList<>(centroIds) : null);
    }

    // ========== Compatibilidad: single centro (DEPRECATED) ==========

    /** Compat: primer centro (si hay varios) */
    @Deprecated
    @JsonIgnore
    public CentroTrabajo getCentroTrabajo() {
        return (centros == null || centros.isEmpty()) ? null : centros.iterator().next();
    }

    /** Compat: asigna un único centro (limpia y deja solo ese) */
    @Deprecated
    public void setCentroTrabajo(CentroTrabajo centroTrabajo) {
        this.centros.clear();
        if (centroTrabajo != null) this.centros.add(centroTrabajo);
    }

    /** Compat JSON: centroId (primer centro) */
    @JsonProperty("centroId")
    public Long getCentroIdCompat() {
        if (centroIdCompat != null) return centroIdCompat;
        CentroTrabajo ct = getCentroTrabajo();
        return (ct != null ? ct.getId() : null);
    }

    /** Compat JSON: centroId (se guarda transitoriamente) */
    @JsonProperty("centroId")
    public void setCentroIdCompat(Long centroId) {
        this.centroIdCompat = centroId;
    }
    
}
