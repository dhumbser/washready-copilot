package com.washready.repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.washready.model.Servicio;

public interface ServicioRepository extends JpaRepository<Servicio, Long> {

    // ===== Opción 1: Entidades con centros (sin duplicar) =====
    // Usa esto desde el Service y mapea a DTO con centroIds/centrosNombres
    @EntityGraph(attributePaths = "centros")
    @Query("""
        select distinct s
        from Servicio s
            left join s.centros c
        where (:centroId is null or c.id = :centroId or s.disponibleTodosCentros = true)
            and (:q is null or lower(s.descripcion) like lower(concat('%', :q, '%')))
            and (:minImporte is null or s.importe >= :minImporte)
            and (:maxImporte is null or s.importe <= :maxImporte)
            and (:tipo is null or s.tipo = :tipo)
        order by s.id desc
    """)
    List<Servicio> findFilteredWithCentros(@Param("centroId") Long centroId,
                                           @Param("q") String q,
                                           @Param("minImporte") BigDecimal minImporte,
                                           @Param("maxImporte") BigDecimal maxImporte,
                                           @Param("tipo") Servicio.Tipo tipo);

    @EntityGraph(attributePaths = "centros")
    Optional<Servicio> findById(Long id);

    // ===== Opción 2: Vista agregada (nativa MySQL) =====
    // Útil si quieres una respuesta "ligera" ya agregada (centros en CSV)
    public interface ServicioAggView {
        Long getId();
        String getDescripcion();
        BigDecimal getImporte();
        Integer getStock();
        Servicio.Tipo getTipo();
        boolean isEditable();
        // CSV agregados por MySQL
        String getCentroIdsCsv();
        String getCentroNombresCsv();
    }

    @Query(value = """
        select
            s.id as id,
            s.descripcion as descripcion,
            s.importe as importe,
            s.stock as stock,
            s.tipo as tipo,
            s.editable as isEditable,
            group_concat(distinct c.id order by c.id separator ',') as centroIdsCsv,
            group_concat(distinct c.nombre order by c.nombre separator ', ') as centroNombresCsv
        from servicio s
            left join servicio_centro sc on sc.servicio_id = s.id
            left join centro_trabajo c on c.id = sc.centro_id
        where (:centroId is null or c.id = :centroId or s.disponible_todos_centros = true)
            and (:q is null or lower(s.descripcion) like lower(concat('%', :q, '%')))
            and (:minImporte is null or s.importe >= :minImporte)
            and (:maxImporte is null or s.importe <= :maxImporte)
        group by s.id, s.descripcion, s.importe, s.stock, s.tipo, s.editable
        order by s.id desc
        """, nativeQuery = true)
    List<ServicioAggView> searchAgg(@Param("centroId") Long centroId,
                                    @Param("q") String q,
                                    @Param("minImporte") BigDecimal minImporte,
                                    @Param("maxImporte") BigDecimal maxImporte);

}
