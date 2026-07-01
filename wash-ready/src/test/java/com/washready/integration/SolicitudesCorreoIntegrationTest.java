package com.washready.integration;

import com.washready.model.Adelanto;
import com.washready.model.Cliente;
import com.washready.model.ClienteNoDeseadoSolicitud;
import com.washready.model.CentroTrabajo;
import com.washready.model.Empresa;
import com.washready.model.Ticket;
import com.washready.model.TicketAnulacion;
import com.washready.model.User;
import com.washready.model.Vehiculo;
import com.washready.repository.AdelantoRepository;
import com.washready.repository.CentroTrabajoRepository;
import com.washready.repository.ClienteNoDeseadoSolicitudRepository;
import com.washready.repository.ClienteRepository;
import com.washready.repository.EmpresaRepository;
import com.washready.repository.TicketAnulacionRepository;
import com.washready.repository.TicketRepository;
import com.washready.repository.UserRepository;
import com.washready.repository.VehiculoRepository;
import com.washready.service.AdelantoService;
import com.washready.service.ClienteNoDeseadoService;
import com.washready.service.ConfigCorreoService;
import com.washready.service.TicketAnulacionService;

import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;

import java.math.BigDecimal;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Cubre, contra una base de datos MySQL real (Testcontainers), los tres flujos
 * que se migraron de SMS a correo electrónico: alta de la solicitud (servicio
 * + envío de email) y resolución vía el enlace mágico "/confirm" expuesto sin
 * sesión. Verifica también que dichos enlaces son accesibles de forma anónima
 * (lo que exige la entrada permitAll de SecurityConfig para estas rutas).
 */
// El indicador de salud de correo (Actuator) exige un bean JavaMailSenderImpl real;
// al sustituir JavaMailSender por un mock, lo desactivamos para no romper el contexto.
@SpringBootTest(properties = "management.health.mail.enabled=false")
@AutoConfigureMockMvc
@Testcontainers
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SolicitudesCorreoIntegrationTest {

    @Container
    @ServiceConnection
    static final MySQLContainer mysql = new MySQLContainer("mysql:8.0");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AdelantoService adelantoService;
    @Autowired
    private TicketAnulacionService ticketAnulacionService;
    @Autowired
    private ClienteNoDeseadoService clienteNoDeseadoService;
    @Autowired
    private ConfigCorreoService configCorreoService;

    @Autowired
    private AdelantoRepository adelantoRepo;
    @Autowired
    private TicketAnulacionRepository ticketAnulacionRepo;
    @Autowired
    private ClienteNoDeseadoSolicitudRepository clienteNoDeseadoRepo;
    @Autowired
    private TicketRepository ticketRepo;
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

    @MockitoBean
    private JavaMailSender mailSender;

    private CentroTrabajo centro;
    private User operario;

    @BeforeAll
    void crearFixturesCompartidas() {
        Empresa empresa = new Empresa();
        empresa.setNombre("Lavados Acme S.L.");
        empresa.setCif("B00000000");
        empresa = empresaRepo.save(empresa);

        centro = new CentroTrabajo();
        centro.setEmpresa(empresa);
        centro.setNombre("Centro Norte");
        centro = centroRepo.save(centro);

        operario = userRepo.save(new User("operario.test", "{noop}clave-test", "ROLE_USER", centro));

        configCorreoService.setDefaultTo("admin@washready.test");
    }

    @BeforeEach
    void prepararMockDeCorreo() {
        reset(mailSender);
        given(mailSender.createMimeMessage())
                .willAnswer(invocation -> new MimeMessage(Session.getInstance(new Properties())));
    }

    // ===================== Adelanto =====================

    @Test
    void crearAdelanto_envia_correo_y_el_enlace_de_confirmacion_resuelve_la_solicitud_sin_sesion() throws Exception {
        Adelanto creado = adelantoService.crear(operario.getId(), new BigDecimal("150.00"), "Juan", "Pérez", "12345678A");

        assertThat(creado.getId()).isNotNull();
        assertThat(creado.getEstado()).isEqualTo(Adelanto.Estado.PENDIENTE);
        assertThat(creado.getDecisionToken()).isNotBlank();
        verify(mailSender).send(any(MimeMessage.class));

        String token = creado.getDecisionToken();

        mockMvc.perform(get("/api/adelantos/confirm").param("token", token))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Decidir solicitud de adelanto")));

        mockMvc.perform(post("/api/adelantos/confirm")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("token", token)
                        .param("accion", "ACEPTAR"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Solicitud de adelanto aceptada")));

        Adelanto actualizado = adelantoRepo.findById(creado.getId()).orElseThrow();
        assertThat(actualizado.getEstado()).isEqualTo(Adelanto.Estado.ACEPTADO);
        assertThat(actualizado.getDecisionToken()).isNull();
    }

    // ===================== Anulación de ticket =====================

    @Test
    void solicitarAnulacionDeTicket_envia_correo_y_el_enlace_anula_el_ticket_sin_sesion() throws Exception {
        Ticket ticket = crearTicketPendienteDePago();

        ticketAnulacionService.solicitar(ticket.getId(), operario.getId(), centro.getId(),
                operario.getUsuario(), centro.getNombre(), "El cliente canceló el servicio");

        verify(mailSender).send(any(MimeMessage.class));

        TicketAnulacion anulacion = ticketAnulacionRepo
                .findByTicketAndEstado(ticket, TicketAnulacion.Estado.PENDIENTE)
                .stream().findFirst().orElseThrow();
        String token = anulacion.getToken();
        assertThat(token).isNotBlank();

        mockMvc.perform(get("/api/tickets/anulacion/confirm").param("token", token))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Confirmar anulación de ticket")));

        mockMvc.perform(post("/api/tickets/anulacion/confirm")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("token", token)
                        .param("accion", "APROBAR"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Ticket anulado correctamente")));

        Ticket ticketActualizado = ticketRepo.findById(ticket.getId()).orElseThrow();
        assertThat(ticketActualizado.getEstado()).isEqualTo(Ticket.Estado.ANULADO);

        TicketAnulacion anulacionResuelta = ticketAnulacionRepo.findById(anulacion.getId()).orElseThrow();
        assertThat(anulacionResuelta.getEstado()).isEqualTo(TicketAnulacion.Estado.APROBADA);
    }

    // ===================== Cliente No deseado =====================

    @Test
    void solicitarClienteNoDeseado_envia_correo_y_el_enlace_marca_al_cliente_sin_sesion() throws Exception {
        Cliente cliente = clienteRepo.save(nuevoCliente("Carlos", "Ruiz"));
        assertThat(cliente.isNoDeseado()).isFalse();

        clienteNoDeseadoService.solicitar(cliente.getId(), operario.getId(), centro.getId(),
                operario.getUsuario(), centro.getNombre(), "Impagos repetidos");

        verify(mailSender).send(any(MimeMessage.class));

        ClienteNoDeseadoSolicitud solicitud = clienteNoDeseadoRepo
                .findByClienteAndEstado(cliente, ClienteNoDeseadoSolicitud.Estado.PENDIENTE)
                .stream().findFirst().orElseThrow();
        String token = solicitud.getToken();
        assertThat(token).isNotBlank();

        mockMvc.perform(get("/api/clientes/no-deseado/confirm").param("token", token))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Confirmar cliente No deseado")));

        mockMvc.perform(post("/api/clientes/no-deseado/confirm")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("token", token)
                        .param("accion", "APROBAR"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Cliente marcado como No deseado")));

        Cliente clienteActualizado = clienteRepo.findById(cliente.getId()).orElseThrow();
        assertThat(clienteActualizado.isNoDeseado()).isTrue();

        ClienteNoDeseadoSolicitud solicitudResuelta = clienteNoDeseadoRepo.findById(solicitud.getId()).orElseThrow();
        assertThat(solicitudResuelta.getEstado()).isEqualTo(ClienteNoDeseadoSolicitud.Estado.APROBADA);
    }

    // ===================== Fixtures =====================

    private Ticket crearTicketPendienteDePago() {
        Cliente cliente = clienteRepo.save(nuevoCliente("Marta", "Gómez"));

        Vehiculo vehiculo = new Vehiculo();
        vehiculo.setMatricula("1234ABC");
        vehiculo.setMarca("Seat");
        vehiculo.setModelo("Ibiza");
        vehiculo = vehiculoRepo.save(vehiculo);

        Ticket ticket = new Ticket();
        ticket.setEstado(Ticket.Estado.PTE_PAGO);
        ticket.setCliente(cliente);
        ticket.setVehiculo(vehiculo);
        ticket.setUsuario(operario);
        ticket.setCentro(centro);
        ticket.setTotal(new BigDecimal("50.00"));
        return ticketRepo.save(ticket);
    }

    private Cliente nuevoCliente(String nombre, String apellido) {
        Cliente c = new Cliente();
        c.setNombre(nombre);
        c.setApellido(apellido);
        return c;
    }
}
