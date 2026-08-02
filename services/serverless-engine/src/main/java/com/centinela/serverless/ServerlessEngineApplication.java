package com.centinela.serverless;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@ComponentScan(basePackages = {"com.centinela.serverless", "com.centinela.shared.messaging"})
@EntityScan(basePackages = {"com.centinela.serverless", "com.centinela.shared.messaging"})
@EnableJpaRepositories(basePackages = {"com.centinela.serverless", "com.centinela.shared.messaging"})
@EnableScheduling
public class ServerlessEngineApplication {

    public static void main(String[] args) {
        SpringApplication.run(ServerlessEngineApplication.class, args);
    }
}
