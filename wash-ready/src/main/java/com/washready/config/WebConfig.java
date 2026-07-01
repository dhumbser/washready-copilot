package com.washready.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Override
    public void addViewControllers(ViewControllerRegistry registry) {
        registry.addViewController("/login.html").setViewName("login");
        registry.addViewController("/").setViewName("index");
        registry.addViewController("/index").setViewName("index");
        registry.addViewController("/index.html").setViewName("index");
        registry.addViewController("/clientes").setViewName("clientes/index");
        registry.addViewController("/vehiculos").setViewName("vehiculos/index");
        registry.addViewController("/servicios").setViewName("servicios/index");
        registry.addViewController("/informes").setViewName("informes/index");
        registry.addViewController("/adelantos").setViewName("adelantos/index");
        registry.addViewController("/admin/dashboard").setViewName("admin/dashboard");
        registry.addViewController("/admin/usuarios").setViewName("admin/usuarios");
        registry.addViewController("/admin/centros").setViewName("admin/centros");
        registry.addViewController("/admin/empresas").setViewName("admin/empresas");
        registry.addViewController("/tickets/buscar").setViewName("tickets/buscar");
        registry.addViewController("/tickets/crear").setViewName("tickets/crear");
        registry.addViewController("/pdf_viewer.html").setViewName("shared/pdf_viewer");
    }

}