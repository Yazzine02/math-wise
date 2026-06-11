package com.mathwise.content;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/**
 * Curriculum service (:9092). Serves courses/lessons and owns the seed data
 * (DataSeeder). Read-mostly: after seeding, this service only answers GETs.
 */
@SpringBootApplication(scanBasePackages = {"com.mathwise.content", "com.mathwise.common"})
@EntityScan("com.mathwise.common.entity")
@EnableJpaRepositories("com.mathwise.common.repository")
public class ContentServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(ContentServiceApplication.class, args);
    }
}
