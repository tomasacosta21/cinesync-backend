package ar.edu.unrn.cinesync.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Configura CORS para que Angular (localhost:4200 en dev, Netlify en prod)
 * pueda consumir la API y recibir eventos SSE.
 *
 * El origen se lee de application.properties y puede sobreescribirse con
 * la variable de entorno CORS_ALLOWED_ORIGINS en Railway.
 */
@Configuration
public class CorsConfig implements WebMvcConfigurer {

    @Value("${cinesync.cors.allowed-origins}")
    private String allowedOrigins;

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOrigins(allowedOrigins.split(","))
                .allowedMethods("GET", "POST", "OPTIONS")
                .allowedHeaders("*")
                .allowCredentials(false);
    }
}
