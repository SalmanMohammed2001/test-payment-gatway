package com.exotic.payment;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class CybersourcePaymentApplication {

    public static void main(String[] args) {
        SpringApplication.run(CybersourcePaymentApplication.class, args);
    }
}
