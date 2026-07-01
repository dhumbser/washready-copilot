package com.washready.repository.proyecciones;

import java.math.BigDecimal;

public interface ResumenInforme {
    
    long getTotalTickets();
    BigDecimal getTotalBase();
    BigDecimal getTotalIva();
    BigDecimal getTotalConIva();

}
