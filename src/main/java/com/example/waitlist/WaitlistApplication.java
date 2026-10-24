package com.example.waitlist;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/** Entry point. {@code @ConfigurationPropertiesScan} registers {@code OfferingProperties}. */
@SpringBootApplication
@ConfigurationPropertiesScan
public class WaitlistApplication {
    public static void main(String[] args) {
        SpringApplication.run(WaitlistApplication.class, args);
    }
}
