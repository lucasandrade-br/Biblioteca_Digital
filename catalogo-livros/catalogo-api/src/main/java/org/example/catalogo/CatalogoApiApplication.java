package org.example.catalogo;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class CatalogoApiApplication {

    public static void main(String[] args) {
        SpringApplication.run(CatalogoApiApplication.class, args);
    }

}
