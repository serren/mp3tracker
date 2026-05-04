package com.example.resourceprocessor.client;

import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.http.MediaType;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import java.util.List;

@Component
public class ResourceServiceClient {

    private final RestClient restClient;
    private final DiscoveryClient discoveryClient;

    public ResourceServiceClient(DiscoveryClient discoveryClient) {
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
    public byte[] getResource(Long id) {
        return restClient.get()
                .uri(resolveBaseUri() + "/resources/" + id)
                .accept(MediaType.APPLICATION_OCTET_STREAM)
                .retrieve()
                .body(byte[].class);
    }

    private String resolveBaseUri() {
        List<ServiceInstance> instances = discoveryClient.getInstances("resource-service");
        if (instances.isEmpty()) {
            throw new IllegalStateException("No instances of resource-service registered in Eureka");
        }
        return instances.get(0).getUri().toString();
    }
}
