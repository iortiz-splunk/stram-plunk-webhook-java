package com.example.streamsplunkwebhook.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.SslProvider;
import io.netty.handler.ssl.util.InsecureTrustManagerFactory;
import reactor.netty.http.client.HttpClient;
import reactor.netty.resources.ConnectionProvider;

import javax.net.ssl.SSLException;
import java.time.Duration;

// NEW/CORRECTED IMPORT for Spring's ReactorClientHttpConnector
import org.springframework.http.client.reactive.ReactorClientHttpConnector;


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
    public WebClient.Builder webClientBuilder() throws SSLException {
        HttpClient httpClient;

        if (!splunkHecSslVerify) {
            io.netty.handler.ssl.SslContext sslContext = SslContextBuilder.forClient()
                    .sslProvider(SslProvider.JDK)
                    .trustManager(InsecureTrustManagerFactory.INSTANCE)
                    .build();

            httpClient = HttpClient.create(ConnectionProvider.builder("splunk-hec-pool")
                            .maxConnections(500)
                            .maxIdleTime(Duration.ofSeconds(30))
                            .maxLifeTime(Duration.ofSeconds(60))
                            .pendingAcquireTimeout(Duration.ofSeconds(5))
                            .build())
                    .secure(sslContextSpec -> sslContextSpec.sslContext(sslContext));
        } else {
            httpClient = HttpClient.create(ConnectionProvider.builder("splunk-hec-pool")
                            .maxConnections(500)
                            .maxIdleTime(Duration.ofSeconds(30))
                            .maxLifeTime(Duration.ofSeconds(60))
                            .pendingAcquireTimeout(Duration.ofSeconds(5))
                            .build());
        }

        // This is the CORRECT line: use Spring's ReactorClientHttpConnector
        return WebClient.builder().clientConnector(new ReactorClientHttpConnector(httpClient));
    }
}