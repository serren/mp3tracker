package com.example.resourceservice.dto;

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
    private String storageType;
    private String bucket;
    private String path;
}
