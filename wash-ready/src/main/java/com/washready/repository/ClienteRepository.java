package com.washready.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.washready.model.Cliente;

public interface ClienteRepository extends JpaRepository<Cliente, Long> {

    boolean existsByNif(String nif);
    boolean existsByCorreoIgnoreCase(String correo);
    boolean existsByTelefono(String telefono);
    boolean existsByTelefonoAndIdNot(String telefono, Long id);

    Optional<Cliente> findByNif(String nif);
    Optional<Cliente> findByCorreoIgnoreCase(String correo);

    List<Cliente> findByNombreContainingIgnoreCase(String nombre);
    List<Cliente> findByApellidoContainingIgnoreCase(String apellido);

    @Query("""
        select c
        from Cliente c
        where (:q is null
            or lower(c.nombre) like lower(concat('%', :q, '%'))
            or lower(c.apellido) like lower(concat('%', :q, '%'))
            or lower(c.nif) like lower(concat('%', :q, '%'))
            or lower(c.correo) like lower(concat('%', :q, '%'))
            or lower(c.telefono) like lower(concat('%', :q, '%'))
            or (:q1 is not null and :q2 is not null and (
                (lower(c.nombre) like lower(concat('%', :q1, '%')) and lower(c.apellido) like lower(concat('%', :q2, '%')))
                or (lower(c.nombre) like lower(concat('%', :q2, '%')) and lower(c.apellido) like lower(concat('%', :q1, '%')))
            ))
        )
        and (:noDeseado is null or c.noDeseado = :noDeseado)
        order by c.nombre asc, c.apellido asc, c.id asc
    """)
    Page<Cliente> search(@Param("q") String q,
                            @Param("q1") String q1,
                            @Param("q2") String q2,
                            @Param("noDeseado") Boolean noDeseado,
                            Pageable pageable);
    
    @Query("""
        select (count(c) > 0)
        from Cliente c
        where c.telefono = :tel
            and (:excludeId is null or c.id <> :excludeId)
    """)
    boolean existsTelefonoExcludingId(@Param("tel") String tel, @Param("excludeId") Long excludeId);
    
}
