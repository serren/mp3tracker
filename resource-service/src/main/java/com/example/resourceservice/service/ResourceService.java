package com.example.resourceservice.service;

import com.example.resourceservice.client.SongServiceClient;
import com.example.resourceservice.entity.Resource;
import com.example.resourceservice.exception.InvalidRequestException;
import com.example.resourceservice.exception.ResourceNotFoundException;
import com.example.resourceservice.repository.ResourceRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
public class ResourceService {

    private static final String AUDIO_MPEG_CONTENT_TYPE = "audio/mpeg";
    private final ResourceRepository resourceRepository;
    private final SongServiceClient songServiceClient;
    private final S3StorageService s3StorageService;

    public ResourceService(ResourceRepository resourceRepository,
                           SongServiceClient songServiceClient,
                           S3StorageService s3StorageService) {
        this.resourceRepository = resourceRepository;
        this.songServiceClient = songServiceClient;
        this.s3StorageService = s3StorageService;
    }

    @Transactional
    public Long uploadResource(String contentType, byte[] data) {
        if (contentType == null || !contentType.startsWith(AUDIO_MPEG_CONTENT_TYPE)) {
            String declared = contentType != null ? contentType : "unknown";
            throw new InvalidRequestException("Invalid file format: " + declared + ". Only MP3 files are allowed");
        }
        String s3Key = s3StorageService.upload(data);
        Resource resource = new Resource();
        resource.setS3Key(s3Key);
        return resourceRepository.save(resource).getId();
    }

    public byte[] getResource(Long id) {
        validateId(id);
        Resource resource = resourceRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(id));
        return s3StorageService.download(resource.getS3Key());
    }

    @Transactional
    public List<Long> deleteResources(String csvIds) {
        if (csvIds == null || csvIds.isBlank()) {
            throw new InvalidRequestException("CSV string is empty or null");
        }
        List<Long> parsedIds = getIds(csvIds);

        List<Long> deletedIds = new ArrayList<>();
        for (Long id : parsedIds) {
            resourceRepository.findById(id).ifPresent(resource -> {
                s3StorageService.delete(resource.getS3Key());
                resourceRepository.deleteById(id);
                deletedIds.add(id);
            });
        }

        if (!deletedIds.isEmpty()) {
            songServiceClient.deleteSongMetadata(deletedIds);
        }

        return deletedIds;
    }

    private static List<Long> getIds(String csvIds) {
        if (csvIds.length() > 200) {
            throw new InvalidRequestException("CSV string is too long: received " + csvIds.length() + " characters, maximum allowed is 200");
        }

        String[] parts = csvIds.split(",");
        List<Long> parsedIds = new ArrayList<>();
        for (String part : parts) {
            String trimmed = part.trim();
            try {
                long id = Long.parseLong(trimmed);
                parsedIds.add(id);
            } catch (NumberFormatException e) {
                throw new InvalidRequestException("Invalid ID format: '" + trimmed + "'. Only positive integers are allowed");
            }
        }
        return parsedIds;
    }

    private void validateId(Long id) {
        if (id == null || id <= 0) {
            throw new InvalidRequestException("Invalid value '" + id + "' for ID. Must be a positive integer");
        }
    }
}
