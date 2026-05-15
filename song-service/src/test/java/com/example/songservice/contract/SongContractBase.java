package com.example.songservice.contract;

import com.example.songservice.entity.Song;
import com.example.songservice.repository.SongRepository;
import io.restassured.module.mockmvc.RestAssuredMockMvc;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.context.WebApplicationContext;

/**
 * Base class for Spring Cloud Contract generated tests.
 * Pre-populates id=1 so that GET /songs/1 and DELETE /songs?id=1 contracts can be verified.
 */
@SpringBootTest
@ActiveProfiles("test")
public abstract class SongContractBase {

    @Autowired
    WebApplicationContext context;

    @Autowired
    SongRepository songRepository;

    @BeforeEach
    public void setup() {
        RestAssuredMockMvc.webAppContextSetup(context);

        songRepository.deleteAll();

        Song song = new Song();
        song.setId(1L);
        song.setName("Bohemian Rhapsody");
        song.setArtist("Queen");
        song.setAlbum("A Night at the Opera");
        song.setDuration("05:55");
        song.setYear("1975");
        songRepository.save(song);
    }
}
