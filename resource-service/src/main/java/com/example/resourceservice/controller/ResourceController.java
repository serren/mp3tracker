package com.example.resourceservice.controller;

import com.example.resourceservice.dto.DeletedIdsResponse;
import com.example.resourceservice.dto.ResourceIdResponse;
import com.example.resourceservice.exception.InvalidRequestException;
import com.example.resourceservice.service.ResourceService;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/resources")
public class ResourceController {

    private static final String AUDIO_MPEG_CONTENT_TYPE = "audio/mpeg";

    private final ResourceService resourceService;

    public ResourceController(ResourceService resourceService) {
        this.resourceService = resourceService;
    }

    @PostMapping
    public ResponseEntity<ResourceIdResponse> uploadResource(
            @RequestHeader("Content-Type") String contentType,
            @RequestBody byte[] data) {
        if (contentType == null || !contentType.startsWith(AUDIO_MPEG_CONTENT_TYPE)) {
            String declared = contentType != null ? contentType : "unknown";
            throw new InvalidRequestException("Invalid file format: " + declared + ". Only MP3 files are allowed");
        }
        Long id = resourceService.uploadResource(data);
        return ResponseEntity.ok(new ResourceIdResponse(id));
    }

    @GetMapping(value = "/{id}", produces = MediaType.APPLICATION_OCTET_STREAM_VALUE)
    public ResponseEntity<byte[]> getResource(@PathVariable Long id) {
        byte[] data = resourceService.getResource(id);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("audio/mpeg"))
                .body(data);
    }

    @DeleteMapping
    public ResponseEntity<DeletedIdsResponse> deleteResources(@RequestParam("id") String ids) {
        List<Long> deletedIds = resourceService.deleteResources(ids);
        return ResponseEntity.ok(new DeletedIdsResponse(deletedIds));
    }
}
