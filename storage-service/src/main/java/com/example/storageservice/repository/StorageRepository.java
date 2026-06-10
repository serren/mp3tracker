package com.example.storageservice.repository;

import com.example.storageservice.entity.Storage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface StorageRepository extends JpaRepository<Storage, Long> {

    Optional<Storage> findByStorageType(String storageType);
}
