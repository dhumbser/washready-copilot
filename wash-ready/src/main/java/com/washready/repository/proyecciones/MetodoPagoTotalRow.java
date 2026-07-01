package com.washready.repository.proyecciones;

import java.math.BigDecimal;

import com.washready.model.Ticket;

public interface MetodoPagoTotalRow {
    
    Ticket.MetodoPago getMetodoPago();
    BigDecimal getTotal();

}
