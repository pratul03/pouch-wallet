package com.pockt;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class PocktApplication {
    public static void main(String[] args) {
        SpringApplication.run(PocktApplication.class, args);
    }
}
