package com.example.songservice.service;

import com.example.songservice.dto.SongRequest;
import com.example.songservice.dto.SongResponse;
import com.example.songservice.entity.Song;
import com.example.songservice.exception.InvalidRequestException;
import com.example.songservice.exception.SongAlreadyExistsException;
import com.example.songservice.exception.SongNotFoundException;
import com.example.songservice.repository.SongRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
public class SongService {

    private final SongRepository songRepository;

    public SongService(SongRepository songRepository) {
        this.songRepository = songRepository;
    }

    @Transactional
    public Long createSong(SongRequest request) {
        if (songRepository.existsById(request.getId())) {
            throw new SongAlreadyExistsException(request.getId());
        }

        Song song = new Song();
        song.setId(request.getId());
        song.setName(request.getName());
        song.setArtist(request.getArtist());
        song.setAlbum(request.getAlbum());
        song.setDuration(request.getDuration());
        song.setYear(request.getYear());

        songRepository.save(song);
        return song.getId();
    }

    public SongResponse getSong(Long id) {
        validateId(id);
        Song song = songRepository.findById(id)
                .orElseThrow(() -> new SongNotFoundException(id));
        return toResponse(song);
    }

    @Transactional
    public List<Long> deleteSongs(String csvIds) {
        if (csvIds == null || csvIds.isBlank()) {
            throw new InvalidRequestException("CSV string is empty or null");
        }
        if (csvIds.length() >= 200) {
            throw new InvalidRequestException("CSV string length must be less than 200 characters");
        }

        String[] parts = csvIds.split(",");
        List<Long> parsedIds = new ArrayList<>();
        for (String part : parts) {
            String trimmed = part.trim();
            try {
                long id = Long.parseLong(trimmed);
                parsedIds.add(id);
            } catch (NumberFormatException e) {
                throw new InvalidRequestException("Invalid ID in CSV: '" + trimmed + "'");
            }
        }

        List<Long> deletedIds = new ArrayList<>();
        for (Long id : parsedIds) {
            if (songRepository.existsById(id)) {
                songRepository.deleteById(id);
                deletedIds.add(id);
            }
        }
        return deletedIds;
    }

    private void validateId(Long id) {
        if (id == null || id <= 0) {
            throw new InvalidRequestException("ID must be a positive integer");
        }
    }

    private SongResponse toResponse(Song song) {
        return new SongResponse(
                song.getId(),
                song.getName(),
                song.getArtist(),
                song.getAlbum(),
                song.getDuration(),
                song.getYear()
        );
    }
}
