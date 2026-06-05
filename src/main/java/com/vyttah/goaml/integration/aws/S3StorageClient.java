package com.vyttah.goaml.integration.aws;

/**
 * Stores and retrieves report-attachment bytes in S3. The single object-storage seam the attachment
 * feature (Phase 8) depends on: the service layer owns the key strategy (per-tenant, per-report
 * prefixes) and the {@code attachment} metadata; this client only moves bytes for a given key.
 *
 * <p>Named to avoid a simple-name clash with the AWS SDK's own {@code S3Client}.
 */
public interface S3StorageClient {

    /**
     * Store {@code bytes} at {@code key} (overwriting any existing object).
     *
     * @throws S3AccessException if the upload fails
     */
    void put(String key, byte[] bytes, String contentType);

    /**
     * Fetch the bytes stored at {@code key}.
     *
     * @throws S3AccessException if the object is missing or unreadable
     */
    byte[] fetch(String key);

    /**
     * Delete the object at {@code key}. Deleting a non-existent key is a no-op (S3 semantics).
     *
     * @throws S3AccessException if the delete call fails
     */
    void delete(String key);
}
