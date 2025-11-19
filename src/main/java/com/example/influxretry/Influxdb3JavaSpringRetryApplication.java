package com.example.influxretry;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Entry point for the Spring Boot application.
 * <p>
 * This class uses {@link SpringBootApplication} to trigger component scanning
 * and auto‑configuration.
 */
@SpringBootApplication
public class Influxdb3JavaSpringRetryApplication {

    public static void main(String[] args) {
        SpringApplication.run(Influxdb3JavaSpringRetryApplication.class, args);
    }
}
