package com.washready.pdf;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;

@Service
public class PdfService {
    
    // Convierte una cadena HTML a bytes PDF
    public byte[] htmlToPdf(String html) {
        String baseUrl = getClass().getResource("/").toExternalForm();
        return htmlToPdf(html, baseUrl);
    }

    // Variante con baseURL en caso de que se utilicen rutas relativas
    public byte[] htmlToPdf(String html, String baseUrl) {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            PdfRendererBuilder builder = new PdfRendererBuilder();
            builder.useFastMode();
            builder.withHtmlContent(html, baseUrl);
            builder.toStream(baos);
            builder.run();
            return baos.toByteArray();
        } catch (Exception e) {
            throw new IllegalStateException("Error generando PDF", e);
        }
    }

    // Carga un HTML desde classpath y lo convierte a PDF
    public byte[] htmlResourceToPdf(String classpathHtml) {
        try {
            ClassPathResource res = new ClassPathResource(classpathHtml);
            try (InputStream is = res.getInputStream()) {
                String html = new String(is.readAllBytes(), StandardCharsets.UTF_8);
                // base URL para que <img src="logo.png"> o <link href="styles.css"> funcionen:
                String baseUrl = res.getURL().toString().replace(res.getFilename(), "");
                return htmlToPdf(html, baseUrl);
            }
        } catch (Exception e) {
                throw new IllegalStateException("Error generando PDF desde " + classpathHtml, e);
        }
    }

}
