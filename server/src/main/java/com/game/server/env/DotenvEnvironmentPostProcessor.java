package com.game.server.env;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

import io.github.cdimascio.dotenv.Dotenv;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.MutablePropertySources;

/**
 * Loads optional dotenv files into the Spring {@link ConfigurableEnvironment}.
 * <ul>
 *   <li>OS environment variables and JVM system properties always win over this file.</li>
 *   <li>Entries override values from classpath {@code application.properties}.</li>
 *   <li>Set {@code BATTALION_DOTENV_DISABLED=true} to skip (Gradle {@code test} sets this).</li>
 * </ul>
 */
@Order(Ordered.HIGHEST_PRECEDENCE)
public class DotenvEnvironmentPostProcessor implements EnvironmentPostProcessor {

    public static final String DOTENV_DISABLED_ENV = "BATTALION_DOTENV_DISABLED";
    public static final String DOTENV_FILE_ENV = "BATTALION_DOTENV_FILE";

    private static final String PROPERTY_SOURCE_NAME = "battalion-dotenv";

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        if (Boolean.parseBoolean(environment.getProperty(DOTENV_DISABLED_ENV, "false"))) {
            return;
        }
        Path dotenvPath = resolveDotenvPath(environment);
        if (dotenvPath == null || !Files.isRegularFile(dotenvPath)) {
            return;
        }
        Dotenv dotenv = Dotenv.configure()
            .directory(dotenvPath.getParent().toString())
            .filename(dotenvPath.getFileName().toString())
            .ignoreIfMalformed()
            .ignoreIfMissing()
            .load();

        Map<String, Object> map = new LinkedHashMap<>();
        dotenv.entries().forEach(e -> {
            String key = e.getKey();
            String value = e.getValue();
            if (key == null || key.isBlank() || value == null || value.isBlank()) {
                return;
            }
            if (System.getenv(key) != null) {
                return;
            }
            if (System.getProperty(key) != null) {
                return;
            }
            map.put(key, value);
        });
        if (map.isEmpty()) {
            return;
        }
        MutablePropertySources sources = environment.getPropertySources();
        if (sources.contains(PROPERTY_SOURCE_NAME)) {
            return;
        }
        // After system environment: real deployment env wins; dotenv beats application.properties.
        if (sources.contains("systemEnvironment")) {
            sources.addAfter("systemEnvironment", new MapPropertySource(PROPERTY_SOURCE_NAME, map));
        } else {
            sources.addFirst(new MapPropertySource(PROPERTY_SOURCE_NAME, map));
        }
    }

    private static Path resolveDotenvPath(ConfigurableEnvironment environment) {
        String override = environment.getProperty(DOTENV_FILE_ENV);
        if (override != null && !override.isBlank()) {
            return Path.of(override.trim()).toAbsolutePath().normalize();
        }
        String userDir = System.getProperty("user.dir", ".");
        return Path.of(userDir, ".env").toAbsolutePath().normalize();
    }
}
