package com.scrumble.gudocs;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

@SpringBootApplication
@EnableJpaAuditing
public class GudocsApplication {

    public static void main(String[] args) {
        SpringApplication.run(GudocsApplication.class, args);
    }

}
