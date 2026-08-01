package com.centinela;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@SpringBootApplication(scanBasePackages = "com.centinela")
@EntityScan(basePackages = {
    "com.centinela.ingestion.infrastructure.persistence",
    "com.centinela.scoring.infrastructure.persistence"
})
@EnableJpaRepositories(basePackages = {
    "com.centinela.ingestion.infrastructure.persistence",
    "com.centinela.scoring.infrastructure.persistence"
})
public class CentinelaApplication {

    public static void main(String[] args) {
        SpringApplication.run(CentinelaApplication.class, args);
    }
}
