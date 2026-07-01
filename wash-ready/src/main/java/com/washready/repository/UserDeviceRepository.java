package com.washready.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import com.washready.model.User;
import com.washready.model.UserDevice;

public interface UserDeviceRepository extends JpaRepository<UserDevice, Long> {
    
    @Query("""
        select ud
        from UserDevice ud
        where ud.user = :user
            and ud.deviceId = :deviceId
            and ud.revokedAt is null
    """)
    Optional<UserDevice> findActiveByUserAndDeviceId(@Param("user") User user, @Param("deviceId") String deviceId);

    Optional<UserDevice> findByUserAndDeviceId(User user, String deviceId);

    @Query("""
        select ud.revokedAt
        from UserDevice ud
        where ud.user.usuario = :usuario
            and ud.deviceId = :deviceId
    """)
    Optional<java.time.LocalDateTime> findRevokedAtByUsuarioAndDeviceId(@Param("usuario") String usuario, @Param("deviceId") String deviceId);

    @Query("""
        select count(distinct ud.deviceId)
        from UserDevice ud
        where ud.user.centroTrabajo.id = :centroId
            and ud.revokedAt is null
            and (ud.user.disabledFrom is null or ud.user.disabledFrom > CURRENT_TIMESTAMP)
    """)
    long countActiveDevicesByCentroId(@Param("centroId") Long centroId);

    @Query("""
        select count(ud)
        from UserDevice ud
        where ud.user.centroTrabajo.id = :centroId
            and ud.deviceId = :deviceId
            and ud.revokedAt is null
            and (ud.user.disabledFrom is null or ud.user.disabledFrom > CURRENT_TIMESTAMP)
    """)
    long countActiveByCentroIdAndDeviceId(@Param("centroId") Long centroId, @Param("deviceId") String deviceId);

    @Query("""
        select count(ud)
        from UserDevice ud
        where ud.user.id = :userId
            and ud.revokedAt is null
            and (ud.user.disabledFrom is null or ud.user.disabledFrom > CURRENT_TIMESTAMP)
    """)
    long countActiveByUserId(@Param("userId") Long userId);

    @Query("""
        select count(ud)
        from UserDevice ud
        where ud.user.id = :userId
            and ud.revokedAt is not null
    """)
    long countRevokedByUserId(@Param("userId") Long userId);

    List<UserDevice> findByUserIdOrderByLastSeenAtDesc(Long userId);

    @Query("""
        select ud
        from UserDevice ud
        where ud.user.id = :userId
            and ud.deviceId = :deviceId
    """)
    UserDevice findByUserIdAndDeviceId(@Param("userId") Long userId, @Param("deviceId") String deviceId);

    @Query("""
        select ud
        from UserDevice ud
        where ud.user.centroTrabajo.id = :centroId
            and ud.revokedAt is null
            and (ud.user.disabledFrom is null or ud.user.disabledFrom > CURRENT_TIMESTAMP)
    """)
    List<UserDevice> findActiveByCentroId(@Param("centroId") Long centroId);

    @Query("""
        select ud
        from UserDevice ud
        where ud.user.centroTrabajo.id = :centroId
            and (ud.user.disabledFrom is null or ud.user.disabledFrom > CURRENT_TIMESTAMP)
    """)
    List<UserDevice> findAllByCentroId(@Param("centroId") Long centroId);

    @Transactional
    @Modifying
    @Query("""
        delete from UserDevice ud
        where ud.user.id = :userId
    """)
    int deleteByUserId(@Param("userId") Long userId);

    @Modifying
    @Query("""
        update UserDevice ud
        set ud.revokedAt = :revokedAt
        where ud.user.centroTrabajo.id = :centroId
            and ud.deviceId = :deviceId
            and ud.revokedAt is null
    """)
    int revokeByCentroAndDeviceId(@Param("centroId") Long centroId,
                                    @Param("deviceId") String deviceId,
                                    @Param("revokedAt") java.time.LocalDateTime revokedAt);

    @Modifying
    @Query("""
        update UserDevice ud
        set ud.revokedAt = null,
            ud.lastSeenAt = :lastSeenAt
        where ud.user.centroTrabajo.id = :centroId
            and ud.deviceId = :deviceId
            and ud.revokedAt is not null
    """)
    int reactivateByCentroAndDeviceId(@Param("centroId") Long centroId,
                                        @Param("deviceId") String deviceId,
                                        @Param("lastSeenAt") java.time.LocalDateTime lastSeenAt);

    @Query("""
        select count(ud)
        from UserDevice ud
        where ud.user.centroTrabajo.id = :centroId
            and ud.deviceId = :deviceId
            and ud.revokedAt is null
    """)
    long countAnyActiveByCentroIdAndDeviceId(@Param("centroId") Long centroId, @Param("deviceId") String deviceId);

    @Transactional
    @Modifying
    @Query("""
        delete from UserDevice ud
        where ud.user.centroTrabajo.id = :centroId
            and ud.deviceId = :deviceId
    """)
    int deleteByCentroIdAndDeviceId(@Param("centroId") Long centroId, @Param("deviceId") String deviceId);

}
