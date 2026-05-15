package com.example.resourceprocessor.service;

import com.example.resourceprocessor.dto.SongMetadata;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class MetadataExtractorServiceTest {

    private MetadataExtractorService extractor;

    @BeforeEach
    void setUp() {
        extractor = new MetadataExtractorService();
    }

    @Test
    void extract_withEmptyBytes_returnsAllDefaults() {
        SongMetadata metadata = extractor.extract(new byte[0]);

        assertThat(metadata.getName()).isEqualTo("Unknown");
        assertThat(metadata.getArtist()).isEqualTo("Unknown");
        assertThat(metadata.getAlbum()).isEqualTo("Unknown");
        assertThat(metadata.getDuration()).isEqualTo("00:00");
        assertThat(metadata.getYear()).isEqualTo("2000");
    }

    @Test
    void extract_withMp3ContainingId3v2Tags_returnsExtractedTitleArtistAlbum() throws IOException {
        byte[] mp3 = createId3v2TaggedMp3("Bohemian Rhapsody", "Queen", "A Night at the Opera", "1975");

        SongMetadata metadata = extractor.extract(mp3);

        assertThat(metadata.getName()).isEqualTo("Bohemian Rhapsody");
        assertThat(metadata.getArtist()).isEqualTo("Queen");
        assertThat(metadata.getAlbum()).isEqualTo("A Night at the Opera");
        assertThat(metadata.getYear()).isEqualTo("1975");
    }

    @Test
    void extract_withPartialTags_returnsUnknownForMissingFields() throws IOException {
        // Only title tag, no artist/album
        byte[] mp3 = createId3v2WithTitle("Only Title");

        SongMetadata metadata = extractor.extract(mp3);

        assertThat(metadata.getName()).isEqualTo("Only Title");
        assertThat(metadata.getArtist()).isEqualTo("Unknown");
        assertThat(metadata.getAlbum()).isEqualTo("Unknown");
    }

    // ---- ID3v2.3.0 test file builder ----

    /**
     * Builds a minimal ID3v2.3.0 tagged MP3 byte array with the given tag values.
     * The tags are written as ISO-8859-1 text frames:
     * TIT2 = title, TPE1 = artist, TALB = album, TYER = year (ID3v2.3).
     */
    private static byte[] createId3v2TaggedMp3(String title, String artist, String album,
                                                String year) throws IOException {
        List<byte[]> frames = new ArrayList<>();
        frames.add(buildId3Frame("TIT2", title));
        frames.add(buildId3Frame("TPE1", artist));
        frames.add(buildId3Frame("TALB", album));
        frames.add(buildId3Frame("TYER", year));
        return buildId3v2(frames);
    }

    private static byte[] createId3v2WithTitle(String title) throws IOException {
        List<byte[]> frames = new ArrayList<>();
        frames.add(buildId3Frame("TIT2", title));
        return buildId3v2(frames);
    }

    /**
     * Builds a single ID3v2.3.0 text frame.
     * Format: 4-byte ID | 4-byte big-endian size | 2-byte flags | 1-byte encoding | text bytes
     */
    private static byte[] buildId3Frame(String frameId, String text) throws IOException {
        byte[] textBytes = text.getBytes(StandardCharsets.ISO_8859_1);
        // Frame content = encoding byte (0x00 = ISO-8859-1) + text bytes
        int contentLength = 1 + textBytes.length;

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(frameId.getBytes(StandardCharsets.US_ASCII));      // 4-byte frame ID
        out.write((contentLength >> 24) & 0xFF);                      // size (big-endian)
        out.write((contentLength >> 16) & 0xFF);
        out.write((contentLength >> 8) & 0xFF);
        out.write(contentLength & 0xFF);
        out.write(0x00);                                               // flags byte 1
        out.write(0x00);                                               // flags byte 2
        out.write(0x00);                                               // encoding: ISO-8859-1
        out.write(textBytes);
        return out.toByteArray();
    }

    /**
     * Assembles an ID3v2.3.0 tag from a list of frames, followed by a minimal MPEG audio
     * frame header so Tika detects the content as audio/mpeg.
     */
    private static byte[] buildId3v2(List<byte[]> frames) throws IOException {
        int totalFramesSize = frames.stream().mapToInt(f -> f.length).sum();

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        // ID3v2.3.0 header (10 bytes)
        out.write("ID3".getBytes(StandardCharsets.US_ASCII));
        out.write(0x03);  // major version 2.3
        out.write(0x00);  // revision
        out.write(0x00);  // flags (no unsynchronisation, no extended header)
        // Syncsafe integer encoding of the tag size (4 * 7 bits)
        out.write((totalFramesSize >> 21) & 0x7F);
        out.write((totalFramesSize >> 14) & 0x7F);
        out.write((totalFramesSize >> 7) & 0x7F);
        out.write(totalFramesSize & 0x7F);

        for (byte[] frame : frames) {
            out.write(frame);
        }

        // Minimal MPEG Layer 3 sync word so Tika identifies it as audio/mpeg
        // 0xFF 0xFB = MPEG1, Layer3, 128kbps header
        out.write(new byte[]{(byte) 0xFF, (byte) 0xFB, (byte) 0x90, 0x00});
        // Padding bytes to satisfy the MPEG frame size
        out.write(new byte[413]);

        return out.toByteArray();
    }
}
