package com.example.streamsplunkwebhook.service;

import com.example.streamsplunkwebhook.config.AppConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Service
public class RedisQueueService {

    private static final Logger log = LoggerFactory.getLogger(RedisQueueService.class);

    private final RedisTemplate<String, String> redisTemplate;
    private final String webhookQueueName;
    private final String processedSetKey;
    private final long deduplicationWindowSeconds;

    public RedisQueueService(RedisTemplate<String, String> redisTemplate, AppConfig appConfig) {
        this.redisTemplate = redisTemplate;
        this.webhookQueueName = appConfig.getWebhookQueueName();
        this.processedSetKey = "processed_webhooks:" + webhookQueueName; // Key for deduplication set
        this.deduplicationWindowSeconds = appConfig.getDeduplicationWindowSeconds();
    }

    public void enqueueWebhook(String webhookPayload) {
        Long length = redisTemplate.opsForList().rightPush(webhookQueueName, webhookPayload);
        log.info("Webhook queued successfully. New queue length: {}", length);
    }

    public String dequeueWebhook(long timeoutSeconds) {
        String result = redisTemplate.opsForList().leftPop(webhookQueueName, Duration.ofSeconds(timeoutSeconds));
        if (result != null) {
            // Log that an item was dequeued
            log.debug("Dequeued item from Redis queue: {}", result);
            return result;
        }
        // Log that no item was found (only at debug level to avoid clutter)
        log.debug("No item found in Redis queue after {} seconds.", timeoutSeconds);
        return null;
    }

    public boolean isWebhookProcessed(String webhookId) {
        return Boolean.TRUE.equals(redisTemplate.opsForSet().isMember(processedSetKey, webhookId));
    }

    public void markWebhookAsProcessed(String webhookId) {
        redisTemplate.opsForSet().add(processedSetKey, webhookId);
        // Set expiry for the deduplication key
        redisTemplate.expire(processedSetKey, deduplicationWindowSeconds, TimeUnit.SECONDS);
        log.debug("Marked webhook ID {} as processed in Redis for deduplication.", webhookId);
    }
}