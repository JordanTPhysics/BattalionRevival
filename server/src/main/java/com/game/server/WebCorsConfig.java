package com.game.server;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.lang.NonNull;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebCorsConfig {

    /**
     * Comma-separated list of allowed origins for browser REST calls (e.g. {@code http://localhost:5173}).
     * Default includes the usual Vite dev server. Use {@code *} only for development.
     */
    @Bean
    public WebMvcConfigurer battalionCorsConfigurer(
        @Value(
            "${battalion.cors.allowed-origins:http://localhost:3000,http://127.0.0.1:3000,https://battalionrevival.netlify.app/}"
        ) String allowedOrigins
    ) {
        String[] origins = splitCsv(allowedOrigins);
        return new WebMvcConfigurer() {
            @Override
            public void addCorsMappings(@NonNull CorsRegistry registry) {
                registry.addMapping("/api/**")
                    .allowedOrigins(origins)
                    .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                    .allowedHeaders("*");
            }
        };
    }

    private static String[] splitCsv(String raw) {
        if (raw == null || raw.isBlank()) {
            return new String[] {"http://localhost:5173"};
        }
        String[] parts = raw.split(",");
        for (int i = 0; i < parts.length; i++) {
            parts[i] = parts[i].trim();
        }
        return parts;
    }
}
