package com.example.streamsplunkwebhook.service;

import com.example.streamsplunkwebhook.config.AppConfig;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import java.time.Instant;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

@Service
public class SplunkForwarderService {

    private static final Logger log = LoggerFactory.getLogger(SplunkForwarderService.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();

    private final RedisQueueService redisQueueService;
    private final RestTemplate restTemplate;
    private final AppConfig appConfig;

    private ExecutorService executorService;
    private volatile boolean running = true;

    public SplunkForwarderService(RedisQueueService redisQueueService, RestTemplate restTemplate, AppConfig appConfig) {
        this.redisQueueService = redisQueueService;
        this.restTemplate = restTemplate;
        this.appConfig = appConfig;
    }

    @PostConstruct
    public void startWorker() {
        log.info("Splunk Forwarder Worker started. Waiting for messages...");
        executorService = Executors.newSingleThreadExecutor();
        executorService.submit(this::pollQueueAndForward);
    }

    @PreDestroy
    public void stopWorker() {
        log.info("Shutting down Splunk Forwarder Worker...");
        running = false;
        executorService.shutdown();
        try {
            if (!executorService.awaitTermination(5, TimeUnit.SECONDS)) {
                executorService.shutdownNow();
            }
        } catch (InterruptedException e) {
            executorService.shutdownNow();
            Thread.currentThread().interrupt();
        }
        log.info("Splunk Forwarder Worker stopped.");
    }

    private void pollQueueAndForward() {
        while (running) {
            try {
                String rawWebhookData = redisQueueService.dequeueWebhook(appConfig.getPollIntervalMs() / 1000); // Convert ms to seconds
                if (rawWebhookData != null) {
                    processAndForward(rawWebhookData);
                } else {
                    // log.debug("No messages in queue, waiting..."); // Too noisy for debug
                    // No need to sleep here as dequeueWebhook is blocking with a timeout
                }
            } catch (Exception e) {
                log.error("An unexpected error occurred in worker: {}", e.getMessage(), e);
                try {
                    Thread.sleep(1000); // Prevent busy-looping on persistent errors
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    running = false;
                }
            }
        }
    }

    private void processAndForward(String rawWebhookData) {
        try {
            JsonNode webhookData = objectMapper.readTree(rawWebhookData);
            String webhookId = webhookData.path("x_webhook_id").asText();

            if (webhookId != null && redisQueueService.isWebhookProcessed(webhookId)) {
                log.info("Skipping duplicate webhook ID {} (already processed or in window).", webhookId);
                return;
            }

            log.info("Processing webhook ID: {}", webhookId);
            boolean success = forwardToSplunk(webhookData);

            if (success && webhookId != null) {
                redisQueueService.markWebhookAsProcessed(webhookId);
            } else if (!success) {
                log.error("Failed to send webhook ID {} to Splunk. Consider re-queueing if necessary.", webhookId);
                // Re-queueing logic (e.g., to a dead-letter queue or back to original with delay)
                // For simplicity, we just log and move on.
            }
        } catch (Exception e) {
            log.error("Error processing or forwarding webhook data: {}", e.getMessage(), e);
        }
    }

    private boolean forwardToSplunk(JsonNode webhookData) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(appConfig.getSplunkHecToken()); // Splunk HEC uses Bearer token auth

        // Construct Splunk HEC payload
        ObjectNode splunkPayload = objectMapper.createObjectNode();
        splunkPayload.put("event", webhookData.path("original_payload")); // Original payload is nested
        splunkPayload.put("time", webhookData.path("timestamp").asLong()); // Unix timestamp
        splunkPayload.put("host", "stream-webhook-forwarder-java");
        splunkPayload.put("source", "stream-chat-webhook");
        splunkPayload.put("sourcetype", "_json"); // Or a specific sourcetype for your Stream data

        // Add custom fields
        ObjectNode fields = objectMapper.createObjectNode();
        fields.put("x_webhook_id", webhookData.path("x_webhook_id").asText());
        fields.put("x_api_key", webhookData.path("x_api_key").asText());
        splunkPayload.set("fields", fields);

        HttpEntity<String> request = new HttpEntity<>(splunkPayload.toString(), headers);

        try {
            // TODO: For production, configure RestTemplate to handle SSL verification based on appConfig.isSplunkHecSslVerify()
            // This might involve using HttpClientBuilder and setting SSLContext.
            ResponseEntity<String> response = restTemplate.postForEntity(appConfig.getSplunkHecUrl(), request, String.class);
            log.info("Successfully forwarded webhook ID {} to Splunk. Status: {}", webhookData.path("x_webhook_id").asText(), response.getStatusCode());
            return true;
        } catch (HttpClientErrorException | HttpServerErrorException e) {
            log.error("HTTP error forwarding to Splunk for webhook ID {}: {} - {}", webhookData.path("x_webhook_id").asText(), e.getStatusCode(), e.getResponseBodyAsString());
            return false;
        } catch (ResourceAccessException e) {
            log.error("Network/Connection error forwarding to Splunk for webhook ID {}: {}", webhookData.path("x_webhook_id").asText(), e.getMessage());
            return false;
        } catch (Exception e) {
            log.error("Unexpected error forwarding to Splunk for webhook ID {}: {}", webhookData.path("x_webhook_id").asText(), e.getMessage(), e);
            return false;
        }
    }
}