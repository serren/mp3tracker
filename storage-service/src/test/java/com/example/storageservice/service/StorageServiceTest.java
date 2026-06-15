package com.example.storageservice.service;

import com.example.storageservice.dto.StorageRequest;
import com.example.storageservice.dto.StorageResponse;
import com.example.storageservice.entity.Storage;
import com.example.storageservice.exception.InvalidRequestException;
import com.example.storageservice.repository.StorageRepository;
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
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class StorageServiceTest {

    @Mock
    private StorageRepository storageRepository;

    @InjectMocks
    private StorageService storageService;

    @Test
    void createStorage_validRequest_savesAndReturnsId() {
        StorageRequest request = new StorageRequest();
        request.setStorageType("STAGING");
        request.setBucket("mp3-staging");
        request.setPath("");

        Storage saved = buildStorage(1L, "STAGING", "mp3-staging", "");
        when(storageRepository.save(any(Storage.class))).thenReturn(saved);

        Long id = storageService.createStorage(request);

        assertThat(id).isEqualTo(1L);
        verify(storageRepository).save(any(Storage.class));
    }

    @Test
    void getAllStorages_returnsAllMapped() {
        List<Storage> storages = List.of(
                buildStorage(1L, "STAGING", "mp3-staging", ""),
                buildStorage(2L, "PERMANENT", "mp3-permanent", "")
        );
        when(storageRepository.findAll()).thenReturn(storages);

        List<StorageResponse> result = storageService.getAllStorages();

        assertThat(result).hasSize(2);
        assertThat(result.get(0).getStorageType()).isEqualTo("STAGING");
        assertThat(result.get(1).getStorageType()).isEqualTo("PERMANENT");
    }

    @Test
    void deleteStorages_existingIds_deletesAndReturnsIds() {
        Storage storage = buildStorage(1L, "STAGING", "mp3-staging", "");
        when(storageRepository.findById(1L)).thenReturn(Optional.of(storage));

        List<Long> result = storageService.deleteStorages("1");

        assertThat(result).containsExactly(1L);
        verify(storageRepository).deleteById(1L);
    }

    @Test
    void deleteStorages_nonExistingId_returnsEmpty() {
        when(storageRepository.findById(99L)).thenReturn(Optional.empty());

        List<Long> result = storageService.deleteStorages("99");

        assertThat(result).isEmpty();
        verify(storageRepository, never()).deleteById(any());
    }

    @Test
    void deleteStorages_withNullInput_throwsInvalidRequestException() {
        assertThatThrownBy(() -> storageService.deleteStorages(null))
                .isInstanceOf(InvalidRequestException.class);
    }

    @Test
    void deleteStorages_withInvalidFormat_throwsInvalidRequestException() {
        assertThatThrownBy(() -> storageService.deleteStorages("abc"))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("Invalid ID format");
    }

    private Storage buildStorage(Long id, String storageType, String bucket, String path) {
        Storage s = new Storage();
        s.setId(id);
        s.setStorageType(storageType);
        s.setBucket(bucket);
        s.setPath(path);
        return s;
    }
}
