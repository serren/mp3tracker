package com.example.resourceservice.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.util.UUID;

@Service
public class S3StorageService {

    private final S3Client s3Client;
    private final String bucketName;

    public S3StorageService(S3Client s3Client, @Value("${aws.s3.bucket-name}") String bucketName) {
        this.s3Client = s3Client;
        this.bucketName = bucketName;
    }

    public String upload(byte[] data) {
        String key = UUID.randomUUID().toString() + ".mp3";
        s3Client.putObject(
                PutObjectRequest.builder().bucket(bucketName).key(key).build(),
                RequestBody.fromBytes(data));
        return "s3://" + bucketName + "/" + key;
    }

    public byte[] download(String s3Uri) {
        ResponseBytes<GetObjectResponse> response = s3Client.getObjectAsBytes(
                GetObjectRequest.builder().bucket(bucketName).key(extractKey(s3Uri)).build());
        return response.asByteArray();
    }

    public void delete(String s3Uri) {
        s3Client.deleteObject(
                DeleteObjectRequest.builder().bucket(bucketName).key(extractKey(s3Uri)).build());
    }

    private String extractKey(String s3Uri) {
        return s3Uri.substring(s3Uri.lastIndexOf('/') + 1);
    }
}
