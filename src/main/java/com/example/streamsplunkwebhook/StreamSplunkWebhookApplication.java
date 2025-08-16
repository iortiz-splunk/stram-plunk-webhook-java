package com.example.streamsplunkwebhook;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling; // Enable scheduling for background tasks

@SpringBootApplication
@EnableScheduling // Required for @Scheduled tasks, though we'll use a custom thread for BLPOP
public class StreamSplunkWebhookApplication {

    public static void main(String[] args) {
        SpringApplication.run(StreamSplunkWebhookApplication.class, args);
    }

}