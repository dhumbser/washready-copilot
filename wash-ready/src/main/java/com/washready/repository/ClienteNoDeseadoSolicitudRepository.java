package com.washready.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.washready.model.Cliente;
import com.washready.model.ClienteNoDeseadoSolicitud;
import com.washready.model.ClienteNoDeseadoSolicitud.Estado;

public interface ClienteNoDeseadoSolicitudRepository extends JpaRepository<ClienteNoDeseadoSolicitud, Long> {

    Optional<ClienteNoDeseadoSolicitud> findByToken(String token);
    List<ClienteNoDeseadoSolicitud> findByClienteAndEstado(Cliente cliente, Estado estado);
    List<ClienteNoDeseadoSolicitud> findAllByEstadoOrderByCreadoAtDesc(Estado estado);

}
