package com.cryptocopilot;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * CryptoCopilot backend — the Spring Boot application service.
 *
 * <p>Modular monolith (PROJECT.md §2): one container, internal modules
 * ({@code data}, {@code ta}, {@code rag}, {@code trading}, {@code analyst}).
 * It reads Python-owned tables read-only (PROJECT.md §3) and writes only the Java-owned
 * trading tables and the Spring-AI-owned vector store.
 */
@SpringBootApplication
@ConfigurationPropertiesScan
public class CryptoCopilotApplication {
    public static void main(String[] args) {
        SpringApplication.run(CryptoCopilotApplication.class, args);
    }
}
