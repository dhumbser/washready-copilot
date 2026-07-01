package com.washready.repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.washready.model.Adelanto;
import com.washready.model.Adelanto.Estado;

public interface AdelantoRepository extends JpaRepository<Adelanto, Long> {

    @Query("""
        select a
          from Adelanto a
         where (:uid   is null or a.user.id = :uid)
           and (:estado is null or a.estado = :estado)
           and (:fromTs is null or a.creadoAt >= :fromTs)
           and (:toTs   is null or a.creadoAt <  :toTs)
         order by a.creadoAt desc
    """)
    Page<Adelanto> search(@Param("uid") Long userId,
                          @Param("estado") Estado estado,
                          @Param("fromTs") LocalDateTime from,
                          @Param("toTs") LocalDateTime to,
                          Pageable pageable);

    @Query("""
        select coalesce(sum(a.importe), 0)
          from Adelanto a
         where a.user.id = :uid
           and a.estado not in :excl
           and a.creadoAt >= :fromTs
           and a.creadoAt <  :toTs
    """)
    BigDecimal sumUserInRangeExcluding(@Param("uid") Long userId,
                                       @Param("excl") java.util.Collection<Estado> excludeEstados,
                                       @Param("fromTs") LocalDateTime from,
                                       @Param("toTs") LocalDateTime to);

   @Query("""
      select coalesce(sum(a.importe), 0)
        from Adelanto a
       where a.estado = Estado.ACEPTADO
         and a.decididoAt is not null
         and (:centroId is null or a.centro.id = :centroId)
         and (:fromTs  is null or a.decididoAt >= :fromTs)
         and (:toTs    is null or a.decididoAt <  :toTs)
    """)
  BigDecimal sumAceptados(@Param("centroId") Long centroId,
                          @Param("fromTs") LocalDateTime fromTs,
                          @Param("toTs")   LocalDateTime toTs);

  Optional<Adelanto> findByDecisionToken(String decisionToken);

}
