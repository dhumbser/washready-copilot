package com.washready.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.washready.model.CentroDevice;

public interface CentroDeviceRepository extends JpaRepository<CentroDevice, Long> {

    @Query("""
        select cd
        from CentroDevice cd
        where cd.centroTrabajo.id = :centroId
            and cd.deviceId = :deviceId
    """)
    Optional<CentroDevice> findByCentroIdAndDeviceId(@Param("centroId") Long centroId,
                                                      @Param("deviceId") String deviceId);

    List<CentroDevice> findByCentroTrabajoIdOrderByRegisteredAtDesc(Long centroId);

}
