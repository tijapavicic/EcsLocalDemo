package com.example.application;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Spring Boot entry point.
 *
 * <p>Scans all {@code com.example} sub-packages so controllers, components,
 * and configuration classes across all modules are discovered automatically.</p>
 *
 * <p>Run with profile:
 * <pre>
 *   # Local MinIO
 *   java -jar app.jar --spring.profiles.active=local-minio
 *
 *   # AWS S3
 *   java -jar app.jar --spring.profiles.active=aws
 * </pre>
 * </p>
 */
@SpringBootApplication(scanBasePackages = "com.example")
public class Application {

    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }
}

