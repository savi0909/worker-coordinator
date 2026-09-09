package com.example.workercoordinator;

import com.example.workercoordinator.config.CoordinatorProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties(CoordinatorProperties.class)
public class WorkerCoordinatorApplication {
    public static void main(String[] args) {
        SpringApplication.run(WorkerCoordinatorApplication.class, args);
    }
}
