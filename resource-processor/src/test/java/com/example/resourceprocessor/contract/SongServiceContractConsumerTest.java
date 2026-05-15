package com.example.resourceprocessor.contract;

import com.example.resourceprocessor.client.SongServiceClient;
import com.example.resourceprocessor.dto.SongRequest;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.discovery.DiscoveryClient;

import java.net.URI;
import java.util.List;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Contract consumer test: verifies that resource-processor's HTTP calls to song-service
 * match the expected contract (same shape as the stubs published by song-service).
 *
 * Uses WireMock directly (no Spring context) with a stubbed DiscoveryClient to route
 * SongServiceClient requests to the WireMock server.
 *
 * Note: @Retryable on SongServiceClient requires Spring AOP — tested here without proxy,
 * so retry is skipped. Only the HTTP call format is verified.
 */
class SongServiceContractConsumerTest {

    WireMockServer wireMockServer;
    SongServiceClient songServiceClient;

    @BeforeEach
    void setUp() {
        wireMockServer = new WireMockServer(WireMockConfiguration.wireMockConfig().dynamicPort());
        wireMockServer.start();

        // Register stub matching the song-service POST /songs contract
        wireMockServer.stubFor(
                post(urlEqualTo("/songs"))
                        .withHeader("Content-Type", WireMock.containing("application/json"))
                        .withRequestBody(matchingJsonPath("$.id"))
                        .withRequestBody(matchingJsonPath("$.name"))
                        .withRequestBody(matchingJsonPath("$.artist"))
                        .willReturn(aResponse()
                                .withStatus(200)
                                .withHeader("Content-Type", "application/json")
                                .withBody("{\"id\":1}")
                        )
        );

        // Point DiscoveryClient at the WireMock server
        DiscoveryClient mockDiscovery = mock(DiscoveryClient.class);
        ServiceInstance mockInstance = mock(ServiceInstance.class);
        when(mockInstance.getUri()).thenReturn(URI.create("http://localhost:" + wireMockServer.port()));
        when(mockDiscovery.getInstances("song-service")).thenReturn(List.of(mockInstance));

        songServiceClient = new SongServiceClient(mockDiscovery);
    }

    @AfterEach
    void tearDown() {
        wireMockServer.stop();
    }

    @Test
    void saveSong_requestFormatSatisfiesContractExpectation() {
        SongRequest request = new SongRequest(1L, "Bohemian Rhapsody", "Queen",
                "A Night at the Opera", "05:55", "1975");

        // Should not throw — stub responds with 200
        assertThatCode(() -> songServiceClient.saveSong(request)).doesNotThrowAnyException();

        // Verify the outbound request matches the contract
        wireMockServer.verify(
                postRequestedFor(urlEqualTo("/songs"))
                        .withRequestBody(matchingJsonPath("$.id", equalTo("1")))
                        .withRequestBody(matchingJsonPath("$.name", equalTo("Bohemian Rhapsody")))
                        .withRequestBody(matchingJsonPath("$.artist", equalTo("Queen")))
        );
    }
}
