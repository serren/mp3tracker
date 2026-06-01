package com.example.resourceservice.client;

import com.example.resourceservice.dto.StorageResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cloud.client.discovery.DiscoveryClient;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
class StorageServiceClientFallbackTest {

    @Mock
    private DiscoveryClient discoveryClient;

    private StorageServiceClient client;

    @BeforeEach
    void setUp() {
        client = new StorageServiceClient(discoveryClient);
    }

    @Test
    void getAllStoragesFallback_returnsStubWithStagingAndPermanent() {
        List<StorageResponse> result = client.getAllStoragesFallback(new RuntimeException("storage-service down"));

        assertThat(result).hasSize(2);
        assertThat(result).extracting(StorageResponse::getStorageType)
                .containsExactlyInAnyOrder("STAGING", "PERMANENT");
    }

    @Test
    void getAllStoragesFallback_stagingEntryHasCorrectBucket() {
        List<StorageResponse> result = client.getAllStoragesFallback(new RuntimeException("timeout"));

        StorageResponse staging = result.stream()
                .filter(s -> "STAGING".equals(s.getStorageType()))
                .findFirst().orElseThrow();
        assertThat(staging.getBucket()).isEqualTo("mp3-staging");
    }

    @Test
    void getAllStoragesFallback_permanentEntryHasCorrectBucket() {
        List<StorageResponse> result = client.getAllStoragesFallback(new RuntimeException("timeout"));

        StorageResponse permanent = result.stream()
                .filter(s -> "PERMANENT".equals(s.getStorageType()))
                .findFirst().orElseThrow();
        assertThat(permanent.getBucket()).isEqualTo("mp3-permanent");
    }
}
