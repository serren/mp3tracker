package com.example.resourceservice.service;

import com.example.resourceservice.client.SongServiceClient;
import com.example.resourceservice.dto.SongMetadataRequest;
import com.example.resourceservice.entity.Resource;
import com.example.resourceservice.exception.InvalidRequestException;
import com.example.resourceservice.exception.ResourceNotFoundException;
import com.example.resourceservice.repository.ResourceRepository;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.sax.BodyContentHandler;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.List;

@Service
public class ResourceService {

    private static final String AUDIO_MPEG_CONTENT_TYPE = "audio/mpeg";
    private final ResourceRepository resourceRepository;
    private final SongServiceClient songServiceClient;

    public ResourceService(ResourceRepository resourceRepository, SongServiceClient songServiceClient) {
        this.resourceRepository = resourceRepository;
        this.songServiceClient = songServiceClient;
    }

    @Transactional
    public Long uploadResource(String contentType, byte[] data) {
        if (contentType == null || !contentType.startsWith(AUDIO_MPEG_CONTENT_TYPE)) {
            String declared = contentType != null ? contentType : "unknown";
            throw new InvalidRequestException("Invalid file format: " + declared + ". Only MP3 files are allowed");
        }
        Resource resource = new Resource();
        resource.setData(data);
        Resource saved = resourceRepository.save(resource);

        SongMetadataRequest metadata = extractMetadata(data, saved.getId());
        songServiceClient.saveSongMetadata(metadata);

        return saved.getId();
    }

    public byte[] getResource(Long id) {
        validateId(id);
        Resource resource = resourceRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(id));
        return resource.getData();
    }

    @Transactional
    public List<Long> deleteResources(String csvIds) {
        if (csvIds == null || csvIds.isBlank()) {
            throw new InvalidRequestException("CSV string is empty or null");
        }
        List<Long> parsedIds = getIds(csvIds);

        List<Long> deletedIds = new ArrayList<>();
        for (Long id : parsedIds) {
            if (resourceRepository.existsById(id)) {
                resourceRepository.deleteById(id);
                deletedIds.add(id);
            }
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

    private SongMetadataRequest extractMetadata(byte[] data, Long resourceId) {
        try {
            AutoDetectParser parser = new AutoDetectParser();
            BodyContentHandler handler = new BodyContentHandler(-1);
            Metadata metadata = new Metadata();
            ParseContext context = new ParseContext();
            parser.parse(new ByteArrayInputStream(data), handler, metadata, context);

            String name = getMetadataValue(metadata, "dc:title", "title", "xmpDM:album");
            String artist = getMetadataValue(metadata, "xmpDM:artist", "Author", "creator");
            String album = getMetadataValue(metadata, "xmpDM:album", "album");
            String year = getMetadataValue(metadata, "xmpDM:releaseDate", "date", "year");
            String durationRaw = getMetadataValue(metadata, "xmpDM:duration", "duration");

            String duration = convertDuration(durationRaw);

            // Normalize year to 4-digit YYYY if needed
            if (year != null && year.length() > 4) {
                year = year.substring(0, 4);
            }

            return new SongMetadataRequest(
                    resourceId,
                    name != null ? name : "Unknown",
                    artist != null ? artist : "Unknown",
                    album != null ? album : "Unknown",
                    duration,
                    year != null ? year : "2000"
            );
        } catch (Exception e) {
            throw new RuntimeException("Failed to extract metadata from MP3 file", e);
        }
    }

    private String getMetadataValue(Metadata metadata, String... keys) {
        for (String key : keys) {
            String value = metadata.get(key);
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return null;
    }

    private String convertDuration(String durationRaw) {
        if (durationRaw == null || durationRaw.isBlank()) {
            return "00:00";
        }
        try {
            double seconds = Double.parseDouble(durationRaw);
            long totalSeconds = Math.round(seconds);
            long minutes = totalSeconds / 60;
            long secs = totalSeconds % 60;
            return String.format("%02d:%02d", minutes, secs);
        } catch (NumberFormatException e) {
            // Try parsing as mm:ss already
            if (durationRaw.matches("\\d{2}:\\d{2}")) {
                return durationRaw;
            }
            return "00:00";
        }
    }
}
