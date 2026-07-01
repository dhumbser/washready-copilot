package com.washready.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.washready.model.Vehiculo;

public interface VehiculoRepository extends JpaRepository<Vehiculo, Long> {

    boolean existsByMatricula(String matricula);

    @Query("""
            select distinct v
            from Vehiculo v
                left join v.clientes c
            where (:color is null or lower(v.color) like lower(concat('%', :color, '%')))
                    and (:marca is null or lower(v.marca) like lower(concat('%', :marca, '%')))
                    and (:modelo is null or lower(v.modelo) like lower(concat('%', :modelo, '%')))
                    and (:matricula is null or lower(v.matricula) like lower(concat('%', :matricula, '%')))
                    and (:plaza is null or lower(v.plaza) like lower(concat('%', :plaza, '%')))
                    and (:clienteId is null or c.id = :clienteId)
                    and (:cliente is null or lower(concat(coalesce(c.nombre,''),' ',coalesce(c.apellido,'')))
                            like lower(concat('%', :cliente, '%')))
    """)
    Page<Vehiculo> search(@Param("color") String color,
                            @Param("marca") String marca,
                            @Param("modelo") String modelo,
                            @Param("matricula") String matricula,
                            @Param("plaza") String plaza,
                            @Param("cliente") String cliente,
                            @Param("clienteId") Long clienteId,
                            Pageable pageable);

    @EntityGraph(attributePaths = "clientes")
    @Query("""
        select distinct v
        from Vehiculo v
            left join v.clientes c
        where (:matricula is not null and :matricula <> ''
                and replace(replace(replace(lower(v.matricula), ' ', ''), '-', ''), '.', '')
                            like lower(concat('%', :matricula, '%')))
                or (:digits is not null and :digits <> ''
                    and replace(replace(replace(replace(replace(replace(coalesce(c.telefono,''), ' ', ''), '-', ''), '(', ''), ')', ''), '+', ''), '.', '')
                                like concat('%', :digits, '%'))
        order by v.matricula asc, v.id asc
    """)
    List<Vehiculo> searchAuto(@Param("matricula") String matricula, @Param("digits") String digits);

    // Cargar vehículo con clientes asociados (M2M)
    @EntityGraph(attributePaths = "clientes")
    Optional<Vehiculo> findWithClientesById(@Param("id") Long id);

    Vehiculo findByMatricula(String matricula);

}
