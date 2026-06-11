package com.example.resourceservice.dto;

import com.example.resourceservice.enums.StorageType;
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
