
package com.washready.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app")
public class AppProperties {

  private String publicBaseUrl;

  /** Vida de los tokens de confirmación por enlace (adelantos, anulaciones, no deseados), en horas */
  private int tokenTtlHours = 24;

  public String getPublicBaseUrl() { return publicBaseUrl; }
  public void setPublicBaseUrl(String publicBaseUrl) { this.publicBaseUrl = publicBaseUrl; }

  public int getTokenTtlHours() { return tokenTtlHours; }
  public void setTokenTtlHours(int tokenTtlHours) { this.tokenTtlHours = tokenTtlHours; }
}
