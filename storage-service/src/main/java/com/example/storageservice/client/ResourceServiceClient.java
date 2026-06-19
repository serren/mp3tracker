package com.example.storageservice.client;

import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

@Component
public class ResourceServiceClient {

    private final RestClient restClient;
    private final DiscoveryClient discoveryClient;

    public ResourceServiceClient(DiscoveryClient discoveryClient, RestClient.Builder restClientBuilder) {
        this.restClient = restClientBuilder.build();
        this.discoveryClient = discoveryClient;
    }

    @Retryable(
            retryFor = {ResourceAccessException.class, HttpServerErrorException.class, IllegalStateException.class},
            maxAttempts = 3,
            backoff = @Backoff(delay = 1000, multiplier = 2, maxDelay = 5000)
    )
    public boolean existsByStorageType(String storageType) {
        @SuppressWarnings("unchecked")
        Map<String, Boolean> response = restClient.get()
                .uri(resolveBaseUri() + "/resources/exists?storageType=" + storageType)
                .retrieve()
                .body(Map.class);
        return response != null && Boolean.TRUE.equals(response.get("exists"));
    }

    private String resolveBaseUri() {
        List<ServiceInstance> instances = discoveryClient.getInstances("resource-service");
        if (instances.isEmpty()) {
            throw new IllegalStateException("No instances of resource-service registered in Eureka");
        }
        return instances.get(0).getUri().toString();
    }
}
