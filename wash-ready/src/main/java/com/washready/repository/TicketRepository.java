package com.washready.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.washready.model.Ticket;
import com.washready.model.Ticket.Estado;
import com.washready.model.Vehiculo;
import com.washready.repository.proyecciones.CentroFacturacionAgg;
import com.washready.repository.proyecciones.MetodoPagoTotalRow;
import com.washready.repository.proyecciones.ResumenInforme;
import com.washready.repository.proyecciones.TicketInformeRow;

public interface TicketRepository extends JpaRepository<Ticket, Long> {

  @Query("""
        select t from Ticket t
          join t.vehiculo v
          join t.usuario  u
          join t.cliente  c
          left join u.centroTrabajo ct
        where (:centroId is null or ct.id = :centroId)
          and (:matricula is null or lower(v.matricula) like lower(concat('%', :matricula, '%')))
          and (:marca     is null or lower(v.marca)     like lower(concat('%', :marca,     '%')))
          and (:modelo    is null or lower(v.modelo)    like lower(concat('%', :modelo,    '%')))
          and (:color     is null or lower(v.color)     like lower(concat('%', :color,     '%')))
          and (:cliente   is null or lower(concat(coalesce(c.nombre,''),' ',coalesce(c.apellido,'')))
                                                        like lower(concat('%', :cliente, '%')))
          and (:telefono  is null or c.telefono         like concat('%', :telefono, '%'))
          and (:operario  is null or lower(u.usuario)   like lower(concat('%', :operario,  '%')))
          and (:referencia is null or lower(t.referencia) like lower(concat('%', :referencia, '%')))
          and (:desde     is null or t.fecha >= :desde)
          and (:hasta     is null or t.fecha <= :hasta)
          and ((:estados) is null or t.estado in (:estados))
          and (:estado    is null or t.estado = :estado)
          and (:metodo    is null or t.metodoPago = :metodo)
        order by t.fecha desc, t.id desc
      """)
  Page<Ticket> search(
      @Param("centroId") Long centroId,
      @Param("matricula") String matricula,
      @Param("marca") String marca,
      @Param("modelo") String modelo,
      @Param("color") String color,
      @Param("cliente") String cliente,
      @Param("telefono") String telefono,
      @Param("operario") String operario,
      @Param("referencia") String referencia,
      @Param("estados") List<Ticket.Estado> estados,
      @Param("estado") Ticket.Estado estado,
      @Param("desde") Instant desde,
      @Param("hasta") Instant hasta,
      @Param("metodo") Ticket.MetodoPago metodo,
      Pageable pageable);

  @Query("select max(t.referencia) from Ticket t where t.referencia like concat(:prefix, '%')")
  String findMaxReferenciaByPrefix(@Param("prefix") String prefix);

  Optional<Ticket> findTopByCentroIdAndFechaBetweenAndReferenciaIsNotNullOrderByIdDesc(
      Long centroId,
      Instant startInclusive,
      Instant endExclusive);

  // ===== INFORMES ======

  /** Resumen global: nÂº tickets, base, iva, total en rango/centro */
  @Query("""
        select
          count(t)                                        as tickets,
          coalesce(sum(t.totalSinIva), 0)                 as base,
          coalesce(sum(t.total - t.totalSinIva), 0)       as iva,
          coalesce(sum(t.total), 0)                       as total
        from Ticket t
        where (:centroId is null or t.centro.id = :centroId)
          and (:desde is null or t.fecha >= :desde)
          and (:hasta is null or t.fecha < :hasta)
          and (t.estado in (Estado.PAGADO, Estado.CERRADO))
      """)
  ResumenInforme resumen(
      @Param("centroId") Long centroId,
      @Param("desde") Instant desde,
      @Param("hasta") Instant hasta);

  /** Para los totales globales */
  @Query("""
        select
           t.id                                 as id,
           t.fecha                              as fecha,
           t.referencia                         as referencia,
           concat(coalesce(c.nombre,''),' ',coalesce(c.apellido,'')) as cliente,
           v.matricula                          as matricula,
           t.totalSinIva                        as base,
           (t.total - t.totalSinIva)            as iva,
           t.total                              as total,
           coalesce(ct.nombre, '')              as centro
        from Ticket t
           join t.cliente  c
           join t.vehiculo v
      left join t.usuario  u
      left join u.centroTrabajo ct
       where (:centroId is null or ct.id = :centroId)
         and (:desde    is null or t.fecha >= :desde)
         and (:hasta    is null or t.fecha <= :hasta)
         and t.estado in (Estado.PAGADO, Estado.CERRADO)
       order by t.fecha desc, t.id desc
      """)
  Page<TicketInformeRow> informeTickets(
      @Param("centroId") Long centroId,
      @Param("desde") Instant desde,
      @Param("hasta") Instant hasta,
      Pageable pageable);

  @Query("""
        select distinct t
        from Ticket t
          join fetch t.cliente  c
          join fetch t.vehiculo v
          join fetch t.centro   ct
          left join fetch t.detalles d
          left join fetch d.servicio s
        where (:centroId is null or ct.id = :centroId)
          and (:desde is null or t.fecha >= :desde)
          and (:hasta is null or t.fecha < :hasta)
          and (:metodo is null or t.metodoPago = :metodo)
          and t.estado in (
            Estado.PAGADO,
            Estado.CERRADO
          )
        order by t.fecha desc, t.id desc
      """)
  List<Ticket> cierreDiaTickets(
      @Param("centroId") Long centroId,
      @Param("desde") Instant desde,
      @Param("hasta") Instant hasta,
      @Param("metodo") Ticket.MetodoPago metodo);

  @Query("""
        select distinct t from Ticket t
          join fetch t.cliente  c
          join fetch t.vehiculo v
          join fetch t.centro   ct
        where (:centroId is null or ct.id = :centroId)
          and (:desde is null or t.fecha >= :desde)
          and (:hasta is null or t.fecha < :hasta)
          and t.estado = Estado.PTE_PAGO
        order by t.fecha desc, t.id desc
      """)
  List<Ticket> cierreDiaPendientes(
      @Param("centroId") Long centroId,
      @Param("desde") Instant desde,
      @Param("hasta") Instant hasta);

  @Query("""
        select
          t.metodoPago as metodoPago,
          coalesce(sum(t.total), 0) as total
        from Ticket t
        where (:centroId is null or t.centro.id = :centroId)
          and (:desde is null or t.fecha >= :desde)
          and (:hasta is null or t.fecha < :hasta)
          and (:metodo is null or t.metodoPago = :metodo)
          and t.estado in (
            Estado.PAGADO,
            Estado.CERRADO
          )
        group by t.metodoPago
      """)
  List<MetodoPagoTotalRow> totalesPorMetodoPago(
      @Param("centroId") Long centroId,
      @Param("desde") Instant desde,
      @Param("hasta") Instant hasta,
      @Param("metodo") Ticket.MetodoPago metodo);

  @Query("""
        select
          count(t)                                                      as totalTickets,
          coalesce(sum(t.totalSinIva), 0)                               as totalBase,
          coalesce(sum(t.total), 0) - coalesce(sum(t.totalSinIva), 0)   as totalIva,
          coalesce(sum(t.total), 0)                                     as totalConIva
        from Ticket t
          join t.usuario u
          left join u.centroTrabajo ct
        where (:centroId is null or ct.id = :centroId)
          and (:desde   is null or t.fecha >= :desde)
          and (:hasta   is null or t.fecha <  :hasta)
          and (
         (:estado is null and t.estado in (Estado.PAGADO, Estado.CERRADO))
          or (:estado is not null and t.estado = :estado)
          )
          and (:metodo  is null or t.metodoPago = :metodo)
      """)
  ResumenInforme resumenGlobal(
      @Param("centroId") Long centroId,
      @Param("desde") Instant desde,
      @Param("hasta") Instant hasta,
      @Param("estado") Estado estado,
      @Param("metodo") Ticket.MetodoPago metodo);

  // Facturacion por centro en un rango [desde, hasta)
  @Query("""
        select
          ct.id                                    as centroId,
          ct.nombre                                as centroNombre,
          count(t)                                 as tickets,
          coalesce(sum(t.total), 0)                as total
        from Ticket t
          left join t.centro ct
        where (:centroId is null or ct.id = :centroId)
          and (:desde is null or t.fecha >= :desde)
          and (:hasta is null or t.fecha <  :hasta)
          and t.estado in (Estado.PAGADO, Estado.CERRADO)
        group by ct.id, ct.nombre
        order by coalesce(ct.nombre, '') asc, ct.id asc
      """)
  List<CentroFacturacionAgg> facturacionPorCentro(
      @Param("centroId") Long centroId,
      @Param("desde") Instant desde,
      @Param("hasta") Instant hasta);

  // Consulta para impresion del ticket
  @Query("""
        select distinct t from Ticket t
          join fetch t.cliente c
          join fetch t.vehiculo v
          join fetch t.usuario u
          left join fetch u.centroTrabajo ct
          left join fetch ct.empresa e
          left join fetch t.detalles d
          left join fetch d.servicio s
        where t.id = :id
      """)
  Optional<Ticket> findByIdDeep(@Param("id") Long id);

  @Query("""
        select distinct t from Ticket t
          join fetch t.cliente c
          join fetch t.vehiculo v
          left join fetch t.detalles d
          left join fetch d.servicio s
        where c.id = :clienteId
          and t.estado in (
            Estado.PAGADO,
            Estado.CERRADO
          )
        order by t.fecha desc
      """)
  List<Ticket> findHistoryByCliente(@Param("clienteId") Long clienteId, Pageable pageable);

  List<Ticket> findByVehiculoAndFechaBetween(Vehiculo vehiculo, Instant start, Instant end);

}
