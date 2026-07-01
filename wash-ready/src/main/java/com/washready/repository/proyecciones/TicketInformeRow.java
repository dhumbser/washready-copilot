package com.washready.repository.proyecciones;

import java.math.BigDecimal;
import java.time.Instant;

public interface TicketInformeRow {

    Long getId();
    Instant getFecha();
    String getReferencia();
    String getCliente();
    String getMatricula();
    BigDecimal getBase();
    BigDecimal getIva();
    BigDecimal getTotal();
    String getCentro();

}
