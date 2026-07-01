package com.washready.model;

import java.time.LocalDateTime;

import jakarta.persistence.*;

@Entity
@Table(name = "centro_device", uniqueConstraints = {
        @UniqueConstraint(name = "uk_centro_device_centro_device", columnNames = { "centro_id", "device_id" })
})
public class CentroDevice {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "centro_id", nullable = false, foreignKey = @ForeignKey(name = "fk_centro_device_centro"))
    private CentroTrabajo centroTrabajo;

    @Column(name = "device_id", nullable = false, length = 64)
    private String deviceId;

    @Column(name = "alias", length = 120)
    private String alias;

    @Column(name = "registered_at", nullable = false)
    private LocalDateTime registeredAt;

    public CentroDevice() {
    }

    public CentroDevice(CentroTrabajo centroTrabajo, String deviceId, LocalDateTime registeredAt) {
        this.centroTrabajo = centroTrabajo;
        this.deviceId = deviceId;
        this.registeredAt = registeredAt;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public CentroTrabajo getCentroTrabajo() { return centroTrabajo; }
    public void setCentroTrabajo(CentroTrabajo centroTrabajo) { this.centroTrabajo = centroTrabajo; }

    public String getDeviceId() { return deviceId; }
    public void setDeviceId(String deviceId) { this.deviceId = deviceId; }

    public String getAlias() { return alias; }
    public void setAlias(String alias) { this.alias = alias; }

    public LocalDateTime getRegisteredAt() { return registeredAt; }
    public void setRegisteredAt(LocalDateTime registeredAt) { this.registeredAt = registeredAt; }

}
