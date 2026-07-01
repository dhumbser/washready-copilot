package com.washready.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.washready.model.User;

public interface UserRepository extends JpaRepository<User, Long> {
    
    Optional<User> findByUsuario(String usuario);

    boolean existsByUsuario(String usuario);

    @Query("""
        select u
        from User u
            left join fetch u.empresa e
            left join fetch u.centroTrabajo c
    """)
    List<User> findAllWithEmpresaCentro();

    @Query("""
        select u
        from User u
            left join fetch u.empresa e
            left join fetch u.centroTrabajo c
        where u.id = :id
    """)
    Optional<User> findByIdWithEmpresaCentro(@Param("id") Long id);

    List<User> findByCentroTrabajoId(Long centroId);

    @Query("""
        select u.disabledFrom
        from User u
        where u.usuario = :usuario
    """)
    Optional<java.time.LocalDateTime> findDisabledFromByUsuario(@Param("usuario") String usuario);

    @Query("""
        select u.centroTrabajo.id
        from User u
        where u.usuario = :usuario
    """)
    Optional<Long> findCentroIdByUsuario(@Param("usuario") String usuario);

    @Query("""
        select count(u)
        from User u
        where u.role = 'ROLE_ADMIN'
            and u.id <> :excludeId
            and (u.disabledFrom is null or u.disabledFrom > :now)
    """)
    long countActiveAdminsExcluding(@Param("excludeId") Long excludeId, @Param("now") java.time.LocalDateTime now);

}
