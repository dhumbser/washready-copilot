package com.washready.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.washready.model.CentroTrabajo;

public interface CentroTrabajoRepository extends JpaRepository<CentroTrabajo, Long> {

    List<CentroTrabajo> findByEmpresaId(Long empresaId);

    List<CentroTrabajo> findByTransaccionalTrue();

    List<CentroTrabajo> findByEmpresaIdAndTransaccionalTrue(Long empresaId);

}
