package com.example.resourceservice.client;

import com.example.resourceservice.dto.StorageResponse;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.List;

@Component
public class StorageServiceClient {

    private static final Logger log = LoggerFactory.getLogger(StorageServiceClient.class);

    private final RestClient restClient;
    private final DiscoveryClient discoveryClient;

    public StorageServiceClient(DiscoveryClient discoveryClient) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(5));
        factory.setReadTimeout(Duration.ofSeconds(5));
        this.restClient = RestClient.builder().requestFactory(factory).build();
        this.discoveryClient = discoveryClient;
    }

    @CircuitBreaker(name = "storageService", fallbackMethod = "getAllStoragesFallback")
    @Retryable(
            retryFor = {ResourceAccessException.class, HttpServerErrorException.class, IllegalStateException.class},
            maxAttemptsExpression = "#{${retry.max-attempts}}",
            backoff = @Backoff(
                    delayExpression = "#{${retry.initial-interval}}",
                    multiplierExpression = "#{${retry.multiplier}}",
                    maxDelayExpression = "#{${retry.max-delay}}"
            )
    )
    public List<StorageResponse> getAllStorages() {
        return restClient.get()
                .uri(resolveBaseUri() + "/storages")
                .accept(MediaType.APPLICATION_JSON)
                .retrieve()
                .body(new ParameterizedTypeReference<>() {});
    }

    List<StorageResponse> getAllStoragesFallback(Throwable t) {
        log.warn("storage-service unavailable, using stub data: {}", t.getMessage());
        return List.of(
                new StorageResponse(1L, "STAGING",   "mp3-staging",   ""),
                new StorageResponse(2L, "PERMANENT", "mp3-permanent", "")
        );
    }

    private String resolveBaseUri() {
        List<ServiceInstance> instances = discoveryClient.getInstances("storage-service");
        if (instances.isEmpty()) {
            throw new IllegalStateException("No instances of storage-service registered in Eureka");
        }
        return instances.get(0).getUri().toString();
    }
}
