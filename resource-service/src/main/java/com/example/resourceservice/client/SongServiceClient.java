package com.example.resourceservice.client;

import com.example.resourceservice.dto.SongMetadataRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;

@Component
public class SongServiceClient {

    private final RestClient restClient;

    public SongServiceClient(@Value("${song-service.url}") String songServiceUrl) {
        this.restClient = RestClient.builder()
                .baseUrl(songServiceUrl)
                .build();
    }

    public void saveSongMetadata(SongMetadataRequest request) {
        restClient.post()
                .uri("/songs")
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .toBodilessEntity();
    }

    public void deleteSongMetadata(List<Long> ids) {
        String csv = ids.stream()
                .map(String::valueOf)
                .collect(java.util.stream.Collectors.joining(","));
        restClient.delete()
                .uri("/songs?id={ids}", csv)
                .retrieve()
                .toBodilessEntity();
    }
}
