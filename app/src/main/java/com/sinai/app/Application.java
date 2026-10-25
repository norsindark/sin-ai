package com.sinai.app;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.AutoConfigurationPackage;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = "com.sinai")
@AutoConfigurationPackage(basePackages = "com.sinai")
public class Application {

    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }
}
