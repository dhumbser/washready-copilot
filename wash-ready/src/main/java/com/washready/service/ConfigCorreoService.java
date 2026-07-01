package com.washready.service;

import com.washready.model.ConfigCorreo;
import com.washready.repository.ConfigCorreoRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
public class ConfigCorreoService {

  public static final String KEY_DEFAULT_TO = "mail.default_to";

  private final ConfigCorreoRepository repo;

  public ConfigCorreoService(ConfigCorreoRepository repo) {
    this.repo = repo;
  }

  @Transactional(readOnly = true)
  public Optional<String> get(String clave) {
    return repo.findById(clave).map(ConfigCorreo::getValor);
  }

  @Transactional
  public void set(String clave, String valor) {
    repo.save(new ConfigCorreo(clave, valor));
  }

  // Atajos específicos
  @Transactional(readOnly = true)
  public Optional<String> getDefaultTo() {
    return get(KEY_DEFAULT_TO);
  }

  @Transactional
  public void setDefaultTo(String email) {
    set(KEY_DEFAULT_TO, email);
  }
}
