package com.example.streamsplunkwebhook.service;

import com.example.streamsplunkwebhook.config.AppConfig;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;

import java.net.MalformedURLException; // Import for URL parsing
import java.net.URL; // Import for URL parsing
import java.time.Instant;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

@Service
public class SplunkForwarderService {

    private static final Logger log = LoggerFactory.getLogger(SplunkForwarderService.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();

    private final RedisQueueService redisQueueService;
    private final WebClient webClient;
    private final AppConfig appConfig;

    private ExecutorService executorService;
    private volatile boolean running = true;

    public SplunkForwarderService(RedisQueueService redisQueueService, WebClient.Builder webClientBuilder, AppConfig appConfig) {
        this.redisQueueService = redisQueueService;
        this.appConfig = appConfig;
        // Set the base URL of the WebClient to just the scheme, host, and port
        // The full path /services/collector/event will be used in the .uri() method
        this.webClient = webClientBuilder.baseUrl(extractBaseUrl(appConfig.getSplunkHecUrl())).build();
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
                String rawWebhookData = redisQueueService.dequeueWebhook(appConfig.getPollIntervalMs() / 1000);
                if (rawWebhookData != null) {
                    processAndForward(rawWebhookData);
                }
            } catch (Exception e) {
                log.error("An unexpected error occurred in worker: {}", e.getMessage(), e);
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    running = false;
                }
            }
        }
    }

    private void processAndForward(String rawWebhookData) {
        String webhookId = "unknown";
        try {
            JsonNode webhookData = objectMapper.readTree(rawWebhookData);
            webhookId = webhookData.path("x_webhook_id").asText();

            log.info("Processing webhook ID: {}", webhookId);

            if (webhookId != null && redisQueueService.isWebhookProcessed(webhookId)) {
                log.info("Skipping webhook ID {} because it was already processed (deduplication).", webhookId);
                return;
            }

            boolean success = forwardToSplunk(webhookData);

            if (success && webhookId != null) {
                redisQueueService.markWebhookAsProcessed(webhookId);
                log.info("Successfully processed and marked webhook ID {} as processed.", webhookId);
            } else if (!success) {
                log.error("Failed to send webhook ID {} to Splunk. Consider re-queueing if necessary.", webhookId);
            }
        } catch (Exception e) {
            log.error("Error processing or forwarding webhook data for ID {}: {}", webhookId, e.getMessage(), e);
        }
    }

    private boolean forwardToSplunk(JsonNode webhookData) {
        // Construct Splunk HEC payload
        ObjectNode splunkPayload = objectMapper.createObjectNode();
        splunkPayload.put("event", webhookData.path("original_payload"));
        splunkPayload.put("time", webhookData.path("timestamp").asLong());
        splunkPayload.put("host", "stream-webhook-forwarder-java");
        splunkPayload.put("source", "stream-chat-webhook");
        splunkPayload.put("sourcetype", "stream:chat:webhook");

        ObjectNode fields = objectMapper.createObjectNode();
        fields.put("x_webhook_id", webhookData.path("x_webhook_id").asText());
        fields.put("x_api_key", webhookData.path("x_api_key").asText());
        splunkPayload.set("fields", fields);

        String webhookIdForLogging = webhookData.path("x_webhook_id").asText();

        log.debug("Attempting to send to Splunk HEC URL: {}", appConfig.getSplunkHecUrl());
        log.debug("Splunk HEC Token present: {}", (appConfig.getSplunkHecToken() != null && !appConfig.getSplunkHecToken().isEmpty()));
        log.debug("Splunk HEC Token length: {}", (appConfig.getSplunkHecToken() != null ? appConfig.getSplunkHecToken().length() : 0));
        log.debug("Request Body (first 500 chars): {}", splunkPayload.toString().substring(0, Math.min(splunkPayload.toString().length(), 500)));

        try {
            String responseBody = webClient.post()
                    .uri("/services/collector/event") // Use the full path here
                    .header(HttpHeaders.AUTHORIZATION, "Splunk " + appConfig.getSplunkHecToken())
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(splunkPayload.toString())
                    .retrieve()
                    .onStatus(status -> status.isError(), response -> {
                        return response.bodyToMono(String.class)
                                .flatMap(errorBody -> {
                                    log.error("Splunk HEC returned error status {} for webhook ID {}. Response Body: {}",
                                            response.statusCode().value(), webhookIdForLogging, errorBody);
                                    return Mono.error(new WebClientResponseException(
                                            "Splunk HEC Error",
                                            response.statusCode().value(),
                                            response.statusCode().toString(),
                                            response.headers().asHttpHeaders(),
                                            errorBody.getBytes(),
                                            null
                                    ));
                                });
                    })
                    .bodyToMono(String.class)
                    .block();

            log.info("Successfully forwarded webhook ID {} to Splunk. Response: {}", webhookIdForLogging, responseBody);
            return true;
        } catch (WebClientResponseException e) {
            log.error("WebClient HTTP error forwarding to Splunk for webhook ID {}: {} - {}",
                      webhookIdForLogging, e.getStatusCode(), e.getResponseBodyAsString());
            log.error("Splunk Response Headers: {}", e.getHeaders());
            return false;
        } catch (Exception e) {
            log.error("Unexpected error forwarding to Splunk for webhook ID {}: {}", webhookIdForLogging, e.getMessage(), e);
            return false;
        }
    }

    /**
     * Extracts the scheme, host, and port from a full URL.
     * Example: "https://http-inputs.example.com:8088/services/collector/event" -> "https://http-inputs.example.com:8088"
     * @param fullUrl The complete URL string.
     * @return The base URL (scheme://host:port).
     */
    private String extractBaseUrl(String fullUrl) {
        try {
            URL url = new URL(fullUrl);
            // Construct the base URL from scheme, host, and port
            StringBuilder baseUrlBuilder = new StringBuilder();
            baseUrlBuilder.append(url.getProtocol()).append("://").append(url.getHost());
            if (url.getPort() != -1) {
                baseUrlBuilder.append(":").append(url.getPort());
            }
            return baseUrlBuilder.toString();
        } catch (MalformedURLException e) {
            log.error("Malformed Splunk HEC URL provided: {}. Cannot extract base URL.", fullUrl, e);
            // Fallback to the original full URL, but this might lead to continued 404s
            // In a production app, you might want to throw a RuntimeException or handle this more robustly.
            return fullUrl;
        }
    }
}