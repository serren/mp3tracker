package com.example.resourceprocessor;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.retry.annotation.EnableRetry;

@SpringBootApplication
@EnableRetry
public class ResourceProcessorApplication {

    public static void main(String[] args) {
        SpringApplication.run(ResourceProcessorApplication.class, args);
    }
}
