package com.example.storageservice.dto;

import com.example.storageservice.enums.StorageType;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class StorageResponse {

    private Long id;
    private StorageType storageType;
    private String bucket;
    private String path;
}
