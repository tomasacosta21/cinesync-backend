package ar.edu.unrn.cinesync.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.web.servlet.config.annotation.AsyncSupportConfigurer;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Habilita soporte asíncrono en Spring MVC, necesario para SSE.
 * Sin esta config, Spring puede cerrar las conexiones SSE prematuramente.
 */
@Configuration
@EnableAsync
public class AsyncConfig implements WebMvcConfigurer {

    @Override
    public void configureAsyncSupport(AsyncSupportConfigurer configurer) {
        configurer.setDefaultTimeout(-1); // sin timeout — el SseEmitter maneja su propio ciclo de vida
    }
}
