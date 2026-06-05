package com.vyttah.goaml.integration.aws;

import com.vyttah.goaml.config.aws.AwsProperties;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectResponse;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectResponse;

import java.nio.charset.StandardCharsets;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Pure unit tests for {@link DefaultS3StorageClient} — the AWS SDK {@link S3Client} is mocked, so every
 * branch (put/fetch/delete success, blank key, null bytes, not-found, SDK error, unconfigured bucket) is
 * covered deterministically without LocalStack.
 */
class DefaultS3StorageClientTest {

    private final S3Client s3 = mock(S3Client.class);
    private final AwsProperties props =
            new AwsProperties("me-central-1", null, new AwsProperties.S3("goaml-attachments"));
    private final DefaultS3StorageClient client = new DefaultS3StorageClient(s3, props);

    @Test
    @SuppressWarnings("unchecked")
    void putStoresBytes() {
        when(s3.putObject(any(Consumer.class), any(RequestBody.class)))
                .thenReturn(PutObjectResponse.builder().build());

        client.put("tenants/t1/reports/r1/a1-invoice.pdf", "hi".getBytes(StandardCharsets.UTF_8), "application/pdf");

        verify(s3).putObject(any(Consumer.class), any(RequestBody.class));
    }

    @Test
    @SuppressWarnings("unchecked")
    void fetchReturnsBytes() {
        ResponseBytes<GetObjectResponse> bytes = ResponseBytes.fromByteArray(
                GetObjectResponse.builder().build(), "pdf-bytes".getBytes(StandardCharsets.UTF_8));
        when(s3.getObjectAsBytes(any(Consumer.class))).thenReturn(bytes);

        byte[] result = client.fetch("tenants/t1/reports/r1/a1.pdf");

        assertThat(new String(result, StandardCharsets.UTF_8)).isEqualTo("pdf-bytes");
    }

    @Test
    @SuppressWarnings("unchecked")
    void deleteCallsSdk() {
        when(s3.deleteObject(any(Consumer.class))).thenReturn(DeleteObjectResponse.builder().build());

        client.delete("tenants/t1/reports/r1/a1.pdf");

        verify(s3).deleteObject(any(Consumer.class));
    }

    @Test
    void blankKeyThrows() {
        assertThatThrownBy(() -> client.put("  ", new byte[0], "application/pdf"))
                .isInstanceOf(S3AccessException.class);
        assertThatThrownBy(() -> client.fetch(null)).isInstanceOf(S3AccessException.class);
        assertThatThrownBy(() -> client.delete("")).isInstanceOf(S3AccessException.class);
    }

    @Test
    void nullBytesThrows() {
        assertThatThrownBy(() -> client.put("k", null, "application/pdf"))
                .isInstanceOf(S3AccessException.class);
    }

    @Test
    @SuppressWarnings("unchecked")
    void fetchMissingObjectThrows() {
        when(s3.getObjectAsBytes(any(Consumer.class)))
                .thenThrow(NoSuchKeyException.builder().message("nope").build());

        assertThatThrownBy(() -> client.fetch("missing"))
                .isInstanceOf(S3AccessException.class);
    }

    @Test
    @SuppressWarnings("unchecked")
    void putSdkErrorThrows() {
        when(s3.putObject(any(Consumer.class), any(RequestBody.class)))
                .thenThrow(SdkClientException.create("boom"));

        assertThatThrownBy(() -> client.put("k", new byte[]{1}, "application/pdf"))
                .isInstanceOf(S3AccessException.class);
    }

    @Test
    @SuppressWarnings("unchecked")
    void fetchSdkErrorThrows() {
        when(s3.getObjectAsBytes(any(Consumer.class)))
                .thenThrow(SdkClientException.create("boom"));

        assertThatThrownBy(() -> client.fetch("k")).isInstanceOf(S3AccessException.class);
    }

    @Test
    @SuppressWarnings("unchecked")
    void deleteSdkErrorThrows() {
        when(s3.deleteObject(any(Consumer.class)))
                .thenThrow(SdkClientException.create("boom"));

        assertThatThrownBy(() -> client.delete("k")).isInstanceOf(S3AccessException.class);
    }

    @Test
    void unconfiguredBucketThrows() {
        var noBucket = new AwsProperties("me-central-1", null, new AwsProperties.S3("  "));
        var c = new DefaultS3StorageClient(s3, noBucket);

        assertThatThrownBy(() -> c.put("k", new byte[]{1}, "application/pdf"))
                .isInstanceOf(S3AccessException.class);

        var nullS3 = new AwsProperties("me-central-1", null, null);
        var c2 = new DefaultS3StorageClient(s3, nullS3);
        assertThatThrownBy(() -> c2.fetch("k")).isInstanceOf(S3AccessException.class);
    }
}
