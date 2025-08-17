package com.example.streamsplunkwebhook.controller;

import com.example.streamsplunkwebhook.config.AppConfig;
import com.example.streamsplunkwebhook.service.RedisQueueService;
import com.example.streamsplunkwebhook.util.SignatureVerifier;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;

@RestController
public class WebhookController {

    private static final Logger log = LoggerFactory.getLogger(WebhookController.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();

    private final SignatureVerifier signatureVerifier;
    private final RedisQueueService redisQueueService;
    private final AppConfig appConfig;

    public WebhookController(SignatureVerifier signatureVerifier, RedisQueueService redisQueueService, AppConfig appConfig) {
        this.signatureVerifier = signatureVerifier;
        this.redisQueueService = redisQueueService;
        this.appConfig = appConfig;
    }

    @PostMapping("/webhook")
    public ResponseEntity<String> receiveWebhook(
            @RequestBody String rawBody,
            @RequestHeader("X-Signature") String xSignature,
            @RequestHeader("X-Webhook-Id") String xWebhookId,
            @RequestHeader("X-Api-Key") String xApiKey) {

        // Log the initial receipt of the webhook with its ID
        log.info("Received webhook with ID: {}. Checking signature...", xWebhookId);

        if (!signatureVerifier.verifySignature(rawBody, xSignature, appConfig.getStreamApiSecret())) {
            log.warn("Invalid X-Signature for webhook ID: {}. Request rejected.", xWebhookId);
            return new ResponseEntity<>("Invalid X-Signature", HttpStatus.FORBIDDEN);
        }

        // Log successful signature verification
        log.info("Signature verified for webhook ID: {}. Enqueuing for processing.", xWebhookId);

        try {
            // Create a composite payload to store in Redis, including metadata
            ObjectNode webhookData = objectMapper.createObjectNode();
            webhookData.put("timestamp", Instant.now().getEpochSecond()); // Unix timestamp
            webhookData.put("x_webhook_id", xWebhookId);
            webhookData.put("x_api_key", xApiKey);
            webhookData.set("original_payload", objectMapper.readTree(rawBody)); // Store original JSON as a nested object

            redisQueueService.enqueueWebhook(webhookData.toString());

            // Log successful enqueuing
            log.info("Webhook ID {} successfully enqueued.", xWebhookId);
            return new ResponseEntity<>("OK", HttpStatus.OK);
        } catch (Exception e) {
            log.error("Error processing or enqueuing webhook ID {}: {}", xWebhookId, e.getMessage(), e);
            return new ResponseEntity<>("Internal Server Error", HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }
}