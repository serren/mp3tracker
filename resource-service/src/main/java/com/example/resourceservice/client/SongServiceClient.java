package com.example.resourceservice.client;

import com.example.resourceservice.dto.SongMetadataRequest;
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
import java.util.stream.Collectors;

@Component
public class SongServiceClient {

    private final RestClient restClient;
    private final DiscoveryClient discoveryClient;

    public SongServiceClient(DiscoveryClient discoveryClient, RestClient.Builder restClientBuilder) {
        this.restClient = restClientBuilder.build();
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
    public void saveSongMetadata(SongMetadataRequest request) {
        restClient.post()
                .uri(resolveBaseUri() + "/songs")
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .toBodilessEntity();
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
    public void deleteSongMetadata(List<Long> ids) {
        String csv = ids.stream()
                .map(String::valueOf)
                .collect(Collectors.joining(","));
        restClient.delete()
                .uri(resolveBaseUri() + "/songs?id={ids}", csv)
                .retrieve()
                .toBodilessEntity();
    }

    private String resolveBaseUri() {
        List<ServiceInstance> instances = discoveryClient.getInstances("song-service");
        if (instances.isEmpty()) {
            throw new IllegalStateException("No instances of song-service registered in Eureka");
        }
        return instances.get(0).getUri().toString();
    }
}
