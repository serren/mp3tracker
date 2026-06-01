package com.example.storageservice.controller;

import com.example.storageservice.dto.DeletedIdsResponse;
import com.example.storageservice.dto.StorageIdResponse;
import com.example.storageservice.dto.StorageRequest;
import com.example.storageservice.dto.StorageResponse;
import com.example.storageservice.service.StorageService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/storages")
public class StorageController {

    private final StorageService storageService;

    public StorageController(StorageService storageService) {
        this.storageService = storageService;
    }

    @PostMapping
    public ResponseEntity<StorageIdResponse> createStorage(@Valid @RequestBody StorageRequest request) {
        Long id = storageService.createStorage(request);
        return ResponseEntity.ok(new StorageIdResponse(id));
    }

    @GetMapping
    public ResponseEntity<List<StorageResponse>> getAllStorages() {
        return ResponseEntity.ok(storageService.getAllStorages());
    }

    @DeleteMapping
    public ResponseEntity<DeletedIdsResponse> deleteStorages(@RequestParam("id") String ids) {
        List<Long> deletedIds = storageService.deleteStorages(ids);
        return ResponseEntity.ok(new DeletedIdsResponse(deletedIds));
    }
}
