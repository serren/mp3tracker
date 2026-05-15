package com.example.songservice.service;

import com.example.songservice.dto.SongRequest;
import com.example.songservice.dto.SongResponse;
import com.example.songservice.entity.Song;
import com.example.songservice.exception.InvalidRequestException;
import com.example.songservice.exception.SongAlreadyExistsException;
import com.example.songservice.exception.SongNotFoundException;
import com.example.songservice.repository.SongRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SongServiceTest {

    @Mock
    private SongRepository songRepository;

    @InjectMocks
    private SongService songService;

    private SongRequest validRequest;

    @BeforeEach
    void setUp() {
        validRequest = buildRequest(1L, "Bohemian Rhapsody", "Queen", "A Night at the Opera", "05:55", "1975");
    }

    // ---- createSong ----

    @Test
    void createSong_whenNew_savesAndReturnsId() {
        when(songRepository.existsById(1L)).thenReturn(false);
        when(songRepository.save(any(Song.class))).thenAnswer(inv -> inv.getArgument(0));

        Long id = songService.createSong(validRequest);

        assertThat(id).isEqualTo(1L);
        verify(songRepository).save(any(Song.class));
    }

    @Test
    void createSong_whenAlreadyExists_throwsSongAlreadyExistsException() {
        when(songRepository.existsById(1L)).thenReturn(true);

        assertThatThrownBy(() -> songService.createSong(validRequest))
                .isInstanceOf(SongAlreadyExistsException.class)
                .hasMessageContaining("ID=1");
    }

    // ---- getSong ----

    @Test
    void getSong_whenExists_returnsSongResponse() {
        Song song = buildSong(1L, "Bohemian Rhapsody", "Queen", "A Night at the Opera", "05:55", "1975");
        when(songRepository.findById(1L)).thenReturn(Optional.of(song));

        SongResponse response = songService.getSong(1L);

        assertThat(response.getId()).isEqualTo(1L);
        assertThat(response.getName()).isEqualTo("Bohemian Rhapsody");
        assertThat(response.getArtist()).isEqualTo("Queen");
        assertThat(response.getDuration()).isEqualTo("05:55");
        assertThat(response.getYear()).isEqualTo("1975");
    }

    @Test
    void getSong_whenNotFound_throwsSongNotFoundException() {
        when(songRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> songService.getSong(99L))
                .isInstanceOf(SongNotFoundException.class)
                .hasMessageContaining("ID=99");
    }

    @Test
    void getSong_withZeroId_throwsInvalidRequestException() {
        assertThatThrownBy(() -> songService.getSong(0L))
                .isInstanceOf(InvalidRequestException.class);
    }

    @Test
    void getSong_withNegativeId_throwsInvalidRequestException() {
        assertThatThrownBy(() -> songService.getSong(-5L))
                .isInstanceOf(InvalidRequestException.class);
    }

    // ---- deleteSongs ----

    @Test
    void deleteSongs_whenBothIdsExist_deletesAndReturnsBothIds() {
        when(songRepository.existsById(1L)).thenReturn(true);
        when(songRepository.existsById(2L)).thenReturn(true);

        List<Long> deleted = songService.deleteSongs("1,2");

        assertThat(deleted).containsExactlyInAnyOrder(1L, 2L);
        verify(songRepository).deleteById(1L);
        verify(songRepository).deleteById(2L);
    }

    @Test
    void deleteSongs_whenIdAbsent_returnsEmptyListAndDoesNotDelete() {
        when(songRepository.existsById(99L)).thenReturn(false);

        List<Long> deleted = songService.deleteSongs("99");

        assertThat(deleted).isEmpty();
        verify(songRepository, never()).deleteById(any());
    }

    @Test
    void deleteSongs_withNullInput_throwsInvalidRequestException() {
        assertThatThrownBy(() -> songService.deleteSongs(null))
                .isInstanceOf(InvalidRequestException.class);
    }

    @Test
    void deleteSongs_withBlankInput_throwsInvalidRequestException() {
        assertThatThrownBy(() -> songService.deleteSongs("  "))
                .isInstanceOf(InvalidRequestException.class);
    }

    @Test
    void deleteSongs_withCsvExceeding200Chars_throwsInvalidRequestExceptionMentioningLimit() {
        // 201 characters
        String longCsv = "1".repeat(201);

        assertThatThrownBy(() -> songService.deleteSongs(longCsv))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("200");
    }

    @Test
    void deleteSongs_withNonNumericEntry_throwsInvalidRequestException() {
        assertThatThrownBy(() -> songService.deleteSongs("1,abc,3"))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("abc");
    }

    // ---- helpers ----

    private SongRequest buildRequest(Long id, String name, String artist, String album,
                                     String duration, String year) {
        SongRequest r = new SongRequest();
        r.setId(id);
        r.setName(name);
        r.setArtist(artist);
        r.setAlbum(album);
        r.setDuration(duration);
        r.setYear(year);
        return r;
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
