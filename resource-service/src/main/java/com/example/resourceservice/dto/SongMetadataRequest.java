package com.example.resourceservice.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class SongMetadataRequest {
    private Long id;
    private String name;
    private String artist;
    private String album;
    private String duration;
    private String year;
}
