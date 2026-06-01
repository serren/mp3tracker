package com.example.storageservice.exception;

public class StorageNotFoundException extends RuntimeException {

    public StorageNotFoundException(Long id) {
        super("Storage with ID=" + id + " not found");
    }
}
