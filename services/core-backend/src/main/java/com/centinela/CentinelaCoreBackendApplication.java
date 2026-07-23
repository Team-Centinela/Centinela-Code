package com.centinela;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class CentinelaCoreBackendApplication {

    public static void main(String[] args) {
        SpringApplication.run(CentinelaCoreBackendApplication.class, args);
    }
}
