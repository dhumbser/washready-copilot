package com.washready.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import com.washready.model.CentroTrabajo;
import com.washready.model.Empresa;
import com.washready.model.User;
import com.washready.repository.CentroTrabajoRepository;
import com.washready.repository.EmpresaRepository;
import com.washready.repository.UserRepository;

@Component
public class AdminBootstrapRunner implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminBootstrapRunner.class);

    private static final String BOOTSTRAP_CIF     = "B12345678";
    private static final String BOOTSTRAP_EMPRESA = "Wash & Ready SL";
    private static final String BOOTSTRAP_CENTRO  = "Central";

    private final UserRepository userRepository;
    private final EmpresaRepository empresaRepository;
    private final CentroTrabajoRepository centroRepository;
    private final PasswordEncoder passwordEncoder;
    private final String usuario;
    private final String password;

    public AdminBootstrapRunner(UserRepository userRepository,
            EmpresaRepository empresaRepository,
            CentroTrabajoRepository centroRepository,
            PasswordEncoder passwordEncoder,
            @Value("${app.bootstrap-admin.usuario}") String usuario,
            @Value("${app.bootstrap-admin.password}") String password) {
        this.userRepository = userRepository;
        this.empresaRepository = empresaRepository;
        this.centroRepository = centroRepository;
        this.passwordEncoder = passwordEncoder;
        this.usuario = usuario;
        this.password = password;
    }

    @Override
    public void run(String... args) {
        Empresa empresa = empresaRepository.findByCif(BOOTSTRAP_CIF)
                .orElseGet(() -> {
                    Empresa e = new Empresa(BOOTSTRAP_EMPRESA, null, null, null, null, null, BOOTSTRAP_CIF);
                    Empresa saved = empresaRepository.save(e);
                    log.info("Empresa de prueba '{}' creada (CIF: {}).", BOOTSTRAP_EMPRESA, BOOTSTRAP_CIF);
                    return saved;
                });

        CentroTrabajo centro = centroRepository.findByEmpresaId(empresa.getId()).stream()
                .filter(c -> BOOTSTRAP_CENTRO.equals(c.getNombre()))
                .findFirst()
                .orElseGet(() -> {
                    CentroTrabajo c = new CentroTrabajo(BOOTSTRAP_CENTRO, null, null, null, null, null, empresa);
                    CentroTrabajo saved = centroRepository.save(c);
                    log.info("Centro de prueba '{}' creado para la empresa '{}'.", BOOTSTRAP_CENTRO, BOOTSTRAP_EMPRESA);
                    return saved;
                });

        if (userRepository.existsByUsuario(usuario)) {
            userRepository.findByUsuario(usuario).ifPresent(u -> {
                if (u.getEmpresa() == null || u.getCentroTrabajo() == null) {
                    u.setEmpresa(empresa);
                    u.setCentroTrabajo(centro);
                    userRepository.save(u);
                    log.info("Administrador '{}' vinculado a empresa '{}' y centro '{}'.",
                            usuario, BOOTSTRAP_EMPRESA, BOOTSTRAP_CENTRO);
                }
            });
            return;
        }

        userRepository.save(new User(usuario, passwordEncoder.encode(password), "ROLE_ADMIN", centro));
        log.warn("Administrador inicial '{}' creado con la contraseña de arranque definida en application.properties. "
                + "Cámbiala tras el primer inicio de sesión.", usuario);
    }

}