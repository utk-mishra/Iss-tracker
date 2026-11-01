package com.example.iss;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Entry point for the ISS Location &amp; Pass Predictor backend.
 * Serves the REST API and, in production, the bundled React static build on port 8080.
 */
@SpringBootApplication
public class IssTrackerApplication {

    public static void main(String[] args) {
        SpringApplication.run(IssTrackerApplication.class, args);
    }
}
