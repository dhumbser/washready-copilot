package com.washready.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.washready.model.TicketDetalle;

public interface TicketDetalleRepository extends JpaRepository<TicketDetalle, Long> {

    List<TicketDetalle> findByTicketId(Long ticketId);

    Optional<TicketDetalle> findByIdAndTicketId(Long id, Long ticketId);
    void deleteByTicketId(Long ticketId);
    
}
