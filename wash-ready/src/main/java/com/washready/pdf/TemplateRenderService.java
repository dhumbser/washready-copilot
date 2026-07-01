package com.washready.pdf;

import java.util.Locale;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

@Service
public class TemplateRenderService {
    
    private final TemplateEngine templateEngine;
    
    public TemplateRenderService(TemplateEngine templateEngine) {
        this.templateEngine = templateEngine;
    }

    // Atajo específico para el ticket
    public String render(String template, Map<String, Object> model, Locale locale) {
        Context ctx = new Context(locale);
        if (model != null) {
            model.forEach(ctx::setVariable);
        }
        return templateEngine.process(template, ctx);
    }

}
