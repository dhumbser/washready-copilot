package com.washready.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.washready.model.Ticket;
import com.washready.model.TicketAnulacion;
import com.washready.model.TicketAnulacion.Estado;

public interface TicketAnulacionRepository extends JpaRepository<TicketAnulacion, Long> {

    Optional<TicketAnulacion> findByToken(String token);
    List<TicketAnulacion> findByTicketAndEstado(Ticket ticket, Estado estado);
    boolean existsByTicketIdAndEstado(Long ticketId, Estado estado);
    List<TicketAnulacion> findAllByEstadoOrderByCreadoAtDesc(Estado estado);

}
