package com.example.resourceservice.service;

import com.example.resourceservice.client.SongServiceClient;
import com.example.resourceservice.entity.Resource;
import com.example.resourceservice.exception.InvalidRequestException;
import com.example.resourceservice.exception.ResourceNotFoundException;
import com.example.resourceservice.messaging.ResourceEventPublisher;
import com.example.resourceservice.repository.ResourceRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ResourceServiceTest {

    @Mock
    ResourceRepository resourceRepository;
    @Mock
    SongServiceClient songServiceClient;
    @Mock
    S3StorageService s3StorageService;
    @Mock
    ResourceEventPublisher eventPublisher;

    @InjectMocks
    ResourceService resourceService;

    // ---- uploadResource ----

    @Test
    void uploadResource_withAudioMpeg_uploadsToS3AndPublishesEvent() {
        byte[] data = new byte[]{1, 2, 3};
        when(s3StorageService.upload(data)).thenReturn("s3://bucket/test.mp3");
        Resource saved = buildResource(42L, "s3://bucket/test.mp3");
        when(resourceRepository.save(any(Resource.class))).thenReturn(saved);

        Long id = resourceService.uploadResource("audio/mpeg", data);

        assertThat(id).isEqualTo(42L);
        verify(s3StorageService).upload(data);
        verify(eventPublisher).publish(42L);
    }

    @Test
    void uploadResource_withAudioMpegWithCharset_isAccepted() {
        byte[] data = new byte[]{1};
        when(s3StorageService.upload(data)).thenReturn("s3://bucket/x.mp3");
        when(resourceRepository.save(any(Resource.class))).thenReturn(buildResource(1L, "s3://bucket/x.mp3"));

        // content type with charset suffix should still be accepted (startsWith check)
        resourceService.uploadResource("audio/mpeg; charset=utf-8", data);

        verify(s3StorageService).upload(data);
    }

    @Test
    void uploadResource_withNonMpegContentType_throwsInvalidRequestException() {
        assertThatThrownBy(() -> resourceService.uploadResource("application/json", new byte[]{1}))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("application/json");
    }

    @Test
    void uploadResource_withNullContentType_throwsInvalidRequestException() {
        assertThatThrownBy(() -> resourceService.uploadResource(null, new byte[]{1}))
                .isInstanceOf(InvalidRequestException.class);
    }

    // ---- getResource ----

    @Test
    void getResource_whenExists_returnsDownloadedBytes() {
        Resource r = buildResource(1L, "s3://bucket/a.mp3");
        when(resourceRepository.findById(1L)).thenReturn(Optional.of(r));
        when(s3StorageService.download("s3://bucket/a.mp3")).thenReturn(new byte[]{9, 8, 7});

        byte[] result = resourceService.getResource(1L);

        assertThat(result).containsExactly(9, 8, 7);
    }

    @Test
    void getResource_whenNotFound_throwsResourceNotFoundException() {
        when(resourceRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> resourceService.getResource(99L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("ID=99");
    }

    @Test
    void getResource_withZeroId_throwsInvalidRequestException() {
        assertThatThrownBy(() -> resourceService.getResource(0L))
                .isInstanceOf(InvalidRequestException.class);
    }

    @Test
    void getResource_withNegativeId_throwsInvalidRequestException() {
        assertThatThrownBy(() -> resourceService.getResource(-3L))
                .isInstanceOf(InvalidRequestException.class);
    }

    // ---- deleteResources ----

    @Test
    void deleteResources_existingIds_deletesS3AndDbAndCallsSongService() {
        Resource r1 = buildResource(1L, "s3://b/a.mp3");
        Resource r2 = buildResource(2L, "s3://b/b.mp3");
        when(resourceRepository.findById(1L)).thenReturn(Optional.of(r1));
        when(resourceRepository.findById(2L)).thenReturn(Optional.of(r2));

        List<Long> deleted = resourceService.deleteResources("1,2");

        assertThat(deleted).containsExactlyInAnyOrder(1L, 2L);
        verify(s3StorageService).delete("s3://b/a.mp3");
        verify(s3StorageService).delete("s3://b/b.mp3");

        ArgumentCaptor<List<Long>> captor = ArgumentCaptor.forClass(List.class);
        verify(songServiceClient).deleteSongMetadata(captor.capture());
        assertThat(captor.getValue()).containsExactlyInAnyOrder(1L, 2L);
    }

    @Test
    void deleteResources_nonExistingId_returnsEmptyListAndDoesNotCallSongService() {
        when(resourceRepository.findById(99L)).thenReturn(Optional.empty());

        List<Long> deleted = resourceService.deleteResources("99");

        assertThat(deleted).isEmpty();
        verify(songServiceClient, never()).deleteSongMetadata(any());
    }

    @Test
    void deleteResources_withNullInput_throwsInvalidRequestException() {
        assertThatThrownBy(() -> resourceService.deleteResources(null))
                .isInstanceOf(InvalidRequestException.class);
    }

    @Test
    void deleteResources_withCsvExceeding200Chars_throwsInvalidRequestException() {
        String longCsv = "2147483647,".repeat(20); // > 200 chars

        assertThatThrownBy(() -> resourceService.deleteResources(longCsv))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("200");
    }

    @Test
    void deleteResources_withNonNumericEntry_throwsInvalidRequestException() {
        assertThatThrownBy(() -> resourceService.deleteResources("1,X,3"))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("X");
    }

    // ---- helpers ----

    private Resource buildResource(Long id, String s3Key) {
        Resource r = new Resource();
        r.setId(id);
        r.setS3Key(s3Key);
        return r;
    }
}
