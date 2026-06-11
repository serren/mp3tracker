package com.example.storageservice.dto;

import com.example.storageservice.enums.StorageType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class StorageRequest {

    @NotNull(message = "storageType is required")
    private StorageType storageType;

    @NotBlank(message = "bucket is required")
    private String bucket;

    @NotNull(message = "path is required")
    private String path;
}
