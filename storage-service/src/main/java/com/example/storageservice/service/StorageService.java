package com.example.storageservice.service;

import com.example.storageservice.client.ResourceServiceClient;
import com.example.storageservice.dto.StorageRequest;
import com.example.storageservice.dto.StorageResponse;
import com.example.storageservice.entity.Storage;
import com.example.storageservice.exception.InvalidRequestException;
import com.example.storageservice.repository.StorageRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
public class StorageService {

    private static final Logger log = LoggerFactory.getLogger(StorageService.class);

    private final StorageRepository storageRepository;
    private final ResourceServiceClient resourceServiceClient;

    public StorageService(StorageRepository storageRepository, ResourceServiceClient resourceServiceClient) {
        this.storageRepository = storageRepository;
        this.resourceServiceClient = resourceServiceClient;
    }

    @Transactional
    public Long createStorage(StorageRequest request) {
        Storage storage = new Storage();
        storage.setStorageType(request.getStorageType());
        storage.setBucket(request.getBucket());
        storage.setPath(request.getPath() != null ? request.getPath() : "");
        Long id = storageRepository.save(storage).getId();
        log.info("Created Storage id={} type={}", id, request.getStorageType());
        return id;
    }

    public List<StorageResponse> getAllStorages() {
        return storageRepository.findAll().stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public List<Long> deleteStorages(String csvIds) {
        if (csvIds == null || csvIds.isBlank()) {
            throw new InvalidRequestException("CSV string is empty or null");
        }
        if (csvIds.length() > 200) {
            throw new InvalidRequestException("CSV string is too long");
        }
        List<Long> parsedIds = parseIds(csvIds);
        List<Long> deletedIds = new ArrayList<>();
        for (Long id : parsedIds) {
            storageRepository.findById(id).ifPresent(s -> {
                if (resourceServiceClient.existsByStorageType(s.getStorageType())) {
                    throw new InvalidRequestException(
                            "Cannot delete storage '" + s.getStorageType() + "': resources are still stored there");
                }
                storageRepository.deleteById(id);
                deletedIds.add(id);
            });
        }
        return deletedIds;
    }

    private List<Long> parseIds(String csvIds) {
        String[] parts = csvIds.split(",");
        List<Long> ids = new ArrayList<>();
        for (String part : parts) {
            String trimmed = part.trim();
            try {
                ids.add(Long.parseLong(trimmed));
            } catch (NumberFormatException e) {
                throw new InvalidRequestException("Invalid ID format: '" + trimmed + "'");
            }
        }
        return ids;
    }

    private StorageResponse toResponse(Storage s) {
        return new StorageResponse(s.getId(), s.getStorageType(), s.getBucket(), s.getPath());
    }
}
