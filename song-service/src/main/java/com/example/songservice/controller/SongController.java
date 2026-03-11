package com.example.songservice.controller;

import com.example.songservice.dto.DeletedIdsResponse;
import com.example.songservice.dto.SongIdResponse;
import com.example.songservice.dto.SongRequest;
import com.example.songservice.dto.SongResponse;
import com.example.songservice.service.SongService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/songs")
public class SongController {

    private final SongService songService;

    public SongController(SongService songService) {
        this.songService = songService;
    }

    @PostMapping
    public ResponseEntity<SongIdResponse> createSong(@Valid @RequestBody SongRequest request) {
        Long id = songService.createSong(request);
        return ResponseEntity.ok(new SongIdResponse(id));
    }

    @GetMapping("/{id}")
    public ResponseEntity<SongResponse> getSong(@PathVariable Long id) {
        SongResponse response = songService.getSong(id);
        return ResponseEntity.ok(response);
    }

    @DeleteMapping
    public ResponseEntity<DeletedIdsResponse> deleteSongs(@RequestParam("id") String ids) {
        List<Long> deletedIds = songService.deleteSongs(ids);
        return ResponseEntity.ok(new DeletedIdsResponse(deletedIds));
    }
}
