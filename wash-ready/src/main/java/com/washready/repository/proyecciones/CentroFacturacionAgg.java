package com.washready.repository.proyecciones;

import java.math.BigDecimal;

public interface CentroFacturacionAgg {
    
    Long getCentroId();
    String getCentroNombre();
    long getTickets();
    BigDecimal getTotal();

}
