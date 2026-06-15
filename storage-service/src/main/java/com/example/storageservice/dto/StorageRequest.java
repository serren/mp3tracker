package com.example.storageservice.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class StorageRequest {

    @NotBlank(message = "storageType is required")
    private String storageType;

    @NotBlank(message = "bucket is required")
    private String bucket;

    @NotNull(message = "path is required")
    private String path;
}
