package com.washready.model;

import jakarta.persistence.*;

@Entity
@Table(name = "config_correo")
public class ConfigCorreo {

  @Id
  @Column(name = "clave", length = 100)
  private String clave;

  @Column(name = "valor", length = 1000)
  private String valor;

  public ConfigCorreo() {}
  public ConfigCorreo(String clave, String valor) {
    this.clave = clave;
    this.valor = valor;
  }

  public String getClave() { return clave; }
  public void setClave(String clave) { this.clave = clave; }
  public String getValor() { return valor; }
  public void setValor(String valor) { this.valor = valor; }
}
