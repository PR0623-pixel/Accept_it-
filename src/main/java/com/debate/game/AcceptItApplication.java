
package com.debate.game;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class AcceptItApplication {

    public static void main(String[] args) {
        SpringApplication.run(AcceptItApplication.class, args);
    }
}
