package com.example.songservice.exception;

public class SongAlreadyExistsException extends RuntimeException {

    public SongAlreadyExistsException(Long id) {
        super("Song metadata with ID=" + id + " already exists");
    }
}
