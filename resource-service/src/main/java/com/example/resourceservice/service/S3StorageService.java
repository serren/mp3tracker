package com.example.resourceservice.service;

import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;

import java.util.UUID;

@Service
public class S3StorageService {

    private final S3Client s3Client;

    public S3StorageService(S3Client s3Client) {
        this.s3Client = s3Client;
    }

    public String upload(byte[] data, String bucket) {
        String key = UUID.randomUUID().toString() + ".mp3";
        s3Client.putObject(
                PutObjectRequest.builder().bucket(bucket).key(key).build(),
                RequestBody.fromBytes(data));
        return "s3://" + bucket + "/" + key;
    }

    public byte[] download(String s3Uri) {
        ResponseBytes<GetObjectResponse> response = s3Client.getObjectAsBytes(
                GetObjectRequest.builder()
                        .bucket(extractBucket(s3Uri))
                        .key(extractKey(s3Uri))
                        .build());
        return response.asByteArray();
    }

    public void delete(String s3Uri) {
        s3Client.deleteObject(
                DeleteObjectRequest.builder()
                        .bucket(extractBucket(s3Uri))
                        .key(extractKey(s3Uri))
                        .build());
    }

    public void copy(String sourceUri, String targetBucket) {
        String sourceBucket = extractBucket(sourceUri);
        String key = extractKey(sourceUri);
        s3Client.copyObject(CopyObjectRequest.builder()
                .sourceBucket(sourceBucket)
                .sourceKey(key)
                .destinationBucket(targetBucket)
                .destinationKey(key)
                .build());
    }

    private String extractBucket(String s3Uri) {
        // s3Uri format: "s3://bucket/key"
        String withoutScheme = s3Uri.substring("s3://".length());
        return withoutScheme.substring(0, withoutScheme.indexOf('/'));
    }

    private String extractKey(String s3Uri) {
        return s3Uri.substring(s3Uri.lastIndexOf('/') + 1);
    }
}
