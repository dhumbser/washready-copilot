package com.washready.controller;

import com.washready.model.Empresa;
import com.washready.service.EmpresaService;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/empresas")
public class EmpresaController {
    private final EmpresaService service;

    public EmpresaController(EmpresaService service) {
        this.service = service;
    }

    public record EmpresaDto(
            Long id,
            String nombre,
            String direccion,
            String municipio,
            String provincia,
            String pais,
            String codigoPostal,
            String correo,
            String telefono,
            String cif) {
        public static EmpresaDto from(Empresa e) {
            return new EmpresaDto(
                    e.getId(), e.getNombre(), e.getDireccion(), e.getMunicipio(), e.getProvincia(), e.getPais(),
                    e.getCodigoPostal(), e.getCorreo(), e.getTelefono(), e.getCif());
        }
    }

    @GetMapping
    public List<EmpresaDto> listar() {
        return service.listar().stream().map(EmpresaDto::from).toList();
    }

    @GetMapping("/{id}")
    public ResponseEntity<EmpresaDto> obtener(@PathVariable Long id) {
        return service.obtener(id).map(EmpresaDto::from).map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    public ResponseEntity<?> crear(@RequestBody EmpresaDto req) {
        try {
            Empresa e = new Empresa();
            e.setNombre(req.nombre());
            e.setDireccion(req.direccion());
            e.setMunicipio(req.municipio());
            e.setProvincia(req.provincia());
            e.setPais(req.pais());
            e.setCodigoPostal(req.codigoPostal());
            e.setCorreo(req.correo());
            e.setTelefono(req.telefono());
            e.setCif(req.cif());
            return ResponseEntity.status(HttpStatus.CREATED).body(EmpresaDto.from(service.crear(e)));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(ex.getMessage());
        }
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> actualizar(@PathVariable Long id, @RequestBody EmpresaDto req) {
        try {
            Empresa upd = new Empresa();
            upd.setNombre(req.nombre());
            upd.setDireccion(req.direccion());
            upd.setMunicipio(req.municipio());
            upd.setProvincia(req.provincia());
            upd.setPais(req.pais());
            upd.setCodigoPostal(req.codigoPostal());
            upd.setCorreo(req.correo());
            upd.setTelefono(req.telefono());
            upd.setCif(req.cif());
            return service.actualizar(id, upd).map(EmpresaDto::from).map(ResponseEntity::ok)
                    .orElse(ResponseEntity.notFound().build());
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(ex.getMessage());
        }
    }

}