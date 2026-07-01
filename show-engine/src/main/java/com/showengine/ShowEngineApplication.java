package com.showengine;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableAsync
public class ShowEngineApplication {

    public static void main(String[] args) {
        SpringApplication.run(ShowEngineApplication.class, args);
    }
}
