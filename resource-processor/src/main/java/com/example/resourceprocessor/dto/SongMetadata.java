package com.example.resourceprocessor.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class SongMetadata {
    private String name;
    private String artist;
    private String album;
    private String duration;
    private String year;
}
