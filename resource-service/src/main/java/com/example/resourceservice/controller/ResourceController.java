package com.example.resourceservice.controller;

import com.example.resourceservice.dto.DeletedIdsResponse;
import com.example.resourceservice.dto.ResourceIdResponse;
import com.example.resourceservice.service.ResourceService;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/resources")
public class ResourceController {

    private final ResourceService resourceService;

    public ResourceController(ResourceService resourceService) {
        this.resourceService = resourceService;
    }

    @PostMapping
    public ResponseEntity<ResourceIdResponse> uploadResource(
            @RequestHeader("Content-Type") String contentType,
            @RequestBody byte[] data) {
        Long id = resourceService.uploadResource(contentType, data);
        return ResponseEntity.ok(new ResourceIdResponse(id));
    }

    @GetMapping(value = "/{id}", produces = MediaType.APPLICATION_OCTET_STREAM_VALUE)
    public ResponseEntity<byte[]> getResource(@PathVariable Long id) {
        byte[] data = resourceService.getResource(id);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("audio/mpeg"))
                .body(data);
    }

    @GetMapping("/exists")
    public ResponseEntity<Map<String, Boolean>> existsByStorageType(@RequestParam String storageType) {
        boolean exists = resourceService.hasResourcesWithStorageType(storageType);
        return ResponseEntity.ok(Map.of("exists", exists));
    }

    @DeleteMapping
    public ResponseEntity<DeletedIdsResponse> deleteResources(@RequestParam("id") String ids) {
        List<Long> deletedIds = resourceService.deleteResources(ids);
        return ResponseEntity.ok(new DeletedIdsResponse(deletedIds));
    }
}
