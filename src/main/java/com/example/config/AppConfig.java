package com.example.streamsplunkwebhook.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

@Configuration
public class AppConfig {

    @Value("${app.stream.api-secret}")
    private String streamApiSecret;

    @Value("${app.splunk.hec-url}")
    private String splunkHecUrl;

    @Value("${app.splunk.hec-token}")
    private String splunkHecToken;

    @Value("${app.splunk.hec-ssl-verify}")
    private boolean splunkHecSslVerify;

    @Value("${app.queue.name}")
    private String webhookQueueName;

    @Value("${app.worker.deduplication-window-seconds}")
    private long deduplicationWindowSeconds;

    @Value("${app.worker.poll-interval-ms}")
    private long pollIntervalMs;

    public String getStreamApiSecret() {
        return streamApiSecret;
    }

    public String getSplunkHecUrl() {
        return splunkHecUrl;
    }

    public String getSplunkHecToken() {
        return splunkHecToken;
    }

    public boolean isSplunkHecSslVerify() {
        return splunkHecSslVerify;
    }

    public String getWebhookQueueName() {
        return webhookQueueName;
    }

    public long getDeduplicationWindowSeconds() {
        return deduplicationWindowSeconds;
    }

    public long getPollIntervalMs() {
        return pollIntervalMs;
    }

    @Bean
    public RestTemplate restTemplate() {
        // For production, configure a proper HttpClient with SSL context if splunkHecSslVerify is true
        // and you have custom CA certificates.
        // For verify=false, a simple RestTemplate is fine.
        return new RestTemplate();
    }
}