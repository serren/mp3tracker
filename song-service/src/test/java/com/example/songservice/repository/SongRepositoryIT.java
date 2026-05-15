package com.example.songservice.repository;

import com.example.songservice.entity.Song;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class SongRepositoryIT {

    @Autowired
    SongRepository songRepository;

    @Test
    void save_andFindById_roundTrip() {
        Song song = buildSong(10L, "Stairway to Heaven", "Led Zeppelin", "Led Zeppelin IV", "08:02", "1971");

        songRepository.save(song);
        Optional<Song> found = songRepository.findById(10L);

        assertThat(found).isPresent();
        assertThat(found.get().getName()).isEqualTo("Stairway to Heaven");
        assertThat(found.get().getArtist()).isEqualTo("Led Zeppelin");
        assertThat(found.get().getDuration()).isEqualTo("08:02");
        assertThat(found.get().getYear()).isEqualTo("1971");
    }

    @Test
    void existsById_returnsFalse_whenAbsent() {
        assertThat(songRepository.existsById(999L)).isFalse();
    }

    @Test
    void deleteById_removesEntity() {
        Song song = buildSong(20L, "Comfortably Numb", "Pink Floyd", "The Wall", "06:22", "1979");
        songRepository.save(song);

        songRepository.deleteById(20L);

        assertThat(songRepository.findById(20L)).isEmpty();
    }

    private Song buildSong(Long id, String name, String artist, String album,
                           String duration, String year) {
        Song s = new Song();
        s.setId(id);
        s.setName(name);
        s.setArtist(artist);
        s.setAlbum(album);
        s.setDuration(duration);
        s.setYear(year);
        return s;
    }
}
