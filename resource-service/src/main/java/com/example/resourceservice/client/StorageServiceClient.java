package com.example.resourceservice.client;

import com.example.resourceservice.dto.StorageResponse;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import java.util.List;

@Component
public class StorageServiceClient {

    private final RestClient restClient;
    private final DiscoveryClient discoveryClient;

    public StorageServiceClient(DiscoveryClient discoveryClient) {
        this.restClient = RestClient.builder().build();
        this.discoveryClient = discoveryClient;
    }

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

    private String resolveBaseUri() {
        List<ServiceInstance> instances = discoveryClient.getInstances("storage-service");
        if (instances.isEmpty()) {
            throw new IllegalStateException("No instances of storage-service registered in Eureka");
        }
        return instances.get(0).getUri().toString();
    }
}
