package com.example.resourceservice.client;

import com.example.resourceservice.dto.StorageResponse;
import com.example.resourceservice.enums.StorageType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.web.client.RestClient;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StorageServiceClientFallbackTest {

    @Mock
    private DiscoveryClient discoveryClient;

    @Mock
    private RestClient.Builder restClientBuilder;

    private StorageServiceClient client;

    @BeforeEach
    void setUp() {
        when(restClientBuilder.requestFactory(any())).thenReturn(restClientBuilder);
        when(restClientBuilder.build()).thenReturn(mock(RestClient.class));
        client = new StorageServiceClient(discoveryClient, restClientBuilder);
    }

    @Test
    void getAllStoragesFallback_returnsStubWithStagingAndPermanent() {
        List<StorageResponse> result = client.getAllStoragesFallback(new RuntimeException("storage-service down"));

        assertThat(result).hasSize(2);
        assertThat(result).extracting(StorageResponse::getStorageType)
                .containsExactlyInAnyOrder(StorageType.STAGING, StorageType.PERMANENT);
    }

    @Test
    void getAllStoragesFallback_stagingEntryHasCorrectBucket() {
        List<StorageResponse> result = client.getAllStoragesFallback(new RuntimeException("timeout"));

        StorageResponse staging = result.stream()
                .filter(s -> StorageType.STAGING == s.getStorageType())
                .findFirst().orElseThrow();
        assertThat(staging.getBucket()).isEqualTo("mp3-staging");
    }

    @Test
    void getAllStoragesFallback_permanentEntryHasCorrectBucket() {
        List<StorageResponse> result = client.getAllStoragesFallback(new RuntimeException("timeout"));

        StorageResponse permanent = result.stream()
                .filter(s -> StorageType.PERMANENT == s.getStorageType())
                .findFirst().orElseThrow();
        assertThat(permanent.getBucket()).isEqualTo("mp3-permanent");
    }
}
