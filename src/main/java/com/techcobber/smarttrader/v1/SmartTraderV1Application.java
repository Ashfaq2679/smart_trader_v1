package com.techcobber.smarttrader.v1;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.data.mongodb.config.EnableMongoAuditing;

@SpringBootApplication
@EnableScheduling
@EnableMongoAuditing
public class SmartTraderV1Application {

    public static void main(String[] args) {
        SpringApplication.run(SmartTraderV1Application.class, args);
    }

}