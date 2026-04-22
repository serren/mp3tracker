package com.example.resourceprocessor.service;

import com.example.resourceprocessor.dto.SongMetadata;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.metadata.XMPDM;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.sax.BodyContentHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.InputStream;

@Service
public class MetadataExtractorService {

    private static final Logger log = LoggerFactory.getLogger(MetadataExtractorService.class);

    public SongMetadata extract(byte[] mp3Data) {
        Metadata tikaMetadata = new Metadata();
        AutoDetectParser parser = new AutoDetectParser();

        try (InputStream stream = new ByteArrayInputStream(mp3Data)) {
            parser.parse(stream, new BodyContentHandler(), tikaMetadata, new ParseContext());
        } catch (Exception e) {
            log.warn("Tika parsing warning: {}", e.getMessage());
        }

        String name = getOrDefault(tikaMetadata, TikaCoreProperties.TITLE);
        String artist = getOrDefault(tikaMetadata, XMPDM.ARTIST);
        String album = getOrDefault(tikaMetadata, XMPDM.ALBUM);
        String duration = formatDuration(tikaMetadata.get(XMPDM.DURATION));
        String year = extractYear(tikaMetadata.get(XMPDM.RELEASE_DATE));

        log.debug("Extracted metadata: name={}, artist={}, album={}, duration={}, year={}",
                name, artist, album, duration, year);

        return new SongMetadata(name, artist, album, duration, year);
    }

    private String getOrDefault(Metadata metadata, org.apache.tika.metadata.Property property) {
        String value = metadata.get(property);
        return (value != null && !value.isBlank()) ? value : "Unknown";
    }

    private String formatDuration(String rawDuration) {
        if (rawDuration == null || rawDuration.isBlank()) {
            return "00:00";
        }
        try {
            double seconds = Double.parseDouble(rawDuration);
            int totalSecs = (int) Math.round(seconds);
            return String.format("%02d:%02d", totalSecs / 60, totalSecs % 60);
        } catch (NumberFormatException e) {
            log.warn("Could not parse duration '{}', defaulting to 00:00", rawDuration);
            return "00:00";
        }
    }

    private String extractYear(String releaseDate) {
        if (releaseDate == null || releaseDate.length() < 4) {
            return "0000";
        }
        return releaseDate.substring(0, 4);
    }
}
