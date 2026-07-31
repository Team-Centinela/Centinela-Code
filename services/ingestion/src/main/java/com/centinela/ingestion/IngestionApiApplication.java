package com.centinela.ingestion;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@ComponentScan(basePackages = {"com.centinela.ingestion", "com.centinela.shared.messaging"})
@EntityScan(basePackages = {"com.centinela.ingestion", "com.centinela.shared.messaging"})
@EnableJpaRepositories(basePackages = {"com.centinela.ingestion", "com.centinela.shared.messaging"})
@EnableScheduling
public class IngestionApiApplication {

    public static void main(String[] args) {
        SpringApplication.run(IngestionApiApplication.class, args);
    }
}
