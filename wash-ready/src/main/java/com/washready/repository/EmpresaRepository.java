package com.washready.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.washready.model.Empresa;

public interface EmpresaRepository extends JpaRepository<Empresa, Long> {
    
    boolean existsByCif(String cif);
    Optional<Empresa> findByCif(String cif);
    
}
