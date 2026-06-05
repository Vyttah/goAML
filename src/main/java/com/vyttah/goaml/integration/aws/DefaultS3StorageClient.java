package com.vyttah.goaml.integration.aws;

import com.vyttah.goaml.config.aws.AwsProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;

/**
 * Default {@link S3StorageClient} backed by the AWS SDK v2 {@link S3Client}. Reads the bucket name from
 * {@code goaml.aws.s3.bucket}. Every failure mode surfaces as {@link S3AccessException} carrying only the
 * key (never the object bytes).
 */
@Component
@RequiredArgsConstructor
public class DefaultS3StorageClient implements S3StorageClient {

    private final S3Client s3Client;
    private final AwsProperties props;

    @Override
    public void put(String key, byte[] bytes, String contentType) {
        requireKey(key);
        if (bytes == null) {
            throw new S3AccessException("Refusing to store null bytes at key: " + key);
        }
        String bucket = bucket();
        try {
            s3Client.putObject(
                    req -> req.bucket(bucket).key(key).contentType(contentType),
                    RequestBody.fromBytes(bytes));
        } catch (SdkException e) {
            throw new S3AccessException("Failed to store object: " + key, e);
        }
    }

    @Override
    public byte[] fetch(String key) {
        requireKey(key);
        String bucket = bucket();
        try {
            return s3Client.getObjectAsBytes(req -> req.bucket(bucket).key(key)).asByteArray();
        } catch (NoSuchKeyException e) {
            throw new S3AccessException("Object not found: " + key, e);
        } catch (SdkException e) {
            throw new S3AccessException("Failed to fetch object: " + key, e);
        }
    }

    @Override
    public void delete(String key) {
        requireKey(key);
        String bucket = bucket();
        try {
            s3Client.deleteObject(req -> req.bucket(bucket).key(key));
        } catch (SdkException e) {
            throw new S3AccessException("Failed to delete object: " + key, e);
        }
    }

    private String bucket() {
        if (props.s3() == null || props.s3().bucket() == null || props.s3().bucket().isBlank()) {
            throw new S3AccessException("goaml.aws.s3.bucket is not configured");
        }
        return props.s3().bucket();
    }

    private static void requireKey(String key) {
        if (key == null || key.isBlank()) {
            throw new S3AccessException("S3 key is blank");
        }
    }
}
