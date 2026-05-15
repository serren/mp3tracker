package com.example.songservice.controller;

import com.example.songservice.dto.SongRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class SongControllerIT {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    @Test
    void postSong_withValidRequest_returnsOkWithId() throws Exception {
        SongRequest req = validRequest(1L);

        mockMvc.perform(post("/songs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1L));
    }

    @Test
    void postSong_withDuplicateId_returns409WithErrorCode() throws Exception {
        SongRequest req = validRequest(2L);
        mockMvc.perform(post("/songs")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)));

        mockMvc.perform(post("/songs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("409"))
                .andExpect(jsonPath("$.errorMessage").value("Metadata for resource ID=2 already exists"));
    }

    @Test
    void postSong_withInvalidDurationPattern_returns400WithFieldDetails() throws Exception {
        SongRequest req = validRequest(3L);
        req.setDuration("99:99"); // seconds > 59

        mockMvc.perform(post("/songs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("400"))
                .andExpect(jsonPath("$.details.duration").exists());
    }

    @Test
    void postSong_withInvalidYearPattern_returns400WithFieldDetails() throws Exception {
        SongRequest req = validRequest(4L);
        req.setYear("1800"); // before 1900

        mockMvc.perform(post("/songs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details.year").exists());
    }

    @Test
    void getSong_whenExists_returnsFullMetadata() throws Exception {
        SongRequest req = validRequest(5L);
        mockMvc.perform(post("/songs")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)));

        mockMvc.perform(get("/songs/5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(5L))
                .andExpect(jsonPath("$.name").value("Bohemian Rhapsody"))
                .andExpect(jsonPath("$.artist").value("Queen"))
                .andExpect(jsonPath("$.duration").value("05:55"))
                .andExpect(jsonPath("$.year").value("1975"));
    }

    @Test
    void getSong_whenNotFound_returns404WithMessage() throws Exception {
        mockMvc.perform(get("/songs/99999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("404"))
                .andExpect(jsonPath("$.errorMessage").value("Song metadata for ID=99999 not found"));
    }

    @Test
    void getSong_withNonNumericId_returns400() throws Exception {
        mockMvc.perform(get("/songs/ABC"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorMessage").value(containsString("ABC")));
    }

    @Test
    void deleteSongs_withExistingId_returnsDeletedIds() throws Exception {
        SongRequest req = validRequest(6L);
        mockMvc.perform(post("/songs")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)));

        mockMvc.perform(delete("/songs").param("id", "6"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ids[0]").value(6L));
    }

    @Test
    void deleteSongs_withCsvExceeding200Chars_returns400() throws Exception {
        // Build a CSV string > 200 characters using valid-looking IDs
        String csv = "2147483647,2147483646,2147483645,2147483644,2147483643,2147483642," +
                     "2147483641,2147483640,2147483639,2147483638,2147483637,2147483636," +
                     "2147483635,2147483634,2147483633,2147483632,2147483631,2147483630,2147483629";

        mockMvc.perform(delete("/songs").param("id", csv))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorMessage").value(containsString("200")));
    }

    private SongRequest validRequest(Long id) {
        SongRequest r = new SongRequest();
        r.setId(id);
        r.setName("Bohemian Rhapsody");
        r.setArtist("Queen");
        r.setAlbum("A Night at the Opera");
        r.setDuration("05:55");
        r.setYear("1975");
        return r;
    }
}
