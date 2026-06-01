package com.example.storageservice.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "storages")
@Getter
@Setter
@NoArgsConstructor
public class Storage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "storage_type", nullable = false)
    private String storageType;

    @Column(name = "bucket", nullable = false)
    private String bucket;

    @Column(name = "path", nullable = false)
    private String path;
}
