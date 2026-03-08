package com.saii.quizapi.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Configuration web : CORS configurable + SPA forwarding pour la production.
 * En production, les fichiers statiques du build Angular sont servis depuis /static.
 * Le SPA forwarding redirige les routes inconnues vers index.html.
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final String allowedOrigins;

    public WebConfig(
            @Value("${saii.cors.allowed-origins:http://localhost:4200}") final String allowedOrigins) {
        this.allowedOrigins = allowedOrigins;
    }

    @Override
    public void addCorsMappings(final CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOrigins(allowedOrigins.split(","))
                .allowedMethods("GET", "POST", "OPTIONS")
                .allowedHeaders("*");
    }

    @Override
    public void addViewControllers(final ViewControllerRegistry registry) {
        // SPA forwarding : toutes les routes non-API redirigent vers index.html
        registry.addViewController("/session/{token}")
                .setViewName("forward:/index.html");
        registry.addViewController("/session/{token}/quiz")
                .setViewName("forward:/index.html");
    }
}
