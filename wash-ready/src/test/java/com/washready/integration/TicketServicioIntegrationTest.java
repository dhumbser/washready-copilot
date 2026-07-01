package com.washready.integration;

import com.washready.model.CentroTrabajo;
import com.washready.model.Cliente;
import com.washready.model.Empresa;
import com.washready.model.Servicio;
import com.washready.model.Ticket;
import com.washready.model.TicketDetalle;
import com.washready.model.User;
import com.washready.model.Vehiculo;
import com.washready.repository.CentroTrabajoRepository;
import com.washready.repository.ClienteRepository;
import com.washready.repository.EmpresaRepository;
import com.washready.repository.ServicioRepository;
import com.washready.repository.TicketDetalleRepository;
import com.washready.repository.TicketRepository;
import com.washready.repository.UserRepository;
import com.washready.repository.VehiculoRepository;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;

import java.math.BigDecimal;
import java.util.LinkedHashSet;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Cubre, contra una base de datos MySQL real (Testcontainers), la integridad del
 * grafo Ticket-TicketDetalle-Servicio que dejó la unificación "producto" -> "servicio":
 * persistencia en cascada, resolución de la FK fk_det_servicio al recargar con
 * findByIdDeep, recálculo de totales del ticket y la relación N:N servicio_centro.
 */
@SpringBootTest
@Testcontainers
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class TicketServicioIntegrationTest {

    @Container
    @ServiceConnection
    static final MySQLContainer mysql = new MySQLContainer("mysql:8.0");

    @Autowired
    private ServicioRepository servicioRepo;
    @Autowired
    private TicketRepository ticketRepo;
    @Autowired
    private TicketDetalleRepository detalleRepo;
    @Autowired
    private ClienteRepository clienteRepo;
    @Autowired
    private VehiculoRepository vehiculoRepo;
    @Autowired
    private EmpresaRepository empresaRepo;
    @Autowired
    private CentroTrabajoRepository centroRepo;
    @Autowired
    private UserRepository userRepo;

    private CentroTrabajo centro;
    private CentroTrabajo otroCentro;
    private User operario;

    @BeforeAll
    void crearFixturesCompartidas() {
        Empresa empresa = new Empresa();
        empresa.setNombre("Lavados Acme S.L.");
        empresa.setCif("B22222222");
        empresa = empresaRepo.save(empresa);

        centro = new CentroTrabajo();
        centro.setEmpresa(empresa);
        centro.setNombre("Centro Sur");
        centro = centroRepo.save(centro);

        otroCentro = new CentroTrabajo();
        otroCentro.setEmpresa(empresa);
        otroCentro.setNombre("Centro Este");
        otroCentro = centroRepo.save(otroCentro);

        operario = userRepo.save(new User("operario.servicios", "{noop}clave-test", "ROLE_USER", centro));
    }

    // ===================== Ticket + TicketDetalle + Servicio =====================

    @Test
    void persistirTicketConDetalles_resuelve_servicio_por_fk_y_recalcula_totales_al_recargar_con_findByIdDeep() {
        Servicio lavado = servicioRepo.save(new Servicio("Lavado completo", new BigDecimal("25.00"), 10, Servicio.Tipo.GENERAL));
        Servicio encerado = servicioRepo.save(new Servicio("Encerado a mano", new BigDecimal("15.50"), null, Servicio.Tipo.TIPO_1));

        Ticket ticket = nuevoTicketBase("9001ABC", "Lucía", "Navarro");

        TicketDetalle detalleLavado = new TicketDetalle();
        detalleLavado.setServicio(lavado);
        detalleLavado.setCantidad(2);
        detalleLavado.setPrecio(lavado.getImporte());
        ticket.addDetalle(detalleLavado);

        TicketDetalle detalleEncerado = new TicketDetalle();
        detalleEncerado.setServicio(encerado);
        detalleEncerado.setDescripcionServicio("Encerado a mano (oferta)");
        detalleEncerado.setCantidad(1);
        detalleEncerado.setPrecio(encerado.getImporte());
        ticket.addDetalle(detalleEncerado);

        Long ticketId = ticketRepo.save(ticket).getId();
        assertThat(ticketId).isNotNull();

        Ticket recargado = ticketRepo.findByIdDeep(ticketId).orElseThrow();

        // total = 2*25.00 + 1*15.50 = 65.50; totalSinIva = 65.50 / 1.21 (iva 21% por defecto) = 54.13
        assertThat(recargado.getTotal()).isEqualByComparingTo("65.50");
        assertThat(recargado.getTotalSinIva()).isEqualByComparingTo("54.13");
        assertThat(recargado.getDetalles()).hasSize(2);

        TicketDetalle lineaLavado = detallePara(recargado, lavado.getId());
        assertThat(lineaLavado.getTicket().getId()).isEqualTo(ticketId);
        assertThat(lineaLavado.getServicio().getId()).isEqualTo(lavado.getId());
        assertThat(lineaLavado.getServicio().getImporte()).isEqualByComparingTo("25.00");
        assertThat(lineaLavado.getServicio().getTipo()).isEqualTo(Servicio.Tipo.GENERAL);
        // descripcionServicio se dejó en blanco -> @PrePersist la copia desde Servicio.descripcion
        assertThat(lineaLavado.getDescripcionServicio()).isEqualTo("Lavado completo");

        TicketDetalle lineaEncerado = detallePara(recargado, encerado.getId());
        // descripcionServicio explícita: el ticket conserva su propia copia, no la del catálogo
        assertThat(lineaEncerado.getDescripcionServicio()).isEqualTo("Encerado a mano (oferta)");

        assertThat(detalleRepo.findByTicketId(ticketId))
                .extracting(TicketDetalle::getId)
                .containsExactlyInAnyOrder(lineaLavado.getId(), lineaEncerado.getId());
    }

    @Test
    void eliminarTicket_propaga_el_borrado_a_sus_detalles_sin_afectar_al_catalogo_de_servicios() {
        Servicio servicio = servicioRepo.save(new Servicio("Aspirado interior", new BigDecimal("8.00"), 5, Servicio.Tipo.TIPO_2));

        Ticket ticket = nuevoTicketBase("9002ABC", "Marcos", "Iglesias");
        TicketDetalle detalle = new TicketDetalle();
        detalle.setServicio(servicio);
        detalle.setCantidad(1);
        detalle.setPrecio(servicio.getImporte());
        ticket.addDetalle(detalle);
        ticket = ticketRepo.save(ticket);

        Long ticketId = ticket.getId();
        Long detalleId = ticket.getDetalles().get(0).getId();
        assertThat(detalleRepo.findById(detalleId)).isPresent();

        ticketRepo.deleteById(ticketId);

        assertThat(ticketRepo.findById(ticketId)).isEmpty();
        // cascade = ALL + orphanRemoval sobre Ticket.detalles: la línea desaparece con el ticket
        assertThat(detalleRepo.findById(detalleId)).isEmpty();
        // fk_det_servicio es opcional y sin cascada: el catálogo de servicios no se ve afectado
        assertThat(servicioRepo.findById(servicio.getId())).isPresent();
    }

    // ===================== Servicio =====================

    @Test
    void servicioRepository_filtra_por_centro_a_traves_de_la_relacion_servicio_centro() {
        Servicio exclusivoCentro = new Servicio("Pulido de faros", new BigDecimal("12.00"), null, Servicio.Tipo.TIPO_3);
        exclusivoCentro.setCentros(new LinkedHashSet<>(List.of(centro)));
        servicioRepo.save(exclusivoCentro);

        Servicio paraTodos = new Servicio("Limpieza de tapicería", new BigDecimal("40.00"), null, Servicio.Tipo.GENERAL);
        paraTodos.setDisponibleTodosCentros(true);
        servicioRepo.save(paraTodos);

        Servicio deOtroCentro = new Servicio("Cambio de aceite", new BigDecimal("60.00"), null, Servicio.Tipo.GENERAL);
        deOtroCentro.setCentros(new LinkedHashSet<>(List.of(otroCentro)));
        servicioRepo.save(deOtroCentro);

        List<Servicio> visiblesEnCentro = servicioRepo.findFilteredWithCentros(centro.getId(), null, null, null, null);

        assertThat(visiblesEnCentro)
                .extracting(Servicio::getDescripcion)
                .contains("Pulido de faros", "Limpieza de tapicería")
                .doesNotContain("Cambio de aceite");

        Servicio recargado = servicioRepo.findById(exclusivoCentro.getId()).orElseThrow();
        assertThat(recargado.getCentros())
                .extracting(CentroTrabajo::getId)
                .containsExactly(centro.getId());
    }

    // ===================== Fixtures =====================

    private TicketDetalle detallePara(Ticket ticket, Long servicioId) {
        return ticket.getDetalles().stream()
                .filter(d -> d.getServicio() != null && servicioId.equals(d.getServicio().getId()))
                .findFirst()
                .orElseThrow();
    }

    private Ticket nuevoTicketBase(String matricula, String nombreCliente, String apellidoCliente) {
        Cliente cliente = clienteRepo.save(nuevoCliente(nombreCliente, apellidoCliente));

        Vehiculo vehiculo = new Vehiculo();
        vehiculo.setMatricula(matricula);
        vehiculo.setMarca("Renault");
        vehiculo.setModelo("Clio");
        vehiculo = vehiculoRepo.save(vehiculo);

        Ticket ticket = new Ticket();
        ticket.setEstado(Ticket.Estado.PAGADO);
        ticket.setCliente(cliente);
        ticket.setVehiculo(vehiculo);
        ticket.setUsuario(operario);
        ticket.setCentro(centro);
        return ticket;
    }

    private Cliente nuevoCliente(String nombre, String apellido) {
        Cliente c = new Cliente();
        c.setNombre(nombre);
        c.setApellido(apellido);
        return c;
    }
}
