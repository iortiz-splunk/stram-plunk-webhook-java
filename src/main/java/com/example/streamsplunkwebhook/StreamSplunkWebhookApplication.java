package com.example.streamsplunkwebhook;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration; // Import this
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication(exclude = {RedisAutoConfiguration.class}) // <--- ADD THIS
@EnableScheduling
public class StreamSplunkWebhookApplication {

    public static void main(String[] args) {
        SpringApplication.run(StreamSplunkWebhookApplication.class, args);
    }
}