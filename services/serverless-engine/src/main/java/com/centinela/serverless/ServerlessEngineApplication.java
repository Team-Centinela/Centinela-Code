package com.centinela.serverless;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class ServerlessEngineApplication {

    public static void main(String[] args) {
        SpringApplication.run(ServerlessEngineApplication.class, args);
    }
}
