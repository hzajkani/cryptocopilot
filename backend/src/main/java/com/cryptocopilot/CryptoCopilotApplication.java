package com.cryptocopilot;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * CryptoCopilot backend — the Spring Boot application service.
 *
 * <p>Modular monolith (PROJECT.md §2): one container, internal modules
 * ({@code data}, {@code ta}, and later {@code rag}/{@code trading}/{@code analyst}).
 * It reads Python-owned tables read-only and never writes to them (PROJECT.md §3).
 */
@SpringBootApplication
public class CryptoCopilotApplication {
    public static void main(String[] args) {
        SpringApplication.run(CryptoCopilotApplication.class, args);
    }
}
